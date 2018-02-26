package com.gitlab.ctt.arq.analysis.aspect;

import com.gitlab.ctt.arq.analysis.FileOutput;
import com.gitlab.ctt.arq.analysis.aspect.util.FlagWalker;
import com.gitlab.ctt.arq.core.FileDispatcher;
import com.gitlab.ctt.arq.core.format.LineDelimFormat;
import com.gitlab.ctt.arq.sparql.SparqlAlgorithms;
import com.gitlab.ctt.arq.sparql.SparqlGraph;
import com.gitlab.ctt.arq.sparql.SparqlProperties;
import com.gitlab.ctt.arq.sparql.SparqlQueries;
import com.gitlab.ctt.arq.sparql.check.DesignCheck;
import com.gitlab.ctt.arq.sparql.check.OptCheck;
import com.gitlab.ctt.arq.sparql.check.OptCheck2;
import com.gitlab.ctt.arq.util.Cycles;
import com.gitlab.ctt.arq.util.EdgeCover;
import com.gitlab.ctt.arq.util.GraphShape;
import com.gitlab.ctt.arq.util.SeriesParallel;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.jena.ext.com.google.common.collect.ImmutableMap;
import org.apache.jena.query.Query;
import org.apache.jena.sparql.core.TriplePath;
import org.apache.jena.sparql.syntax.Element;
import org.jgrapht.DirectedGraph;
import org.jgrapht.UndirectedGraph;
import org.jgrapht.graph.DefaultEdge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintWriter;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ShapeAnalysis {
	private static final Logger LOGGER = LoggerFactory.getLogger(ShapeAnalysis.class);

	@SuppressWarnings("Convert2MethodRef")
	private final static Map<String, Function<DirectedGraph<Object, DefaultEdge>, Boolean>> shapeTests =
		ImmutableMap.<String, Function<DirectedGraph<Object, DefaultEdge>, Boolean>>builder()
			.put("singleEdge", graph -> graph.edgeSet().size() == 1)
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

	private static final String CQ_EX_REGEX = "cq_ex_regex";
	private static final String CQ_EX_TEE_PREDICATE = "cq_ex_tee_predicate";
	private static final String CQ_XX_VAR_PREDICATE = "cq_xx_var_predicate";
	private static final String CQ_EX_BAD_FILTER = "cq_ex_bad_filter";
	private static final String CQ_EX_NON_WD = "cq_ex_non_wd";
	private static final String CQ_EX_OPT_BAD_NESTING = "cq_ex_opt_bad_nesting";
	private static final String CQ_EX_OPT_BAD_INTERFACE = "cq_ex_opt_bad_interface";
	private static final String CQ_EX_NON_WDPT = "cq_ex_non_wdpt";


	private static Map<String, Function<Long, Boolean>> fragmentMap =
		ImmutableMap.<String, Function<Long, Boolean>>builder()



			.put(FRAG_CQ,    flagLong -> (flagLong & ~(1)) == 0)
			.put(FRAG_CQ_F,  flagLong -> (flagLong & ~(1|8)) == 0)   
			.put(FRAG_CQ_FO, flagLong -> (flagLong & ~(1|8|4)) == 0) 
			.build();

	private Map<String, LongAdder> counterMap = new ConcurrentHashMap<>();
	private Map<String, LongAdder> tripleCounterMap = new ConcurrentHashMap<>();

	public void outputCount(Map<String, Long> map) {
		fragmentMap.keySet().forEach(fragId -> {
			String taggedTotal = TOTAL_CQ + fragId;
			map.put(taggedTotal, counterMap.get(taggedTotal).sum());
			String taggedUnknown = UNCLASSIFIED_STR + fragId;
			map.put(taggedUnknown, counterMap.get(taggedUnknown).sum());
			shapeTests.keySet().forEach((name) -> {
				String taggedName = name + fragId;
				map.put(taggedName, counterMap.get(taggedName).sum());
			});
		});
		map.put(CQ_EX_REGEX, counterMap.get(CQ_EX_REGEX).sum());
		map.put(CQ_EX_TEE_PREDICATE, counterMap.get(CQ_EX_TEE_PREDICATE).sum());
		map.put(CQ_XX_VAR_PREDICATE, counterMap.get(CQ_XX_VAR_PREDICATE).sum());
		map.put(CQ_EX_BAD_FILTER, counterMap.get(CQ_EX_BAD_FILTER).sum());
		map.put(CQ_EX_NON_WD, counterMap.get(CQ_EX_NON_WD).sum());
		map.put(CQ_EX_OPT_BAD_NESTING, counterMap.get(CQ_EX_OPT_BAD_NESTING).sum());
		map.put(CQ_EX_OPT_BAD_INTERFACE, counterMap.get(CQ_EX_OPT_BAD_INTERFACE).sum());
		map.put(CQ_EX_NON_WDPT, counterMap.get(CQ_EX_NON_WDPT).sum());
		outputFragProperties(map);

		tripleCounterMap.forEach((k, v) -> map.put(k, v.sum()));

		countingMaps.forEach((k, v) -> {
			v.forEach((n, c) -> {
				map.put(String.format("%s_%s", k, n), c.sum());
			});
		});
	}

	public void analyzeShape(Query query, FlagWalker flagWalker) {
		long flagLong = flagWalker.asLong();
		Map<String, Boolean> fragmentMap = ShapeAnalysis.fragmentMap.entrySet()
			.stream().collect(Collectors.toMap(
				Entry::getKey, e -> e.getValue().apply(flagLong)));
		if (query.isSelectType() || query.isAskType()) {
			if (fragmentMap.get(FRAG_CQ_FO)) {
				DirectedGraph<Object, DefaultEdge> graph = getGraphAOF(query, flagLong);
				if (graph != null) {  
					analyzeAllShapes(query, fragmentMap, graph);
					checkCycles(graph, query, fragmentMap);
					checkEdgeCover(query, fragmentMap);
					countTriples(query, fragmentMap);
					checkProjection(query, fragmentMap);
				}
			}
			Map<String, Boolean> specialMap = new LinkedHashMap<>();
			specialMap.put("_sa", true);
			countTriples(query, specialMap);
		}
	}

	private static final String HIDDEN_CYCLE = "hiddenCycle";
	private static final String PROJECTION = "projection_cq";
	private static final String PROJECTION_UNSURE = "projectionUnsure_cq";
	private static final String ASK_PROJECTION = "askProjection_cq";
	private static final String ASK_PROJECTION_UNSURE = "askProjectionUnsure_cq";

	private static List<String> fragProperties = Arrays.asList(
		HIDDEN_CYCLE, PROJECTION, PROJECTION_UNSURE, ASK_PROJECTION, ASK_PROJECTION_UNSURE
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
			String fragId,
			boolean hasVarPredicate,
			boolean isShapeless,
			DirectedGraph<Object, DefaultEdge> graph) {
		Element element = query.getQueryPattern();
		boolean hiddenCycle = !hasVarPredicate && GraphShape.hasHiddenCycle(element, graph);
		if (hiddenCycle) {
			counterMap.get(HIDDEN_CYCLE + fragId).increment();
			if (FileDispatcher.isDebug) {
				System.err.println("#### hidden_cycle" + fragId + "\n" + query);
			}
		}
		boolean hypergraphView = hiddenCycle || hasVarPredicate;
		if (extGuard(fragId) && hypergraphView) {
			Pair<Integer, Integer> widthXnodeCount =
				HyperTreeUtil.hyperTreeCheck(query);
			int hyperTreeWidth = widthXnodeCount.getLeft();
			int htNodeCount = widthXnodeCount.getRight();
			String tag = "htw" + fragId;
			countingMaps.computeIfAbsent(tag, k -> new ConcurrentHashMap<>())
				.computeIfAbsent(hyperTreeWidth, k -> new LongAdder()).increment();
			if (hyperTreeWidth > 0) {
				String tag2 = String.format("htw%sNC%s",
					hyperTreeWidth, fragId);
				countingMaps.computeIfAbsent(tag2, k -> new ConcurrentHashMap<>())
					.computeIfAbsent(htNodeCount, k -> new LongAdder()).increment();
			} else {
				if (FileDispatcher.isDebug) {
					System.err.println("#### htw_0" + fragId + "\n" + query);
				}
			}
		}
		if (extGuard(fragId) && !hypergraphView) {
			int nodeCount = graph.vertexSet().size();
			String tag2 = String.format("htwxNC%s", fragId);
			countingMaps.computeIfAbsent(tag2, k -> new ConcurrentHashMap<>())
				.computeIfAbsent(nodeCount, k -> new LongAdder()).increment();
		}
		if (extGuard(fragId) && isShapeless) {
			int treeWidth = HyperTreeUtil.hyperTreeWidth(query, true);
			String tag2 = String.format("tw%s", fragId);
			countingMaps.computeIfAbsent(tag2, k -> new ConcurrentHashMap<>())
				.computeIfAbsent(treeWidth, k -> new LongAdder()).increment();
		}
	}

	private static boolean extGuard(String fragId) {
		return FRAG_CQ_FO.equals(fragId); 
	}

	private void checkProjection(Query query, Map<String, Boolean> fragmentMap) {
		Optional<Boolean> maybeProjection = SparqlQueries.maybeProjection(query);
		boolean projectionUnsure = false;
		boolean projection = false;
		boolean askProjectionUnsure = false;
		boolean askProjection = false;
		if (!maybeProjection.isPresent()) {
			projectionUnsure = true;
		} else if (maybeProjection.orElse(false)) {
			projection = true;
		}
		Optional<Boolean> maybeAskProjection = SparqlQueries.maybeAskProjection(query);
		if (!maybeAskProjection.isPresent()) {
			askProjectionUnsure = true;
		} else if (maybeAskProjection.orElse(false)) {
			askProjection = true;
		}
		for (Entry<String, Boolean> fragEntry : fragmentMap.entrySet()) {
			if (fragEntry.getValue()) {
				String fragId = fragEntry.getKey();
				if (projectionUnsure) {
					counterMap.get(PROJECTION_UNSURE + fragId).increment();
				}
				if (projection) {
					counterMap.get(PROJECTION  + fragId).increment();
				}
				if (askProjectionUnsure) {
					counterMap.get(ASK_PROJECTION_UNSURE + fragId).increment();
				}
				if (askProjection) {
					counterMap.get(ASK_PROJECTION + fragId).increment();
				}
			}
		}
	}

	private DirectedGraph<Object, DefaultEdge> getGraphAOF(Query query, long flagLong) {
		if (!fragmentMap.get(FRAG_CQ_FO).apply(flagLong)) {
			return null;
		}
		Element element = query.getQueryPattern();
		if (element == null) {

			return null;
		}
		if (SparqlProperties.get().hasPath(element)) {
			counterMap.get(CQ_EX_REGEX).increment();
			return null;
		}
		if (SparqlGraph.hasTeePredicate(element)) {
			counterMap.get(CQ_EX_TEE_PREDICATE).increment();
			return null;
		}

		DirectedGraph<Object, DefaultEdge> graph = SparqlGraph.graphFromQuery(element);
		if (graph == null) {
			counterMap.get(CQ_EX_BAD_FILTER).increment();
			return null;
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

	private void analyzeAllShapes(Query query, Map<String, Boolean> fragmentMap,
			DirectedGraph<Object, DefaultEdge> graph) {
		Element element = query.getQueryPattern();
		boolean hasVarPredicate = SparqlGraph.hasVarPredicate(element);
		if (hasVarPredicate) {
			counterMap.get(CQ_XX_VAR_PREDICATE).increment();
		}
		Map<String, Boolean> vector = new LinkedHashMap<>();
		if (!hasVarPredicate) {
			shapeTests.forEach((name, test) -> {
				if (test.apply(graph)) {
					vector.put(name, true);
				}
			});
		}
		for (Entry<String, Boolean> fragEntry : fragmentMap.entrySet()) {
			if (fragEntry.getValue()) {
				String fragId = fragEntry.getKey();
				if (!hasVarPredicate) {
					counterMap.computeIfAbsent(TOTAL_CQ + fragId, k -> new LongAdder()).increment();
					for (Entry<String, Boolean> shapeEntry : vector.entrySet()) {
						String taggedName = shapeEntry.getKey() + fragId;
						counterMap.computeIfAbsent(taggedName, k -> new LongAdder()).increment();
					}
				}
				boolean isShapeless = !hasVarPredicate && vector.entrySet().size() == 0;
				if (isShapeless) {
					String taggedUnknown = UNCLASSIFIED_STR + fragId;
					counterMap.computeIfAbsent(taggedUnknown, k -> new LongAdder()).increment();
					outputUnclassified(query, fragId);
				}
				checkTreeWidthProperties(query, fragId, hasVarPredicate, isShapeless, graph);
			}
		}
	}

	private void countTriples(Query query, Map<String, Boolean> fragmentMap) {
		Element element = query.getQueryPattern();
		for (Entry<String, Boolean> fragEntry : fragmentMap.entrySet()) {
			if (fragEntry.getValue()) {
				Set<TriplePath> tps = SparqlAlgorithms.collectTriples(element);
				String fragId = fragEntry.getKey();
				String tripleCountKey = String.format("%s%s_%s", "tripleCount", fragId, tps.size());
				tripleCounterMap.computeIfAbsent(tripleCountKey,
					k -> new LongAdder()).increment();
			}
		}
	}

	private Map<String, Map<Object, LongAdder>> countingMaps = new ConcurrentHashMap<>();

	private void checkCycles(DirectedGraph<Object, DefaultEdge> graph, Query query,
			Map<String, Boolean> fragmentMap) {
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
			for (Entry<String, Boolean> fragEntry : fragmentMap.entrySet()) {
				if (fragEntry.getValue()) {
					String fragId = fragEntry.getKey();
					countingMaps.computeIfAbsent("cl_count" + fragId,
						k -> new ConcurrentHashMap<>())
						.computeIfAbsent(count,k -> new LongAdder()).increment();
					if (reasonableCount) {
						countingMaps.computeIfAbsent("cl_min" + fragId,
							k -> new ConcurrentHashMap<>())
							.computeIfAbsent(min, k -> new LongAdder()).increment();
						countingMaps.computeIfAbsent("cl_max" + fragId,
							k -> new ConcurrentHashMap<>())
							.computeIfAbsent(max, k -> new LongAdder()).increment();
						countingMaps.computeIfAbsent("cl_avg" + fragId,
							k -> new ConcurrentHashMap<>())
							.computeIfAbsent(avg, k -> new LongAdder()).increment();
					}
				}
			}
		} catch (OutOfMemoryError ignored) {

			if (FileDispatcher.isDebug) {
				System.err.println("#### out of memory\n" + query);
			}
			throw ignored;
		}
	}

	private void checkEdgeCover(Query query, Map<String, Boolean> fragmentMap) {
		Element element = query.getQueryPattern();
		double edgeCoverNumber = EdgeCover.edgeCoverNumber(element);



		for (Entry<String, Boolean> fragEntry : fragmentMap.entrySet()) {
			if (fragEntry.getValue()) {
				String tag = "edgeCover" + fragEntry.getKey();
				countingMaps.computeIfAbsent(tag, k -> new ConcurrentHashMap<>())
					.computeIfAbsent(edgeCoverNumber, k -> new LongAdder()).increment();
			}
		}
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
