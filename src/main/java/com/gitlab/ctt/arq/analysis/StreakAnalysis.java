package com.gitlab.ctt.arq.analysis;

import com.gitlab.ctt.arq.analysis.aspect.db.QueryRecord;
import com.gitlab.ctt.arq.analysis.aspect.util.FlagWalker;
import com.gitlab.ctt.arq.sparql.SparqlAlgorithms;
import com.gitlab.ctt.arq.sparql.SparqlGraph;
import com.gitlab.ctt.arq.sparql.SparqlProperties;
import com.gitlab.ctt.arq.sparql.check.DesignCheck;
import com.gitlab.ctt.arq.sparql.check.OptCheck;
import com.gitlab.ctt.arq.sparql.check.OptCheck2;
import com.gitlab.ctt.arq.util.GraphShape;
import com.gitlab.ctt.arq.util.QueryFixer;
import com.gitlab.ctt.arq.util.SparqlUtil;
import com.gitlab.ctt.arq.utilx.Resources;
import com.gitlab.ctt.arq.utilx.RingBuffer;
import fj.data.Either;
import org.apache.commons.lang3.StringUtils;
import org.apache.jena.query.Query;
import org.apache.jena.sparql.core.TriplePath;
import org.apache.jena.sparql.syntax.Element;
import org.jgrapht.DirectedGraph;
import org.jgrapht.UndirectedGraph;
import org.jgrapht.graph.AsUndirectedGraph;
import org.jgrapht.graph.DefaultEdge;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class StreakAnalysis<T> implements Job<Either<Exception, Query>, Void> {
	public static void main(String[] args) {
		StreakAnalysis<String> sa = StreakAnalysis.stringStreakAnalysis(5, 0.75d);
		sa.init();
		sa.accept("SELECT * WHERE { ?x a ?y }", 0);
		sa.accept("SELECT * WHERE { ?x a ?z }", 1);
		sa.accept("select * where { ?i p ?j }", 2);
		sa.flushRest();
	}

	public static class Streak<T> {
		public T tail;
		public int size;
		public double wSize;
		public int number;
		public List<T> series;

		public Streak(T tail, int size, double wSize, int number, boolean storeFullStreaks) {
			this.tail = tail;
			this.size = size;
			this.wSize = wSize;
			this.number = number;
			if (storeFullStreaks) {
				series = new ArrayList<>();
				series.add(tail);
			}
		}

		@Override
		public String toString() {
			return tail.toString();
		}
	}

	private static class MetaRecord {
		public int tripleCount;
		public String shapeClass;

		public MetaRecord(int tripleCount, String shapeClass) {
			this.tripleCount = tripleCount;
			this.shapeClass = shapeClass;
		}
	}

	private static final Pattern PREFIX_PATTERN = Pattern.compile("(PREFIX\\s[^>]+>\\s+)+");
	private static final int PARSE_ERR = -1;
	private static final int NULL_BODY = -2;
	private static final String UNDEFINED_QUERY = "u";
	private static final String EMPTY_BODY = "e";
	private static final String FILTERED_PRECONDITION = "f";
	private static final String SHAPE_CYCLIC = "c";
	private static final String SHAPE_BRANCHING = "b";
	private static final String SHAPE_LINEAR = "s";


	public static double compare(String str1, String str2) {
		int distance = StringUtils.getLevenshteinDistance(str1, str2);
		int maxLen = Math.max(str1.length(), str2.length());
		return (maxLen - distance) * 1.d / maxLen;
	}

	public static String stringPreprocess(String string) {
		return PREFIX_PATTERN.matcher(string).replaceAll("");
	}


	public static String queryToString(Either<Exception, Query> maybeQuery) {
		return maybeQuery.right().value().toString();
	}

	public static Either<Exception, Query> extractQuery(String sparqlStr) {
		String sparqlStr2 = QueryFixer.get().fix(sparqlStr);
		return SparqlUtil.get().toQuery(sparqlStr2);
	}

	private int bufferSize;
	private double threshold;
	private BiFunction<T, T, Double> diffFunc;
	private Function<T, T> stepTransform;
	private Function<T, Either<Exception, Query>> queryExtract;
	private Function<Either<Exception, Query>, T> queryAsInput;

	private RingBuffer<Streak<T>> items;
	private Map<Integer, Integer> lenCount;
	private Map<Double, Integer> wLenCount;
	private String tag;
	private int counter;
	private final boolean extendedAnalysis = true;
	private List<List<MetaRecord>> metaStreaks;

	public static StreakAnalysis<String> stringStreakAnalysis(int bufferSize, double threshold) {
		return new StreakAnalysis<>(bufferSize, threshold,
			StreakAnalysis::compare,
			StreakAnalysis::stringPreprocess,
			StreakAnalysis::extractQuery,
			StreakAnalysis::queryToString
		);

	}

	public StreakAnalysis(int bufferSize, double threshold,
			BiFunction<T, T, Double> diffFunc,
			Function<T, T> stepTransform,
			Function<T, Either<Exception, Query>> queryExtract,
			Function<Either<Exception, Query>, T> queryAsInput
	) {
		this.bufferSize = bufferSize;
		this.threshold = threshold;
		this.diffFunc = diffFunc;
		this.stepTransform = stepTransform;
		this.queryExtract = queryExtract;
		this.queryAsInput = queryAsInput;
	}


	public void accept(T string, long id) {
		for (int i = items.size() - 1; i >= 0; i--) {
			if ((counter - items.get(i).number) >= bufferSize) {
				flush(i);
			}
		}
		T trimString = stepTransform.apply(string);

		List<Integer> matches = parallelMatch(trimString);
		int hits = matches.size();
		for (Integer i : matches) {
			double w0 = 1d;
			double w1 = w0 / hits;
			update(i, trimString, w1);
		}

		if (hits <= 0) {
			while (items.size() >= bufferSize) {
				flush(0);
			}
			create(new Streak<>(trimString, 1, 1d, counter, extendedAnalysis));
		}
		counter++;
	}


	private List<Integer> performMatch(T trimString) {
		List<Integer> matches = new ArrayList<>();
		for (int i = 0; i < items.size(); i++) {
			Streak<T> other = items.get(i);
			double score = diffFunc.apply(trimString, other.tail);
			if (score >= threshold) {
				matches.add(i);
			}
		}
		return matches;
	}

	private List<Integer> parallelMatch(T trimString) {
		return IntStream.range(0, items.size()).boxed().parallel().map(i -> {
			Streak<T> other = items.get(i);
			double score = diffFunc.apply(trimString, other.tail);
			if (score >= threshold) {
				return i;
			}
			return null;
		}).filter(Objects::nonNull).sorted().collect(Collectors.toList());
	}

	private void create(Streak<T> streak) {
		items.add(streak);
	}

	private void update(int i, T trimString, double w1) {
		items.get(i).tail = trimString;
		items.get(i).size++;
		items.get(i).wSize += w1;
		items.get(i).number = counter;
		if (extendedAnalysis) {
			items.get(i).series.add(trimString);
		}
	}

	public void flushRest() {
		while (items.size() > 0) {
			flush(0);
		}
	}

	private void flush(int i) {
		Streak<T> streak = items.remove(0);


		lenCount.putIfAbsent(streak.size, 0);
		lenCount.computeIfPresent(streak.size, (k, v) -> v + 1);
		wLenCount.putIfAbsent(streak.wSize, 0);
		wLenCount.computeIfPresent(streak.wSize, (k, v) -> v + 1);
		if (extendedAnalysis) {
			extraAnalysis(streak);
		}
	}

	private void extraAnalysis(Streak<T> streak) {
		List<MetaRecord> metaStreak = streak.series.parallelStream().map(sparqlStr -> {
			Either<Exception, Query> maybeQuery = queryExtract.apply(sparqlStr);
			if (maybeQuery.isRight()) {
				Element element = maybeQuery.right().value().getQueryPattern();
				if (element != null) {
					Set<TriplePath> tps = SparqlAlgorithms.collectTriples(element);
					return new MetaRecord(tps.size(), shapeString(element));
				} else {
					return new MetaRecord(NULL_BODY, EMPTY_BODY);
				}
			} else {
				return new MetaRecord(PARSE_ERR, UNDEFINED_QUERY);
			}
		}).filter(Objects::nonNull).collect(Collectors.toList());

		metaStreaks.add(metaStreak);
	}

	
	private static String shapeString(Element element) {
		FlagWalker flagWalker = new FlagWalker();
		flagWalker.consume(element);
		long flagLong = flagWalker.asLong();
		if (!((flagLong & ~(1|8|4)) == 0)) {
			return "1";

		}
		if (SparqlProperties.get().hasPath(element)) {
			return "2";

		}
		if (SparqlGraph.hasTeePredicate(element)) {
			return "3";

		}
		DirectedGraph<Object, DefaultEdge> graph = SparqlGraph.graphFromQuery(element);
		if (graph == null) {
			return FILTERED_PRECONDITION;
		}
		if (!DesignCheck.isUwd(element)) {
			return "5";
		}
		OptCheck optCheck = OptCheck.check(element);



		if (optCheck.isBadInterface()) {
			return "7";

		}
		try {
			if (!OptCheck2.wdpt(element)) {
				return "8";

			}
		} catch (Exception e) {
			return "9";

		}





		UndirectedGraph<Object, DefaultEdge> ug = new AsUndirectedGraph<>(graph);
		if (GraphShape.isCyclic(ug)) {
			return SHAPE_CYCLIC;
		} else if (ug.vertexSet().stream().anyMatch(v -> ug.degreeOf(v) > 2)) {
			return SHAPE_BRANCHING;  
		}
		return SHAPE_LINEAR;  
	}

	@Override
	public void init() {
		items = new RingBuffer<>(bufferSize);
		lenCount = new TreeMap<>();
		wLenCount = new TreeMap<>();
		counter = 0;
		if (extendedAnalysis) {
			metaStreaks = new ArrayList<>();
		}
	}


	@Override
	public Void apply(Either<Exception, Query> maybeQuery) {
		if (maybeQuery.isRight()) {
			accept(queryAsInput.apply(maybeQuery), 0);
		}
		return null;
	}

	@Override
	public void commit() {
		if (items != null) {
			flushRest();
		}
		if (lenCount != null && wLenCount != null) {
			String outputDirName = Resources.getLocalProperty("arq.out.dir");
			File parent = tag != null ? new File(outputDirName, tag) : new File(outputDirName);
			File file = new File(parent, "streaks.yaml");
			FileOutput.withWriter(file, writer -> {
				DumperOptions options = new DumperOptions();
				options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
				Yaml yaml = new Yaml(options);
				yaml.dump(lenCount, writer);
				writer.println("---");
				yaml.dump(wLenCount, writer);
			});
			if (extendedAnalysis) {
				File file2 = new File(parent, "streaksTripleCount.csv");
				FileOutput.withWriter(file2, writer -> {
					for (List<MetaRecord> streak : metaStreaks) {
						String line = streak.stream()
							.map(e -> String.valueOf(e.tripleCount))
							.collect(Collectors.joining(","));
						writer.println(line);
					}
				});
				File file3 = new File(parent, "streaksShape.csv");
				FileOutput.withWriter(file3, writer -> {
					for (List<MetaRecord> streak : metaStreaks) {
						String line = streak.stream().map(e -> e.shapeClass)
							.collect(Collectors.joining(","));
						writer.println(line);
					}
				});
			}
		}
		lenCount = null;
		wLenCount = null;
	}

	@Override
	public void setTag(String tag) {
		this.tag = tag;
	}
}
