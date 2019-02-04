package com.gitlab.ctt.arq.analysis.aspect.util;

import com.gitlab.ctt.arq.sparql.SparqlGraph;
import org.apache.jena.graph.Node;
import org.apache.jena.sparql.core.Var;
import org.apache.jena.sparql.syntax.*;
import org.jgrapht.DirectedGraph;
import org.jgrapht.graph.DefaultEdge;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

public class Element2Triples {
	private int labelCount;
	public List<List<String>> triples = new ArrayList<>();
	public Set<String> labels = new LinkedHashSet<>();
	public Map<String, Integer> map = new LinkedHashMap<>();
	public Map<Integer, String> rMap = new LinkedHashMap<>();

	public Element2Triples(Element element, boolean skipPredicates, boolean skipConstants) {
		if (element != null) {
			ElementWalker.walk(element, new ElementVisitorBase() {
				@Override
				public void visit(ElementPathBlock el) {
					el.patternElts().forEachRemaining(tp -> {
						Node subject = tp.getSubject();
						Node object = tp.getObject();
						List<String> triple = new ArrayList<>();
						addToTriple(subject, triple, skipConstants);
						addToTriple(object, triple, skipConstants);
						if (!skipPredicates) {
							Node predicate = tp.getPredicate();
							Object predOrPath = predicate != null ? predicate : tp.getPath();
							addToTriple(predOrPath, triple, skipConstants);
						}
						if (triple.size() > 0) {
							triples.add(triple);
						}
						labels.addAll(triple);
					});
				}

				@Override
				public void visit(ElementService el) {
					transfer(Element2Triples::serviceToTriples, el, triples);
				}

				@Override
				public void visit(ElementBind el) {
					transfer(Element2Triples::bindToTriples, el, triples);
				}

				@Override
				public void visit(ElementData el) {
					transfer(Element2Triples::valuesToTriples, el, triples);
				}

				@Override
				public void visit(ElementFilter el) {
					transfer(Element2Triples::filterToTriples, el, triples);
				}
			});
		}

		labelCount = 0;
		for (String label : labels) {
			map.put(label, labelCount);
			rMap.put(labelCount, label);
			labelCount++;
		}
	}

	private static <E extends Element> void transfer(BiConsumer<E, List<List<Object>>> c, E el, List<List<String>> tgt) {

		List<List<Object>> objs = new ArrayList<>();
		c.accept(el, objs);
		for (List<Object> obj : objs) {
			tgt.add(obj.stream().map(String::valueOf).collect(Collectors.toList()));
		}
	}

	public void addTriple(List<String> hyperedge) {
		triples.add(hyperedge);
		labels.addAll(hyperedge);
		for (String label : hyperedge) {
			map.put(label, labelCount);
			rMap.put(labelCount, label);
			labelCount++;
		}
	}

	private static void addToTriple(Object thing, List<String> triple, boolean skipConstants) {

		if (!skipConstants || SparqlGraph.isNonConstant(thing)) {
			triple.add(String.valueOf(thing));
		}
	}

	private static void addToTriple2(Object thing, List<Object> triple, boolean skipConstants) {

		if (!skipConstants || SparqlGraph.isNonConstant(thing)) {
			triple.add(thing);
		}
	}

	private static void serviceToTriples(ElementService el, List<List<Object>> triples) {
		List<Object> triple = new ArrayList<>();
		SparqlGraph.collectTriples(el.getElement(), (o1, o2, p3) -> {

			addToTriple2(o1, triple, true);
			addToTriple2(o2, triple, true);
			addToTriple2(o2, triple, true);

		});
		if (triple.size() > 0) {
			triples.add(triple);
		}
	}

	public static boolean permitServiceInGraph(Element element,
			DirectedGraph<Object, DefaultEdge> graph) {
		List<List<Object>> triples = new ArrayList<>();
		ElementWalker.walk(element, new ElementVisitorBase() {
			@Override
			public void visit(ElementService el) {
				serviceToTriples(el, triples);
			}
		});
		return fuseTriplesToGraph(graph, triples);
	}

	private static void bindToTriples(ElementBind el, List<List<Object>> triples) {
		List<Object> triple = new ArrayList<>();
		triple.add(el.getVar());
		Set<Var> vars = SparqlGraph.collectExprVars(el.getExpr());

		vars.forEach(v -> triple.add(v));
		triples.add(triple);
	}

	public static boolean permitBindInGraph(Element element,
			DirectedGraph<Object, DefaultEdge> graph) {
		List<List<Object>> triples = new ArrayList<>();
		ElementWalker.walk(element, new ElementVisitorBase() {
			@Override
			public void visit(ElementBind el) {
				bindToTriples(el, triples);
			}
		});
		return fuseTriplesToGraph(graph, triples);
	}

	public static Boolean permitValuesInGraph(Element element,
			DirectedGraph<Object, DefaultEdge> graph) {
		List<List<Object>> triples = new ArrayList<>();
		ElementWalker.walk(element, new ElementVisitorBase() {
			@Override
			public void visit(ElementData el) {
				valuesToTriples(el, triples);
			}
		});
		return fuseTriplesToGraph(graph, triples);
	}

	private static void valuesToTriples(ElementData el, List<List<Object>> triples) {
		List<Object> triple = new ArrayList<>();

		el.getVars().forEach(v -> triple.add(v));
		triples.add(triple);
	}

	public static Boolean permitFilterInGraph(Element element,
		DirectedGraph<Object, DefaultEdge> graph) {
		List<List<Object>> triples = new ArrayList<>();
		ElementWalker.walk(element, new ElementVisitorBase() {
			@Override
			public void visit(ElementFilter el) {
				filterToTriples(el, triples);
			}
		});
		return fuseTriplesToGraph(graph, triples);
	}

	private static void filterToTriples(ElementFilter el, List<List<Object>> triples) {
		List<Object> triple = new ArrayList<>();
		Set<Var> vars = SparqlGraph.collectExprVars(el.getExpr());

		vars.forEach(v -> triple.add(v));
		triples.add(triple);
	}

	private static boolean fuseTriplesToGraph(DirectedGraph<Object, DefaultEdge> graph, List<List<Object>> triples) {
		boolean result = triples.stream().allMatch(tr -> tr.size() <= 2);
		if (result) {
			triples.forEach(tr -> {
				if (tr.size() > 1) {
					graph.addVertex(tr.get(0));
					graph.addVertex(tr.get(1));
					graph.addEdge(tr.get(0), tr.get(1));
				}
			});
		}
		return result;
	}
}
