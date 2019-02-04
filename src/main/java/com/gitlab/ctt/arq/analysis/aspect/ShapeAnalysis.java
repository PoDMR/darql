package com.gitlab.ctt.arq.analysis.aspect;

import com.gitlab.ctt.arq.analysis.FileOutput;
import com.gitlab.ctt.arq.analysis.aspect.db.QueryRecord;
import com.gitlab.ctt.arq.analysis.aspect.util.Element2Triples;
import com.gitlab.ctt.arq.analysis.aspect.util.FlagWalker;
import com.gitlab.ctt.arq.analysis.aspect.util.HyperTreeEval;
import com.gitlab.ctt.arq.analysis.support.PathWalker;
import com.gitlab.ctt.arq.core.BatchProcessor;
import com.gitlab.ctt.arq.core.FileDispatcher;
import com.gitlab.ctt.arq.core.format.LineDelimFormat;
import com.gitlab.ctt.arq.sparql.*;
import com.gitlab.ctt.arq.sparql.check.DesignCheck;
import com.gitlab.ctt.arq.sparql.check.OptCheck;
import com.gitlab.ctt.arq.sparql.check.OptCheck2;
import com.gitlab.ctt.arq.util.Cycles;
import com.gitlab.ctt.arq.util.EdgeCover;
import com.gitlab.ctt.arq.util.GraphShape;
import com.gitlab.ctt.arq.util.SeriesParallel;
import fj.data.Either;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.jena.ext.com.google.common.collect.ImmutableMap;
import org.apache.jena.graph.Node_Concrete;
import org.apache.jena.query.Query;
import org.apache.jena.sparql.core.TriplePath;
import org.apache.jena.sparql.core.Var;
import org.apache.jena.sparql.syntax.*;
import org.jgrapht.DirectedGraph;
import org.jgrapht.UndirectedGraph;
import org.jgrapht.graph.AsUndirectedGraph;
import org.jgrapht.graph.DefaultEdge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintWriter;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;

import static com.gitlab.ctt.arq.analysis.aspect.util.FlagWalker.*;

public class ShapeAnalysis {
	private static final Logger LOGGER = LoggerFactory.getLogger(ShapeAnalysis.class);

	@SuppressWarnings("Convert2MethodRef")
	private final static Map<String, Function<DirectedGraph<Object, DefaultEdge>, Boolean>> shapeTests =
		ImmutableMap.<String, Function<DirectedGraph<Object, DefaultEdge>, Boolean>>builder()
			.put("noNode", graph -> graph.vertexSet().size() == 0)
			.put("singleNode", graph -> graph.vertexSet().size() == 1)
			.put("noEdge", graph -> graph.edgeSet().size() == 0)
			.put("singleEdge", graph -> graph.edgeSet().size() == 1)
			.put("nonBranching", graph -> graph.vertexSet().stream().mapToInt(
				v -> graph.inDegreeOf(v) + graph.outDegreeOf(v)).allMatch(i -> i <= 2))
			.put("selfLoops", graph -> GraphShape.hasSelfLoop(graph))
			.put("parallelEdges", graph -> GraphShape.hasParallelEdges(graph))
			.put("chain", graph -> GraphShape.isChainU(graph))
			.put("chainSet", graph -> GraphShape.isChainSetU(graph))
			.put("star", graph -> GraphShape.isStarU(graph))
			.put("circle", graph -> GraphShape.isCircleU(graph))
			.put("utree", graph -> GraphShape.isTreeU(graph))
			.put("uforest", graph -> GraphShape.isForestU(graph))
			.put("cycletree", graph -> GraphShape.isCycleTreeU(graph))
			.put("bicycle", graph -> GraphShape.isBicycleUSG(graph))
			.put("flower", graph -> SeriesParallel.isFlower(graph, false))
			.put("flowerSet", graph -> SeriesParallel.isGarden(graph, false))
			.put("spFlower", graph -> SeriesParallel.isFlower(graph, true))
			.put("spFlowerSet", graph -> SeriesParallel.isGarden(graph, true))

			.put("chainSet_sg", graph -> GraphShape.isChainSetUSG(graph))
			.put("star_sg", graph -> GraphShape.isStarUSG(graph))
			.put("circle_sg", graph -> GraphShape.isCircleUSG(graph))
			.put("utree_sg", graph -> GraphShape.isTreeUSG(graph))
			.put("uforest_sg", graph -> GraphShape.isForestUSG(graph))
			.put("cycletree_sg", graph -> GraphShape.isCycleTreeUSG(graph))
			.build();
	private static final String UNCLASSIFIED_STR = "shapeless";
	private static final String TOTAL_CQ = "count_cq";
	private static final String FRAG_CQ = "";
	private static final String FRAG_CQ_F = "_f";
	private static final String FRAG_CQ_FO = "_fo";
	private static final String FRAG_CQ_FOX = "_fox";
	private static final String FRAG_CQ_FOV = "_fov";
	private static final String FRAG_CQ_PRIME = FRAG_CQ_FOX;

	private static final String CQ_EX_REGEX = "cq_ex_regex";
	private static final String CQ_EX_TEE_PREDICATE = "cq_ex_tee_predicate";
	private static final String CQ_XX_VAR_PREDICATE = "cq_xx_var_predicate";
	private static final String CQ_EX_BAD_FILTER = "cq_ex_bad_filter";
	private static final String CQ_EX_NON_WD = "cq_ex_non_wd";
	private static final String CQ_EX_OPT_BAD_NESTING = "cq_ex_opt_bad_nesting";
	private static final String CQ_EX_OPT_BAD_INTERFACE = "cq_ex_opt_bad_interface";
	private static final String CQ_EX_NON_WDPT = "cq_ex_non_wdpt";

	private static final int AFOSBD = 1|8|4|(1<<SERVICE-1)|(1<<BIND-1)|(1<<DATA-1);

	private static Map<String, Function<Long, Boolean>> fragmentMap =
		ImmutableMap.<String, Function<Long, Boolean>>builder()



			.put(FRAG_CQ,    flagLong -> (flagLong & ~(1)) == 0)
			.put(FRAG_CQ_F,  flagLong -> (flagLong & ~(1|8)) == 0)   
			.put(FRAG_CQ_FO, flagLong -> (flagLong & ~(1|8|4)) == 0) 
			.put(FRAG_CQ_FOV, flagLong -> (flagLong & ~(1|8|4|(1<<DATA-1))) == 0)
			.put(FRAG_CQ_FOX, flagLong -> (flagLong & ~(AFOSBD)) == 0)
			.build();

	private Map<String, LongAdder> counterMap = new ConcurrentHashMap<>();
	private Map<String, LongAdder> tripleCounterMap = new ConcurrentHashMap<>();
	private static final LongAdder nullAdder = new LongAdder();

	public void outputCount(Map<String, Long> map) {
		fragmentMap.keySet().forEach(fragId -> {
			String taggedTotal = TOTAL_CQ + fragId;
			outputVariants(map, taggedTotal);
			String taggedUnknown = UNCLASSIFIED_STR + fragId;
			outputVariants(map, taggedUnknown);
			shapeTests.keySet().forEach((name) -> {
				String taggedName = name + fragId;
				outputVariants(map, taggedName);
			});
		});
		safeSum(map, CQ_EX_REGEX);
		safeSum(map, CQ_EX_TEE_PREDICATE);
		safeSum(map, CQ_XX_VAR_PREDICATE);
		safeSum(map, CQ_EX_BAD_FILTER);
		safeSum(map, CQ_EX_NON_WD);
		safeSum(map, CQ_EX_OPT_BAD_NESTING);
		safeSum(map, CQ_EX_OPT_BAD_INTERFACE);
		safeSum(map, CQ_EX_NON_WDPT);
		outputFragProperties(map);

		tripleCounterMap.forEach((k, v) -> map.put(k, v.sum()));

		countingMaps.forEach((k, v) -> {
			v.forEach((n, c) -> {
				map.put(String.format("%s_%s", k, n), c.sum());
			});
		});
	}

	private void outputVariants(Map<String, Long> map, String taggedName) {
		safeSum(map, taggedName);
		safeSum(map, RE_PREFIX + taggedName);
		safeSum(map, "nc_" + taggedName);
		safeSum(map, "nc_" + RE_PREFIX + taggedName);
	}

	private void safeSum(Map<String, Long> map, String taggedName) {
		map.put(taggedName, counterMap.getOrDefault(
		        taggedName, nullAdder).sum());
	}

	public void analyzeShape(Query query, FlagWalker flagWalker) {
		long flagLong = flagWalker.asLong();
		Map<String, Boolean> fragmentMap = ShapeAnalysis.fragmentMap.entrySet()
			.stream().collect(Collectors.toMap(
				Entry::getKey, e -> e.getValue().apply(flagLong)));
		if (query.isSelectType() || query.isAskType() || query.isConstructType()) {
			if (fragmentMap.get(FRAG_CQ_PRIME)) {
				QueryRecord record = new QueryRecord();
				record.regex = false;
				record.teePredicate = false;  
				record.var_predicate = false;
				DirectedGraph<Object, DefaultEdge> graph = getGraphAOF(query, flagLong, record);
				if (graph != null) {  



					if (isGraphPattern(query.getQueryPattern(), record)) {

						checkCycles(graph, query, fragmentMap, record.regex);  
						checkEdgeCover(query, fragmentMap, record.regex);
						countTriples(query, fragmentMap, record.regex, RE_PREFIX);
						countTripleSymbols(query, fragmentMap, record.regex, RE_PREFIX);
						checkProjection(query, fragmentMap, record.regex);

					}
					analyzeAllShapes(query, fragmentMap, graph, record);
				}
			}
			Map<String, Boolean> specialMap = new LinkedHashMap<>();
			specialMap.put("_sa", true);
			countTriples(query, specialMap, true, "");
			countTripleSymbols(query, specialMap, true, "");
			miscTests(query, fragmentMap);
		}
	}

	private static final String HIDDEN_CYCLE = "hiddenCycle";
	private static final String PROJECTION = "projection_cq";
	private static final String PROJECTION_UNSURE = "projectionUnsure_cq";
	private static final String ASK_PROJECTION = "askProjection_cq";
	private static final String ASK_PROJECTION_UNSURE = "askProjectionUnsure_cq";
	private static final String TREE_PATTERN = "treePattern";
	public static final String FREE_CONNEX_ACYCLIC = "fca";
	private static final String RE_PREFIX = "re_";

	private static List<String> fragProperties = Arrays.asList(
		HIDDEN_CYCLE, PROJECTION, PROJECTION_UNSURE, ASK_PROJECTION, ASK_PROJECTION_UNSURE,
		RE_PREFIX + PROJECTION, RE_PREFIX + PROJECTION_UNSURE,
		RE_PREFIX + ASK_PROJECTION, RE_PREFIX + ASK_PROJECTION_UNSURE,
		TREE_PATTERN, FREE_CONNEX_ACYCLIC
	);

	private void initFragProperties() {
		for (String prop : fragProperties) {
			for (String fragId : fragmentMap.keySet()) {
				counterMap.put(prop + fragId, new LongAdder());
			}
		}
	}

	private void outputFragProperties(Map<String, Long> map) {
		for (String prop : fragProperties) {
			for (String fragId : fragmentMap.keySet()) {
				String key = prop + fragId;
				map.put(key, counterMap.get(key).sum());
			}
		}
	}

	private void checkTreeWidthProperties(Query query,
			Map<String, Boolean> fragmentMap,
			boolean asRegularGraph,
			boolean isShapeless,
			boolean isNcShapeless,
			DirectedGraph<Object, DefaultEdge> graph,
			DirectedGraph<Object, DefaultEdge> ncGraph,
			QueryRecord record) {










		boolean hypergraphView = !asRegularGraph && !record.regex;
		if (hypergraphView) {
			Pair<Integer, Integer> widthXnodeCount =
				HyperTreeEval.get().hyperTreeCheck(query);
			int hyperTreeWidth = widthXnodeCount.getLeft();

			int htNodeCount = widthXnodeCount.getRight();
			withFragments(fragmentMap, fragId -> {
				String tag = "htw" + fragId;
				countingMaps.computeIfAbsent(tag, k -> new ConcurrentHashMap<>())
					.computeIfAbsent(hyperTreeWidth, k -> new LongAdder()).increment();
				if (hyperTreeWidth > 0) {
					String tag2 = String.format("htw%sNC%s", hyperTreeWidth, fragId);
					countingMaps.computeIfAbsent(tag2, k -> new ConcurrentHashMap<>())
						.computeIfAbsent(htNodeCount, k -> new LongAdder()).increment();
				} else {
					if (FileDispatcher.isDebug) {
						System.err.println("#### htw_0" + fragId + "\n" + query);
					}
				}
			});
		}
		if (!hypergraphView) {
			int nodeCount = graph.vertexSet().size();
			withFragments(fragmentMap, fragId -> {
				String tag2 = String.format("htwxNC%s", fragId);
				countingMaps.computeIfAbsent(tag2, k -> new ConcurrentHashMap<>())
					.computeIfAbsent(nodeCount, k -> new LongAdder()).increment();
			});
		}
		if (asRegularGraph && isShapeless) {
			int treeWidth = HyperTreeEval.get().hyperTreeWidth(query, true, false);
			withFragments(fragmentMap, fragId -> {
				String tag2 = String.format("tw%s", fragId);
				countingMaps.computeIfAbsent(tag2, k -> new ConcurrentHashMap<>())
					.computeIfAbsent(treeWidth, k -> new LongAdder()).increment();
			});
		}
		if (asRegularGraph && isNcShapeless) {
			int treeWidth = HyperTreeEval.get().hyperTreeWidth(query, true, true);
			withFragments(fragmentMap, fragId -> {
				String tag2 = String.format("nc_tw%s", fragId);
				countingMaps.computeIfAbsent(tag2, k -> new ConcurrentHashMap<>())
					.computeIfAbsent(treeWidth, k -> new LongAdder()).increment();
			});
		}
	}





	private void checkProjection(Query query, Map<String, Boolean> fragmentMap, boolean regex) {
		Optional<Boolean> maybeProjection = SparqlQueries.maybeProjection(query);
		boolean projectionUnsure = !maybeProjection.isPresent();
		boolean projection = maybeProjection.orElse(false);
		Optional<Boolean> maybeAskProjection = SparqlQueries.maybeAskProjection(query);
		boolean askProjectionUnsure = !maybeAskProjection.isPresent();
		boolean askProjection = maybeAskProjection.orElse(false);
		withFragments(fragmentMap, fragId -> {
			withPrefix(RE_PREFIX, regex, prefix -> {
				if (projectionUnsure) {
					counterMap.get(prefix + PROJECTION_UNSURE + fragId).increment();
				}
				if (projection) {
					counterMap.get(prefix + PROJECTION  + fragId).increment();
				}
				if (askProjectionUnsure) {
					counterMap.get(prefix + ASK_PROJECTION_UNSURE + fragId).increment();
				}
				if (askProjection) {
					counterMap.get(prefix + ASK_PROJECTION + fragId).increment();
				}
			});
		});
	}

	private DirectedGraph<Object, DefaultEdge> getGraphAOF(Query query, long flagLong,
			QueryRecord record) {



		Element element = query.getQueryPattern();
		if (element == null) {

			return null;
		}
		if (SparqlProperties.get().hasPath(element)) {
			counterMap.get(CQ_EX_REGEX).increment();
			record.regex = true;

		}
		if (SparqlGraph.hasTeePredicate(element)) {
			counterMap.get(CQ_EX_TEE_PREDICATE).increment();
			record.teePredicate = true;

		}

		DirectedGraph<Object, DefaultEdge> graph = SparqlGraph.graphFromQuery(element);
		if (graph == null) {
			counterMap.get(CQ_EX_BAD_FILTER).increment();
			return null;
		} else {

			record.service = Element2Triples.permitServiceInGraph(element, graph);
			record.bind = Element2Triples.permitBindInGraph(element, graph);
			record.data = Element2Triples.permitValuesInGraph(element, graph);
			record.filter = Element2Triples.permitFilterInGraph(element, graph);
			record.bad_filter =
				!(record.service && record.bind && record.data && record.filter);
		}
		if (!DesignCheck.isUwd(element)) {
			counterMap.get(CQ_EX_NON_WD).increment();
			return null;
		}
		OptCheck optCheck = OptCheck.check(element);
		if (optCheck.isBadNesting()) {
			counterMap.get(CQ_EX_OPT_BAD_NESTING).increment();

		}
		if (optCheck.isBadInterface()) {
			counterMap.get(CQ_EX_OPT_BAD_INTERFACE).increment();
			return null;
		}
		if (!OptCheck2.wdpt(element)) {
			counterMap.get(CQ_EX_NON_WDPT).increment();
			if (FileDispatcher.isDebug) {
				System.err.println("#### non-wdpt" + "\n" + query);
			}
			return null;
		}
		return graph;
	}

	private static void withFragments(Map<String, Boolean> fragmentMap, Consumer<String> fragIdConsumer) {
		for (Entry<String, Boolean> fragEntry : fragmentMap.entrySet()) {
			if (fragEntry.getValue()) {
				String fragId = fragEntry.getKey();
				fragIdConsumer.accept(fragId);
			}
		}
	}













	private static void withPrefix(String prefix, boolean doLift, Consumer<String> consumer) {
		if (doLift) {
			consumer.accept(prefix);
		} else {
			consumer.accept(prefix);
			consumer.accept("");
		}
	}

	private boolean isGraphPattern(Element element, QueryRecord record) {
		boolean hasVarPredicate = SparqlGraph.hasVarPredicate(element);
		boolean hasNoVarPredicateReuse = SparqlGraph.freeVarPredicate(element);
		boolean asRegularGraph = !record.teePredicate && hasNoVarPredicateReuse
			&& !record.bad_filter
			;
		if (hasVarPredicate && !record.var_predicate) {
			counterMap.get(CQ_XX_VAR_PREDICATE).increment();
			record.var_predicate = true;
		}
		return asRegularGraph;
	}

	private void analyzeAllShapes(Query query, Map<String, Boolean> fragmentMap,
		DirectedGraph<Object, DefaultEdge> graph, QueryRecord record) {
		Element element = query.getQueryPattern();
		boolean asRegularGraph = isGraphPattern(element, record);
		DirectedGraph<Object, DefaultEdge> ncGraph = SparqlGraph.graphFromQuery(
			query.getQueryPattern(), false);
		Map<String, Boolean> vector = new LinkedHashMap<>();
		if (asRegularGraph) {
			shapeTests.forEach((name, test) -> {
				if (test.apply(graph)) {
					if (!record.regex) {
						vector.put(name, true);
					}

						vector.put(RE_PREFIX + name, true);

				}

				if (test.apply(ncGraph)) {
					if (!record.regex) {
						vector.put("nc_" + name, true);
					}

						vector.put("nc_re_" + name, true);

				}
			});

			checkTrees(graph, query, fragmentMap);
		} else {


			counterMap.get(CQ_EX_BAD_FILTER).increment();  
		}
		MutableBoolean isShapelessAnyFrag = new MutableBoolean(false);
		MutableBoolean isNcShapelessAnyFrag = new MutableBoolean(false);
		withFragments(fragmentMap, fragId -> {
			if (asRegularGraph) {
				if (!record.regex) {
					counterMap.computeIfAbsent(TOTAL_CQ + fragId,
						k -> new LongAdder()).increment();
					counterMap.computeIfAbsent("nc_" + TOTAL_CQ + fragId,
						k -> new LongAdder()).increment();
				}

					counterMap.computeIfAbsent(RE_PREFIX + TOTAL_CQ + fragId,
						k -> new LongAdder()).increment();
					counterMap.computeIfAbsent("nc_re_" + TOTAL_CQ + fragId,
						k -> new LongAdder()).increment();

				for (Entry<String, Boolean> shapeEntry : vector.entrySet()) {
					String taggedName = shapeEntry.getKey() + fragId;
					counterMap.computeIfAbsent(taggedName,
						k -> new LongAdder()).increment();
				}
			}

			boolean isShapeless = asRegularGraph && vector.entrySet()
				.stream().filter(e -> !e.getKey().contains("nc_"))
				.count() == 0;
			boolean isNcShapeless = asRegularGraph && vector.entrySet()
				.stream().noneMatch(e -> e.getKey().contains("nc_"));





			if (isShapeless) {
				String taggedUnknown = UNCLASSIFIED_STR + fragId;
				if (!record.regex) {
					counterMap.computeIfAbsent(taggedUnknown,
						k -> new LongAdder()).increment();
				}

				counterMap.computeIfAbsent(RE_PREFIX + taggedUnknown,
					k -> new LongAdder()).increment();

				outputUnclassified(query, fragId);
				isShapelessAnyFrag.setTrue();
			}
			if (isNcShapeless) {
				String taggedUnknown = UNCLASSIFIED_STR + fragId;
				if (!record.regex) {
					counterMap.computeIfAbsent("nc_" + taggedUnknown,
						k -> new LongAdder()).increment();
				}

				counterMap.computeIfAbsent("nc_re_" + taggedUnknown,
					k -> new LongAdder()).increment();


				isNcShapelessAnyFrag.setTrue();
			}
		});

		checkTreeWidthProperties(query, fragmentMap,
			asRegularGraph,
			isShapelessAnyFrag.getValue(), isNcShapelessAnyFrag.getValue(),
			graph, ncGraph, record);


		checkFreeConnex(query, asRegularGraph ? graph : null, record.regex, fragmentMap);
		checkTreePattern(element, graph, asRegularGraph, fragmentMap);
	}

	private void checkFreeConnex(Query query,
		DirectedGraph<Object, DefaultEdge> graph,
		boolean regex,
		Map<String, Boolean> fragmentMap
	) {
		Either<Boolean, Integer> fca = GraphShape.freeConnexAcyclic(
			query, graph, regex);
		if (fca.isLeft()) {
			withFragments(fragmentMap, fragId -> {
				counterMap.computeIfAbsent(FREE_CONNEX_ACYCLIC + fragId, k -> new LongAdder()).increment();
			});
		} else {
			if (!regex) {
				withFragments(fragmentMap, fragId -> {
					int htw = fca.right().value();
					countingMaps.computeIfAbsent("fca_htw" + fragId,k -> new ConcurrentHashMap<>())
						.computeIfAbsent(htw, k -> new LongAdder()).increment();
				});
			}
		}
	}

	private void checkTreePattern(Element element,
			DirectedGraph<Object, DefaultEdge> graph, boolean asRegularGraph,
			Map<String, Boolean> fragmentMap) {
		if (asRegularGraph) {
			if (SparqlGraph.isTreePatternAcyclic(graph, element)) {
				withFragments(fragmentMap, fragId -> {
					counterMap.computeIfAbsent(TREE_PATTERN + fragId, k -> new LongAdder()).increment();
				});
			}
		}
	}

	private void countTriples(Query query, Map<String, Boolean> fragmentMap,
			boolean regex, String rePrefix) {
		Element element = query.getQueryPattern();
		withFragments(fragmentMap, fragId -> {
			withPrefix(rePrefix, regex, prefix -> {
				Set<TriplePath> tps = SparqlAlgorithms.collectTriplesWithService(element);
				String tripleCountKey = String.format("%s%s_%s", prefix + "tripleCount", fragId, tps.size());
				tripleCounterMap.computeIfAbsent(tripleCountKey,
					k -> new LongAdder()).increment();
			});
		});
	}

	private void countTripleSymbols(Query query, Map<String, Boolean> fragmentMap,
			boolean regex, String rePrefix) {
		Element element = query.getQueryPattern();
		withFragments(fragmentMap, fragId -> {
			withPrefix(rePrefix, regex, prefix -> {
				long count = PathWalker.symbolCount(element);
				String tripleCountKey = String.format("%s%s_%s",
					prefix + "tripleSymbolCount", fragId, count);
				tripleCounterMap.computeIfAbsent(tripleCountKey,
					k -> new LongAdder()).increment();
			});
		});
	}

	private Map<String, Map<Object, LongAdder>> countingMaps = new ConcurrentHashMap<>();

	private void checkTrees(DirectedGraph<Object, DefaultEdge> graph, Query query,
			Map<String, Boolean> fragmentMap) {
		AsUndirectedGraph<Object, DefaultEdge> ug = new AsUndirectedGraph<>(graph);
		List<Set<Object>> cs = GraphShape.connectedSets(ug);
		if (!GraphShape.isCyclic(ug) && cs.size() == 1) {
			int longestPath = GraphShape.longestPath(ug);
			int maxDeg = ug.vertexSet().stream().mapToInt(ug::degreeOf).max().orElse(0);
			boolean isTrueTree = ug.vertexSet().stream().mapToInt(ug::degreeOf)
				.filter(x -> x > 2).count() > 1;
			final String typeStr = isTrueTree ? "tree_" : maxDeg < 3 ? "chain_" : "star_";










			withFragments(fragmentMap, fragId -> {
				countingMaps.computeIfAbsent(typeStr + "depth_max" + fragId,
					k -> new ConcurrentHashMap<>())
					.computeIfAbsent(longestPath, k -> new LongAdder()).increment();
				if (maxDeg >= 3) {
					countingMaps.computeIfAbsent(typeStr + "degree_max" + fragId,
						k -> new ConcurrentHashMap<>())
						.computeIfAbsent(maxDeg, k -> new LongAdder()).increment();
				}
				if (isTrueTree) {
					double avgDeg = ug.vertexSet().stream().mapToInt(ug::degreeOf)
						.filter(x -> x > 1).average().orElse(0f);
					countingMaps.computeIfAbsent(typeStr + "inner_degree_avg" + fragId,
						k -> new ConcurrentHashMap<>())
						.computeIfAbsent(avgDeg, k -> new LongAdder()).increment();
					double splits = ug.vertexSet().stream().mapToInt(ug::degreeOf)
						.filter(x -> x > 2).count();
					double inner = ug.vertexSet().stream().mapToInt(ug::degreeOf)
						.filter(x -> x > 1).count();
					double count = ug.vertexSet().size();
					countingMaps.computeIfAbsent(typeStr + "split_tot" + fragId,
						k -> new ConcurrentHashMap<>())
						.computeIfAbsent(splits, k -> new LongAdder()).increment();
					countingMaps.computeIfAbsent(typeStr + "inner_tot" + fragId,
						k -> new ConcurrentHashMap<>())
						.computeIfAbsent(inner, k -> new LongAdder()).increment();
					countingMaps.computeIfAbsent(typeStr + "split_rel" + fragId,
						k -> new ConcurrentHashMap<>())
						.computeIfAbsent(splits / count, k -> new LongAdder()).increment();
					countingMaps.computeIfAbsent(typeStr + "inner_rel" + fragId,
						k -> new ConcurrentHashMap<>())
						.computeIfAbsent(inner / count, k -> new LongAdder()).increment();
				}
			});
		}
	}

	private void checkCycles(DirectedGraph<Object, DefaultEdge> graph, Query query,
		Map<String, Boolean> fragmentMap, boolean regex) {
		try {
			UndirectedGraph<Object, DefaultEdge> ug = GraphShape.pseudoToSimple(graph);
			List<List<Object>> cycleBase = GraphShape.findCycleBase(ug);
			int count = cycleBase.size();
			boolean reasonableCount = count <= 8;
			if (!reasonableCount) {
				if (FileDispatcher.isDebug) {
					if (fragmentMap.get(FRAG_CQ_FO)) {
						System.err.println("#### cl_count" + FRAG_CQ_FO + "_" + count
							+ "\n" + query.toString());
					}
				}
			} else {
				cycleBase = Cycles.findBase(cycleBase);
			}
			int min = cycleBase.stream().mapToInt(List::size).min().orElse(0);
			int max = cycleBase.stream().mapToInt(List::size).max().orElse(0);
			double avg = cycleBase.stream().mapToInt(List::size).average().orElse(0d);
			withFragments(fragmentMap, fragId -> {
				withPrefix(RE_PREFIX, regex, prefix -> {
					countingMaps.computeIfAbsent(prefix + "cl_count" + fragId,
						k -> new ConcurrentHashMap<>())
						.computeIfAbsent(count, k -> new LongAdder()).increment();
					if (reasonableCount) {
						countingMaps.computeIfAbsent(prefix + "cl_min" + fragId,
							k -> new ConcurrentHashMap<>())
							.computeIfAbsent(min, k -> new LongAdder()).increment();
						countingMaps.computeIfAbsent(prefix + "cl_max" + fragId,
							k -> new ConcurrentHashMap<>())
							.computeIfAbsent(max, k -> new LongAdder()).increment();
						countingMaps.computeIfAbsent(prefix + "cl_avg" + fragId,
							k -> new ConcurrentHashMap<>())
							.computeIfAbsent(avg, k -> new LongAdder()).increment();
					}
				});
			});
		} catch (OutOfMemoryError error) {

			if (FileDispatcher.isDebug) {
				System.err.println("#### out of memory\n" + query);
			}
			throw error;
		}
	}

	private void checkEdgeCover(Query query, Map<String, Boolean> fragmentMap, Boolean regex) {
		Element element = query.getQueryPattern();
		double edgeCoverNumber = EdgeCover.edgeCoverNumber(element);



		withFragments(fragmentMap, fragId -> {
			withPrefix(RE_PREFIX, regex, prefix -> {
				String tag = "edgeCover" + fragId;
				countingMaps.computeIfAbsent(tag, k -> new ConcurrentHashMap<>())
					.computeIfAbsent(edgeCoverNumber, k -> new LongAdder()).increment();
			});
		});
	}

	private void miscTests(Query query, Map<String, Boolean> fragmentMap) {
		Element element = query.getQueryPattern();
		Map<String, ToIntFunction<Element>> testMap =
			ImmutableMap.<String, ToIntFunction<Element>>builder()
				.put("varCount", ShapeAnalysis::countVars)
				.put("constCount", ShapeAnalysis::countConstants)

				.put("optNestCount", ShapeAnalysis::optNest)
				.build();

			for (Entry<String, ToIntFunction<Element>> tests : testMap.entrySet()) {
				String tag = tests.getKey() + "_sa"; 
				int n = tests.getValue().applyAsInt(element);
				countingMaps.computeIfAbsent(tag, k -> new ConcurrentHashMap<>())
					.computeIfAbsent(n, k -> new LongAdder()).increment();
			}

			String tag = "valuesCount" + "_sa"; 
			List<Integer> valueCounts = countValues(element);
			for (int n : valueCounts) {
				countingMaps.computeIfAbsent(tag, k -> new ConcurrentHashMap<>())
					.computeIfAbsent(n, k -> new LongAdder()).increment();
			}

	}

	private static int countVars(Element element) {
		Set<Var> vars = new LinkedHashSet<>();
		SparqlGraph.collectTriples(element, (o1, o2, o3) -> {
			if (o1 instanceof Var) {
				vars.add((Var) o1);
			}
			if (o2 instanceof Var) {
				vars.add((Var) o2);
			}
			if (o3 instanceof Var) {
				vars.add((Var) o3);
			}
		});
		return vars.size();
	}

	private static int countConstants(Element element) {
		Set<Object> set = new LinkedHashSet<>();
		SparqlGraph.collectTriples(element, (o1, o2, o3) -> {
			if (o1 instanceof Node_Concrete) {
				set.add(o1);
			}
			if (o2 instanceof Node_Concrete) {
				set.add(o2);
			}
			if (o3 instanceof Node_Concrete) {
				set.add(o3);
			}
		});
		return set.size();
	}

	private static List<Integer> countValues(Element element) {
		List<Integer> list = new ArrayList<>();
		ElementDeepWalker.walk(element, new ElementVisitorBase() {
			@Override
			public void visit(ElementData el) {
				list.add(el.getRows().size());
			}
		});
		return list;
	}

	private static int optNest(Element element) {
		try {
			ElementDeepWalker.walk(element, new ElementVisitorBase() {
				@Override
				public void visit(ElementOptional el) {
					throw new BatchProcessor.BailException(el);
				}
			});
		} catch (BatchProcessor.BailException e) {
			Object[] context = e.getContext();
			ElementOptional el = (ElementOptional) context[0];
			return 1 + optNest(el.getOptionalElement());
		}
		return 0;
	}

	private synchronized void outputUnclassified(Query query, String fragId) {
		LOGGER.trace("found unclassified shape");
		PrintWriter writer = writerMap.get(fragId);
		writer.print(LineDelimFormat.HASH_DELIM);
		writer.print("\n");
		writer.print(query.toString());
		writer.flush();
	}



	private Map<String, FileOutput> fileOutputMap = new LinkedHashMap<>();
	private Map<String, PrintWriter> writerMap = new LinkedHashMap<>();
	private String tag;

	public ShapeAnalysis(String tag) {
		this.tag = tag;
		init();
		fragmentMap.keySet().forEach(fragId -> {
			String taggedTotal = TOTAL_CQ + fragId;
			counterMap.put(taggedTotal, new LongAdder());
			shapeTests.keySet().forEach(name -> counterMap.put(
				name + fragId, new LongAdder()));
			String taggedUnknown = UNCLASSIFIED_STR + fragId;
			counterMap.put(taggedUnknown, new LongAdder());
		});
		counterMap.put(CQ_EX_REGEX, new LongAdder());
		counterMap.put(CQ_EX_TEE_PREDICATE, new LongAdder());
		counterMap.put(CQ_XX_VAR_PREDICATE, new LongAdder());
		counterMap.put(CQ_EX_BAD_FILTER, new LongAdder());
		counterMap.put(CQ_EX_NON_WD, new LongAdder());
		counterMap.put(CQ_EX_OPT_BAD_NESTING, new LongAdder());
		counterMap.put(CQ_EX_OPT_BAD_INTERFACE, new LongAdder());
		counterMap.put(CQ_EX_NON_WDPT, new LongAdder());
		initFragProperties();
	}

	public void init() {
		fragmentMap.keySet().forEach(fragId -> {
			String baseName = "shapeless" + fragId + ".txt";
			FileOutput fileOutput = FileOutput.from(tag, baseName);
			PrintWriter writer = fileOutput.getWriter();
			fileOutputMap.put(fragId, fileOutput);
			writerMap.put(fragId, writer);
		});
	}


	public void commit() {
		fragmentMap.keySet().forEach(fragId -> {
			FileOutput fileOutput = fileOutputMap.get(fragId);
			if (fileOutput != null) {
				fileOutput.commit();
			}
			fileOutputMap.remove(fragId);
		});
	}
}
