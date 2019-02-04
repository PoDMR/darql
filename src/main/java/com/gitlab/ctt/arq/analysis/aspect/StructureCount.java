package com.gitlab.ctt.arq.analysis.aspect;

import com.gitlab.ctt.arq.analysis.aspect.util.FlagWalker;
import com.gitlab.ctt.arq.sparql.SparqlAlgorithms;
import com.gitlab.ctt.arq.sparql.SparqlGraph;
import com.gitlab.ctt.arq.sparql.SparqlProperties;
import com.gitlab.ctt.arq.sparql.SparqlTransducer;
import com.gitlab.ctt.arq.sparql.check.DesignCheck;
import org.apache.jena.query.Query;
import org.apache.jena.sparql.syntax.Element;
import org.jgrapht.DirectedGraph;
import org.jgrapht.graph.DefaultEdge;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class StructureCount {
	private AtomicLong singleSimpleTripleCounter = new AtomicLong();
	private AtomicLong regexCounter = new AtomicLong();
	private AtomicLong cpfCounter = new AtomicLong();
	private AtomicLong cpfNoRegexCounter = new AtomicLong();

	private AtomicLong wbCounter = new AtomicLong();

	private AtomicLong uwdCounter = new AtomicLong();
	private AtomicLong uwwdCounter = new AtomicLong();
	private AtomicLong wdCounter = new AtomicLong();
	private AtomicLong wwdCounter = new AtomicLong();
	private AtomicLong optAFUCounter = new AtomicLong();
	private AtomicLong uwdCompCounter = new AtomicLong();
	private AtomicLong uwwdCompCounter = new AtomicLong();
	private AtomicLong acyclicFOCounter = new AtomicLong();
	private AtomicLong acyclicCounter = new AtomicLong();
	private AtomicLong acyclicNoFilterCounter = new AtomicLong();

	private ShapeAnalysis shapeAnalysis;




	private AtomicLong forestCounter = new AtomicLong();
	private AtomicLong treeCounter = new AtomicLong();
	private AtomicLong forestNoRegexCounter = new AtomicLong();
	private AtomicLong treeNoRegexCounter = new AtomicLong();
	private AtomicLong forestFCounter = new AtomicLong();
	private AtomicLong treeFCounter = new AtomicLong();
	private AtomicLong forestNoRegexFCounter = new AtomicLong();
	private AtomicLong treeNoRegexFCounter = new AtomicLong();
	private AtomicLong forestFOCounter = new AtomicLong();
	private AtomicLong treeFOCounter = new AtomicLong();
	private AtomicLong forestNoRegexFOCounter = new AtomicLong();
	private AtomicLong treeNoRegexFOCounter = new AtomicLong();
	private AtomicLong uForestCounter = new AtomicLong();
	private AtomicLong uTreeCounter = new AtomicLong();
	private AtomicLong uForestNoRegexCounter = new AtomicLong();
	private AtomicLong uTreeNoRegexCounter = new AtomicLong();

	private AtomicLong teePredicateCounter = new AtomicLong();

	public StructureCount(String tag) {
		shapeAnalysis = new ShapeAnalysis(tag);
	}

	public void handleQuery(Query query) {

		Element element = query.getQueryPattern();
		boolean hasRegex = SparqlProperties.get().hasPath(element);


		FlagWalker flagWalker = new FlagWalker();
		flagWalker.consume(element);
		long flagLong = flagWalker.asLong();
		boolean hasFilter = ((flagLong & 8L) != 0);
		boolean hasOptional = ((flagLong & 4L) != 0);
		boolean cpf = ((flagLong & ~9L) == 0);  
		boolean afo = ((flagLong & ~13L) == 0); 
		boolean afou = ((flagLong & ~0b1111) == 0); 
		boolean selectOrAsk = query.isSelectType() || query.isAskType() || query.isConstructType();
		afou = afou && selectOrAsk;
		afo = afo && selectOrAsk;

		boolean cpfNoRegex = !hasRegex && cpf;
		boolean singleSimpleTriple = cpfNoRegex && !hasFilter && SparqlAlgorithms.collectTriples(element).size() <= 1;

		boolean wellBehaved = !hasRegex && afou && SparqlProperties.get().isWellBehaved(element);

		boolean uwd = !hasRegex && afou && DesignCheck.isUwd(element);
		boolean uwwd = !hasRegex && afou && DesignCheck.isUwwd(element);
		boolean wd = !hasRegex && afo && DesignCheck.isUwd(element);
		boolean wwd = !hasRegex && afo && DesignCheck.isUwwd(element);

		boolean optAFU = !hasRegex && hasOptional && afou;


		Element unionNormalized = SparqlTransducer.get().unionNormalize(element);
		boolean uwdComp = optAFU && DesignCheck.unionDecomposition(
			unionNormalized).map(x -> x.apply(DesignCheck::isUwd)).orElse(false);
		boolean uwwdComp = optAFU && DesignCheck.unionDecomposition(
			unionNormalized).map(x -> x.apply(DesignCheck::isUwwd)).orElse(false);



		boolean acyclicFO = selectOrAsk && !hasRegex && afo &&
			SparqlProperties.get().isAcyclic(element);
		boolean acyclic = acyclicFO && cpf;
		boolean acyclicNoFilter = acyclic && !hasFilter;

		DirectedGraph<Object, DefaultEdge> graph = SparqlGraph.graphFromQuery(element);
		boolean forest = !hasFilter && cpf && SparqlGraph.isForest(graph);
		boolean tree = !hasFilter && cpf && SparqlGraph.isTree(graph);
		boolean forestNoRegex = !hasRegex && forest;
		boolean treeNoRegex = !hasRegex && tree;
		boolean forestF = cpf && SparqlGraph.isForest(graph);
		boolean treeF = cpf && SparqlGraph.isTree(graph);
		boolean forestNoRegexF = !hasRegex && forestF;
		boolean treeNoRegexF = !hasRegex && treeF;
		boolean forestFO = afo && SparqlGraph.isForest(graph);
		boolean treeFO = afo && SparqlGraph.isTree(graph);
		boolean forestNoRegexFO = !hasRegex && forestFO;
		boolean treeNoRegexFO = !hasRegex && treeFO;

		shapeAnalysis.analyzeShape(query, flagWalker);

		doKeywordAnalyze(query);





		boolean teePredicate = SparqlGraph.hasTeePredicate(element);


		if (wellBehaved) {
			wbCounter.getAndIncrement();
		}
		if (uwd) {
			uwdCounter.getAndIncrement();
		}
		if (uwwd) {
			uwwdCounter.getAndIncrement();
		}
		if (wd) {
			wdCounter.getAndIncrement();
		}
		if (wwd) {
			wwdCounter.getAndIncrement();
		}
		if (uwdComp) {
			uwdCompCounter.getAndIncrement();
		}
		if (uwwdComp) {
			uwwdCompCounter.getAndIncrement();
		}
		if (optAFU) {
			optAFUCounter.getAndIncrement();
		}
		if (singleSimpleTriple) {
			singleSimpleTripleCounter.getAndIncrement();
		}
		if (hasRegex) {
			regexCounter.getAndIncrement();
		}
		if (cpf) {
			cpfCounter.getAndIncrement();
		}
		if (cpfNoRegex) {
			cpfNoRegexCounter.getAndIncrement();
		}
		if (acyclicFO) {
			acyclicFOCounter.getAndIncrement();
		}
		if (acyclic) {
			acyclicCounter.getAndIncrement();
		}
		if (acyclicNoFilter) {
			acyclicNoFilterCounter.getAndIncrement();
		}
		if (forest) {
			forestCounter.getAndIncrement();
		}
		if (tree) {
			treeCounter.getAndIncrement();
		}
		if (forestNoRegex) {
			forestNoRegexCounter.getAndIncrement();
		}
		if (treeNoRegex) {
			treeNoRegexCounter.getAndIncrement();
		}
		if (forestF) {
			forestFCounter.getAndIncrement();
		}
		if (treeF) {
			treeFCounter.getAndIncrement();
		}
		if (forestNoRegexF) {
			forestNoRegexFCounter.getAndIncrement();
		}
		if (treeNoRegexF) {
			treeNoRegexFCounter.getAndIncrement();
		}
		if (forestFO) {
			forestFOCounter.getAndIncrement();
		}
		if (treeFO) {
			treeFOCounter.getAndIncrement();
		}
		if (forestNoRegexFO) {
			forestNoRegexFOCounter.getAndIncrement();
		}
		if (treeNoRegexFO) {
			treeNoRegexFOCounter.getAndIncrement();
		}
		if (teePredicate) {
			teePredicateCounter.getAndIncrement();
		}












	}

	@Deprecated
	private void processOnTopLevelUnions(Element element, FlagWalker flagWalker) {


		if (flagWalker.subquery.isTrue() ||
			flagWalker.graph.isTrue() ||
			flagWalker.optional.isTrue()) {
			return;
		}
		boolean hasRegex = SparqlProperties.get().hasPath(element);

		List<Element> unionArgs = new ArrayList<>();
		SparqlAlgorithms.collectUnionArgs(unionArgs, element);
		boolean onlyTopLevelUnion = unionArgs.stream().allMatch(
			SparqlAlgorithms::isUnionFree);
		if (onlyTopLevelUnion) {
			boolean uForest = true;
			boolean uTree = true;
			for (Element el : unionArgs) {
				DirectedGraph<Object, DefaultEdge> graph = SparqlGraph.graphFromQuery(el);
				if (!SparqlGraph.isForest(graph)) {
					uForest = false;
				}
				if (!SparqlGraph.isTree(graph)) {
					uTree = false;
				}
			}
			boolean uForestNoRegex = !hasRegex && uForest;
			boolean uTreeNoRegex = !hasRegex && uTree;
			if (uForest) {
				uForestCounter.getAndIncrement();
			}
			if (uTree) {
				uTreeCounter.getAndIncrement();
			}
			if (uForestNoRegex) {
				uForestNoRegexCounter.getAndIncrement();
			}
			if (uTreeNoRegex) {
				uTreeNoRegexCounter.getAndIncrement();
			}
		}
	}

	public void mapCounters(Map<String, Long> map) {
		map.put("regex", regexCounter.get());
		map.put("singleSimpleTriple", singleSimpleTripleCounter.get());
		map.put("cpfNoRegex", cpfNoRegexCounter.get());
		map.put("cpf", cpfCounter.get());
		map.put("acyclicNoFilter", acyclicNoFilterCounter.get());
		map.put("acyclic", acyclicCounter.get());
		map.put("acyclicFO", acyclicFOCounter.get());
		map.put("wb", wbCounter.get());
		map.put("uwd", uwdCounter.get());
		map.put("uwwd", uwwdCounter.get());
		map.put("wd", wdCounter.get());
		map.put("wwd", wwdCounter.get());
		map.put("optAFU", optAFUCounter.get());
		map.put("uwdComp", uwdCompCounter.get());
		map.put("uwwdComp", uwwdCompCounter.get());

		shapeAnalysis.outputCount(map);
		shapeAnalysis.commit();




		map.put("forestNoRegex", forestNoRegexCounter.get());
		map.put("treeNoRegex", treeNoRegexCounter.get());
		map.put("forest", forestCounter.get());
		map.put("tree", treeCounter.get());
		map.put("forestNoRegexF", forestNoRegexFCounter.get());
		map.put("treeNoRegexF", treeNoRegexFCounter.get());
		map.put("forestF", forestFCounter.get());
		map.put("treeF", treeFCounter.get());
		map.put("forestNoRegexFO", forestNoRegexFOCounter.get());
		map.put("treeNoRegexFO", treeNoRegexFOCounter.get());
		map.put("forestFO", forestFOCounter.get());
		map.put("treeFO", treeFOCounter.get());
		map.put("uForestNoRegex", uForestNoRegexCounter.get());
		map.put("uTreeNoRegex", uTreeNoRegexCounter.get());
		map.put("uForest", uForestCounter.get());
		map.put("uTree", uTreeCounter.get());

		map.put("teePredicate", teePredicateCounter.get());

		mapKeywords(map);
	}

	private static final List<String> keywords = Arrays.asList(
		"REDUCED",
		"GROUP BY",
		"HAVING",
		"ORDER BY",
		"LIMIT",
		"OFFSET",
		"VALUES");
	private static final List<String> operators = Arrays.asList(
		"SAMPLE",
		"GROUP_CONCAT"
	);
	private Map<String, LongAdder> keywordCounterMap = new ConcurrentHashMap<>();
	private static final LongAdder DUMMY_ADDER = new LongAdder();

	private void doKeywordAnalyze(Query query) {
		try {
			keywordAnalyze(query.toString(), key ->
				keywordCounterMap.computeIfAbsent(key, k -> new LongAdder()).increment());
		} catch (Exception ignored) {

		}
	}

	public static void keywordAnalyze(String queryStr, Consumer<String> consumer) {
		List<String> lines = Arrays.asList(queryStr.split("\n|(\r\n)"));
		lines = lines.stream().filter(s -> !s.startsWith("#")).collect(Collectors.toList());
		String input = String.join("\n", lines);
		keywords.forEach(keyword -> checkKeyword(input, keyword, wrapWord(keyword), consumer));
		operators.forEach(operator -> checkKeyword(input, operator, operator, consumer));
	}

	private static void checkKeyword(String input, String key, String patternStr, Consumer<String> consumer) {
		Pattern pattern = Pattern.compile(patternStr, Pattern.CASE_INSENSITIVE);
		Matcher matcher = pattern.matcher(input);
		if (matcher.find()) {
			consumer.accept(key);
		}
	}

	private static String wrapWord(String word) {
		return String.format("(?<!\\S)%s(?!\\S)", Pattern.quote(word));
	}

	private void mapKeywords(Map<String, Long> map) {
		for (String keyword : keywords) {
			map.put(keyword, keywordCounterMap.getOrDefault(keyword, DUMMY_ADDER).longValue());
		}
		for (String operator : operators) {
			map.put(operator, keywordCounterMap.getOrDefault(operator, DUMMY_ADDER).longValue());
		}
	}
}
