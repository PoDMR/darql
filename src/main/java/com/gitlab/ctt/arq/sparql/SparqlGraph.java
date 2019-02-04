package com.gitlab.ctt.arq.sparql;

import com.gitlab.ctt.arq.analysis.aspect.PathExtractor;
import com.gitlab.ctt.arq.analysis.aspect.util.FlagWalker;
import com.gitlab.ctt.arq.analysis.support.PathWalker;
import com.gitlab.ctt.arq.core.BatchProcessor.BailException;
import com.gitlab.ctt.arq.util.GraphShape;
import com.gitlab.ctt.arq.util.SparqlUtil;
import com.gitlab.ctt.arq.utilx.LabeledEdge;
import com.gitlab.ctt.arq.utilx.Resources;
import fj.data.Either;
import fj.function.Effect3;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.jena.graph.*;
import org.apache.jena.query.Query;
import org.apache.jena.sparql.algebra.walker.Walker;
import org.apache.jena.sparql.core.Var;
import org.apache.jena.sparql.expr.*;
import org.apache.jena.sparql.path.*;
import org.apache.jena.sparql.syntax.*;
import org.jgrapht.DirectedGraph;
import org.jgrapht.alg.ConnectivityInspector;
import org.jgrapht.alg.cycle.DirectedSimpleCycles;
import org.jgrapht.alg.cycle.TarjanSimpleCycles;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.DirectedPseudograph;
import org.jgrapht.graph.DirectedSubgraph;

import javax.annotation.Nullable;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class SparqlGraph {
	public static void main(String[] args) {


		testExample();
	}

	private static void testExample() {
		String sparqlStr = Resources.getResourceAsString("sample/misc/scrap.sparql");
		testString(sparqlStr);

	}

	private static void testHandleFilter(String sparqlStr) {
		Either<Exception, Query> maybeQuery = SparqlUtil.get().toQuery(sparqlStr);
		if (maybeQuery.isRight()) {
			Query query = maybeQuery.right().value();
			Element element = query.getQueryPattern();
			handleFilters(element, (o, o2, o3) -> {});
		} else {
			throw new RuntimeException(maybeQuery.left().value());
		}
	}

	public static void checkGraphShape() {
		DirectedGraph<Object, DefaultEdge> graph = new DirectedPseudograph<>(DefaultEdge.class);
		Arrays.asList("a", "b").forEach(graph::addVertex);
		graph.addEdge("a", "b");
		System.out.println("chain: " + isChain(graph));
	}

	public static boolean isForest(DirectedGraph<Object, DefaultEdge> graph) {
		if (graph == null) {
			return false;
		}
		boolean isInjective = graph.vertexSet().stream()
			.mapToInt(graph::inDegreeOf).allMatch(i -> i <= 1);
		if (!isInjective) {
			return false;
		}
		return isAcyclic(graph);
	}

	private static boolean isAcyclic(DirectedGraph<Object, DefaultEdge> graph) {
		return findCycles(graph).isEmpty();
	}

	private static List<List<Object>> findCycles(DirectedGraph<Object, DefaultEdge> graph) {
		DirectedSimpleCycles<Object, DefaultEdge> cycleFinder =
			new TarjanSimpleCycles<>(graph);
		return cycleFinder.findSimpleCycles();
	}

	public static boolean isTree(DirectedGraph<Object, DefaultEdge> graph) {
		if (graph == null) {
			return false;
		}
		boolean isForest = isForest(graph);
		if (!isForest) {
			return false;
		}
		ConnectivityInspector<Object, DefaultEdge> connectivity =
			new ConnectivityInspector<>(graph);
		return connectivity.isGraphConnected();
	}

	public static boolean isChain(DirectedGraph<Object, DefaultEdge> graph) {
		return graph != null && graph.vertexSet().size() > 0 && isAcyclic(graph)
			&& components(graph).allMatch(SparqlGraph::isSingleChain);
	}

	public static boolean isCircle(DirectedGraph<Object, DefaultEdge> graph) {
		return graph != null && graph.vertexSet().size() > 0
			&& components(graph).allMatch(SparqlGraph::isSingleCircle);
	}

	public static boolean isStar(DirectedGraph<Object, DefaultEdge> graph) {
		return graph != null && graph.vertexSet().size() > 0 && isAcyclic(graph)
			&& components(graph).allMatch(SparqlGraph::isSingleStar);
	}

	private static boolean isSingleChain(DirectedGraph<Object, DefaultEdge> graph) {

		return graph.vertexSet().stream().mapToInt(graph::inDegreeOf).filter(i -> i == 0).count() == 1


			&& graph.vertexSet().stream().mapToInt(graph::outDegreeOf).allMatch(i -> i <= 1);
	}

	private static boolean isSingleCircle(DirectedGraph<Object, DefaultEdge> graph) {
		return findCycles(graph).size() == 1
			&& graph.vertexSet().stream().mapToInt(graph::outDegreeOf).allMatch(i -> i == 1);
	}

	private static boolean isSingleStar(DirectedGraph<Object, DefaultEdge> graph) {
		List<Object> rootCandidates = graph.vertexSet().stream()
			.filter(v -> graph.inDegreeOf(v) == 0).collect(Collectors.toList());
		if (rootCandidates.size() == 1) {
			Object root = rootCandidates.get(0);
			return graph.outDegreeOf(root) > 1
				&& graph.vertexSet().stream().filter(v -> !v.equals(root))
				.mapToInt(graph::outDegreeOf).allMatch(i -> i <= 1);
		} else {
			return false;
		}
	}

	public static boolean isStarChain(DirectedGraph<Object, DefaultEdge> graph) {
		return graph != null && graph.vertexSet().size() > 0 && isAcyclic(graph)
			&& components(graph).filter(SparqlGraph::isSingleChain).count() >= 1
			&& components(graph).filter(SparqlGraph::isSingleStar).count() >= 1;
	}

	private static Stream<DirectedGraph<Object, DefaultEdge>>
			components(DirectedGraph<Object, DefaultEdge> graph) {
		ConnectivityInspector<Object, DefaultEdge> connectivity =
			new ConnectivityInspector<>(graph);
		return connectivity.connectedSets().stream().map(
			set -> new DirectedSubgraph<>(graph, set, graph.edgeSet()));
	}

	public static boolean hasTeePredicate(Element element) {
		Set<Node> predicates = new HashSet<>();
		Set<Node> nonPredicates = new HashSet<>();
		ElementDeepWalker.walk(element, new ElementVisitorBase() {
			@Override
			public void visit(ElementPathBlock el) {
				el.patternElts().forEachRemaining(tp -> {
					nonPredicates.add(tp.getSubject());
					nonPredicates.add(tp.getObject());
					Node predicate = tp.getPredicate();
					if (predicate != null) {
						predicates.add(predicate);
					}
				});
			}
		});
		return !Collections.disjoint(predicates, nonPredicates);
	}

	public static boolean hasVarPredicate(Element element) {
		MutableBoolean hasVarPredicate = new MutableBoolean();
		ElementDeepWalker.walk(element, new ElementVisitorBase() {
			@Override
			public void visit(ElementPathBlock el) {
				el.patternElts().forEachRemaining(tp -> {
					Node predicate = tp.getPredicate();
					if (isVarNode(predicate)) {
						hasVarPredicate.setTrue();
					}
				});
			}
		});
		return hasVarPredicate.isTrue();
	}


	public static boolean freeVarPredicate(Element element) {
		List<Node> subjList = new ArrayList<>();
		List<Node> predList = new ArrayList<>();
		List<Node> objcList = new ArrayList<>();
		ElementDeepWalker.walk(element, new ElementVisitorBase() {
			@Override
			public void visit(ElementPathBlock el) {
				el.patternElts().forEachRemaining(tp -> {
					if (isVarNode(tp.getSubject())) {
						subjList.add(tp.getSubject());
					}
					if (isVarNode(tp.getObject())) {
						objcList.add(tp.getObject());
					}
					if (isVarNode(tp.getPredicate())) {
						predList.add(tp.getPredicate());
					}
				});
			}
		});
		Set<Node> subjSet = new HashSet<>(subjList);
		Set<Node> predSet = new HashSet<>(predList);
		Set<Node> objcSet = new HashSet<>(objcList);
		if (predList.size() != predSet.size()) {
			return false;
		}
		subjSet.retainAll(predList);
		objcSet.retainAll(predList);
		return !(subjSet.size() > 0 || objcSet.size() > 0);
	}

	public static boolean isVarNode(Node predicate) {
		return predicate instanceof Node_Variable ||
			predicate instanceof Node_Blank;
	}



	private static void testString(String sparqlStr) {
		Either<Exception, Query> maybeQuery = SparqlUtil.get().toQuery(sparqlStr);
		if (maybeQuery.isRight()) {
			Query query = maybeQuery.right().value();
			Element element = query.getQueryPattern();
			DirectedGraph<Object, DefaultEdge> graph = graphFromQuery(element, false);
			System.out.println(graph);
			System.out.println("chain: " + GraphShape.isChainU(graph));
			System.out.println("edgeCount: " + graph.edgeSet().size());
			System.out.println("var_pred: " + hasVarPredicate(element));
		} else {
			throw new RuntimeException(maybeQuery.left().value());
		}
	}

	@Nullable
	public static DirectedGraph<Object, DefaultEdge> graphFromQuery(Element element) {
		return graphFromQuery(element, permitConstantsDefault);
	}

	@Nullable
	public static DirectedGraph<Object, DefaultEdge> graphFromQuery(
			Element element, boolean permitConstants) {
		try {
			return tryGraphFromQuery(element, permitConstants);
		} catch (BailException e) {
			return null;
		}
	}

	private static final boolean permitConstantsDefault = true;

	private static DirectedGraph<Object, DefaultEdge> tryGraphFromQuery(
			Element element, boolean permitConstants) {
		DirectedGraph<Object, DefaultEdge> graph = new DirectedPseudograph<>(DefaultEdge.class);
		return tryGraphFromQuery(element, graph, (subject, object, predicate) -> {
			if (permitConstants || (isNonConstant(subject) && isNonConstant(object))) {
				graph.addVertex(subject);
				graph.addVertex(object);
				graph.addEdge(subject, object,
					new LabeledEdge<>(Arrays.asList(subject, object, predicate)));
			}
		});
	}

	
	public static boolean isNonConstant(Object thing) {
		return !((thing instanceof Node_Literal) || (thing instanceof Node_URI));
	}

	
	public static DirectedGraph<Object, DefaultEdge> tryGraphFromQuery(Element element,
			DirectedGraph<Object, DefaultEdge> graph,
			Effect3<Object, Object, Object> buildGraph) {
		Map<Object, Set<Object>> equivalenceMap = equivalenceMap(element);
		Effect3<Object, Object, Object> addEdge = (subject, object, predicate) -> {







			buildGraph.f(subject, object, predicate);
		};

		collectTriples(element, addEdge);


		return graph;
	}

	public static void collectTriples(Element element, Effect3<Object, Object, Object> addEdge) {
		ElementDeepWalker.walk(element, new ElementVisitorBase() {
			@Override
			public void visit(ElementPathBlock el) {
				el.patternElts().forEachRemaining(tp -> {
					Node subject = tp.getSubject();
					Node object = tp.getObject();
					Node predicate = tp.getPredicate();
					if (predicate != null) {
						addEdge.f(subject, object, predicate);
					} else {
						addEdge.f(subject, object, tp.getPath());
					}
				});
			}
		});
	}

	private static void handleFilters(Element element,Effect3<Object, Object, Object> addEdge) {
		ElementDeepWalker.walk(element, new ElementVisitorBase() {
			@Override
			public void visit(ElementFilter filter) {
				Expr expr = filter.getExpr();

				Set<Var> vars = collectExprVars(expr);
				if (vars.size() > 1) {
					if (!(expr instanceof E_Equals)) {
						throw new BailException("rejected");
					} else {
						E_Equals func = (E_Equals) expr;
						Expr arg1 = func.getArg1();
						Expr arg2 = func.getArg2();
						if (!(arg1 instanceof ExprVar && arg2 instanceof ExprVar)) {
							throw new BailException("rejected");
						}
					}
				}


				exprWalkerWalk(new ExprVisitorBase() {

					@Override
					public void visit(ExprFunctionOp op) {
						Set<Var> vars = SparqlAlgorithms.tripleVars(op.getElement());
						if (vars.size() > 1) {
							throw new BailException("rejected");
						}
						collectTriples(op.getElement(), addEdge);
					}
				}, expr);
			}
		});
	}

	public static Set<Var> collectExprVars(Expr expr) {
		Set<Var> vars = new LinkedHashSet<>();
		exprWalkerWalk(new ExprVisitorBase() {
			@Override
			public void visit(ExprVar nv) {
				super.visit(nv);
				vars.add(nv.asVar());
			}
		}, expr);
		return vars;
	}

	public static void throwOnBadOpt(Element element) {
		ElementDeepWalker.walk(element, new ElementVisitorBase() {
			@Override
			public void visit(ElementOptional el) {
				FlagWalker flagWalker = new FlagWalker();
				flagWalker.consume(el.getOptionalElement());
				long flagLong = flagWalker.asLong();

				if ((~1L & flagLong) != 0L) {
					throw new BailException("optional not conjunctive");
				}
			}
		});
	}

	private static List<Pair<Var, Var>> equivalentFilterPairs(Element element) {
		List<Pair<Var,Var>> pairs = new ArrayList<>();
		ElementDeepWalker.walk(element, new ElementVisitorBase() {
			@Override
			public void visit(ElementFilter filter) {
				Expr expr = filter.getExpr();
				exprWalkerWalk(new ExprVisitorBase() {
					@Override
					public void visit(ExprFunction2 func) {
						if (func instanceof E_Equals) {
							Expr arg1 = func.getArg1();
							Expr arg2 = func.getArg2();
							if (arg1 instanceof ExprVar && arg2 instanceof ExprVar) {
								pairs.add(ImmutablePair.of(arg1.asVar(), arg2.asVar()));
							}
						}
					}
				}, expr);
			}
		});
		return pairs;
	}

	private static Map<Object, Set<Object>> equivalenceMap(List<Pair<Var,Var>> pairs) {
		Map<Object, Set<Object>> map = new LinkedHashMap<>();
		for (Pair<?, ?> pair : pairs) {
			Object v1 = pair.getLeft();
			Object v2 = pair.getRight();
			Set<Object> vs1 = map.get(v1);
			if (vs1 == null) {
				vs1 = new LinkedHashSet<>();
				vs1.add(v1);
			}
			Set<Object> vs2 = map.get(v2);
			if (vs2 == null) {
				vs2 = new LinkedHashSet<>();
				vs2.add(v2);
			}
			vs1.addAll(vs2);
			map.put(v1, vs1);
			map.put(v2, vs1);
		}
		return map;
	}

	private static Map<Object, Set<Object>> equivalenceMap(Element element) {
		return equivalenceMap(equivalentFilterPairs(element));
	}

	public static void exprWalkerWalk(ExprVisitor exprVisitor, Expr expr) {

		Walker.walk(expr, exprVisitor);
	}

	public static boolean isTreePatternAcyclic(
		DirectedGraph<Object, DefaultEdge> graph,
		Element element
	) {
		return isTreePatternCompatible(element) && isAcyclic(graph);
	}

	public static boolean isTreePatternCompatible(Element element) {
		try {
			PathVisitor pathVisitor = new PathWalker() {
				@Override
				public void accept(Path path) {
					if (!isTreePatternCompatible(path)) {
						throw new BailException("not tree pattern compatible");
					}
				}
			};
			PathExtractor.walkPath(element, pathVisitor);
		} catch (BailException ignored) {
			return false;
		}
		return true;
	}






	protected static boolean isTreePatternCompatible(Path path) {
		return isTPTerm(path)
			|| isTPConcat(path)
			|| isTPRepeatOrTerm(path);
	}

	private static boolean isTPConcat(Path path) {
		boolean seq = path instanceof P_Seq;
		P_Path2 p2 = (P_Path2) path;

		return seq && (
			isTPRepeatOrTerm(p2.getLeft()) &&
			isTPRepeatOrTerm(p2.getRight())
		);
	}

	private static boolean isTPChoice(Path path) {
		boolean choice = path instanceof P_Alt;
		P_Path2 p2 = (P_Path2) path;
		return choice
			&& isTPTerm(p2.getLeft())
			&& isTPTerm(p2.getRight());
	}

	private static boolean isTPRepeatOrTerm(Path path) {
		if (isTPTerm(path)) {
			return true;
		}
		boolean r1 = path instanceof P_OneOrMore1;
		boolean r2 = path instanceof P_ZeroOrMore1;
		boolean r3 = path instanceof P_ZeroOrMoreN;
		boolean r4 = path instanceof P_OneOrMoreN;
		P_Path1 p1 = (P_Path1) path;
		return (r1 || r2 || r3 || r4) && (
			isTPTerm(p1.getSubPath())
			|| isTPChoice(p1.getSubPath())
		);
	}

	private static boolean isTPTerm(Path path) {
		return path instanceof P_Path0 ||
			path instanceof P_NegPropSet;
	}
}
