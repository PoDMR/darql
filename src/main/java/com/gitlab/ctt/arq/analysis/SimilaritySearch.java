package com.gitlab.ctt.arq.analysis;

import com.gitlab.ctt.arq.analysis.aspect.util.JenaTed;
import com.gitlab.ctt.arq.core.BatchProcessor;
import com.gitlab.ctt.arq.core.FileDispatcher;
import com.gitlab.ctt.arq.core.format.QueryEntry;
import com.gitlab.ctt.arq.utilx.Resources;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.jena.query.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.LongStream;

public class SimilaritySearch implements Job<QueryEntry, Boolean> {
	private static final Logger LOGGER = LoggerFactory.getLogger(SimilaritySearch.class);
	private Map<QueryEntry, AbstractQueue<Pair<QueryEntry, Double>>> map1;
	private Map<QueryEntry, Map<Long, LongAdder>> map2;
	private List<QueryEntry> qe1s;



	private List<Long> steps = LongStream.iterate(1000L, x -> x - 25L)
		.limit((1000L - 750L) / 25L + 1).boxed().collect(Collectors.toList());
	private int limit;
	private double threshold;
	private int variant = 1;
	private String tag;
	private String searchInput = "wikidata_nomod";

	public SimilaritySearch() {
		this(0.75, 32);
	}

	public SimilaritySearch(double threshold, int limit) {
		this.limit = limit;
		this.threshold = threshold;
		this.map1 = new ConcurrentHashMap<>();
		this.map2 = new ConcurrentHashMap<>();
		String searchInputEnv = System.getenv("SEARCH_INPUT");
		if (searchInputEnv != null) {
			searchInput = searchInputEnv;
		}
	}

	@Override
	public void init() {
		qe1s = new ArrayList<>();
		FileDispatcher fd = new FileDispatcher(1) {
			@Override
			protected void processEntry(QueryEntry queryEntry) {
				qe1s.add(queryEntry);
			}
		};
		fd.setValidJobs(Collections.emptyList());
		BatchProcessor bp = new BatchProcessor(fd, searchInput);



		bp.processFromMasterFile("src/main/resources/config.yaml", false);
		fd.waitForAll();

		qe1s.removeIf(Objects::isNull);
		qe1s.removeIf(e -> e.maybeQuery == null);

		qe1s.removeIf(e -> e.maybeQuery.isLeft());
		LOGGER.info("Search query count: {}", qe1s.stream().filter(e -> e.maybeQuery.isRight()).count());

		for (QueryEntry qe : qe1s) {
			if (variant == 0) {
				map1.putIfAbsent(qe, new PriorityBlockingQueue<>(limit,
					Comparator.comparingDouble(Pair::getRight)));
			}
			else {
				Map<Long, LongAdder> map3 = new ConcurrentHashMap<>();
				map2.put(qe, map3);
				for (Long step : steps) {
					map3.put(step, new LongAdder());
				}
			}
		}
	}

	@Override
	public void setTag(String tag) {
		this.tag = tag;
	}

	@Override
	public Boolean apply(QueryEntry qe2) {
		if (qe2.maybeQuery.isRight()) {

			for (QueryEntry qe1 : qe1s) {

					assess(qe1, qe2);

			}
			}
		return false;
	}

	private void assess(QueryEntry qe1, QueryEntry qe2) {
		Query q1 = qe1.maybeQuery.right().value();
		Query q2 = qe2.maybeQuery.right().value();
		JenaTed ted = JenaTed.compute(q1, q2);
		double similarity = ted.similarity();
		if (similarity >= threshold) {
			if (variant == 0) {
				AbstractQueue<Pair<QueryEntry, Double>> collection = map1.get(qe1);
				collection.add(Pair.of(qe2, similarity));
				if (collection.size() > limit) {
					collection.remove();
				}
			} else {
				Map<Long, LongAdder> step2count = map2.get(qe1);

				long step = (long) (Math.floor(similarity * 40) / 40 * 1000);  
				step2count.computeIfAbsent(step, k -> new LongAdder()).increment();
			}
		}
	}

	@Override
	public void commit() {
		List<Map<String, Object>> output = new ArrayList<>();

		if (variant == 0) {
			sortedMap(map1).forEach(entry -> {
				Map<String, Object> item = new LinkedHashMap<>();
				output.add(item);
				QueryEntry qe1 = entry.getKey();
				item.put("src_origin", qe1.origin);
				item.put("src_query_str", qe1.queryStr.replaceAll("\t", "    "));
				AbstractQueue<Pair<QueryEntry, Double>> pairs = entry.getValue();
				List<Pair<QueryEntry, Double>> revPairs = new ArrayList<>(pairs);
				Collections.reverse(revPairs);
				item.put("count", revPairs.size());
				List<Object> subItems = new ArrayList<>();
				item.put("matches", subItems);
				for (Pair<QueryEntry, Double> pair : revPairs) {
					Map<String, Object> hit = new LinkedHashMap<>();
					subItems.add(hit);
					QueryEntry qe2 = pair.getLeft();
					String origin = qe2.origin;
					String queryStr = qe2.queryStr.replaceAll("\t", "    ");
					Double value = pair.getRight();
					hit.put("dst_origin", origin);
					hit.put("dst_query_str", queryStr);
					hit.put("dst_score", value);
				}
			});
		} else {
			sortedMap(map2).forEach(entry -> {
				Map<String, Object> item = new LinkedHashMap<>();
				output.add(item);
				QueryEntry qe1 = entry.getKey();
				item.put("src_origin", qe1.origin);
				Map<Long, LongAdder> step2count = entry.getValue();

				List<Map<String, Long>> items = step2count.entrySet().stream()
					.filter(e -> e.getValue().sum() > 0L)
					.sorted(Comparator.comparingDouble(
						(Entry<Long, LongAdder> e) -> e.getKey()).reversed())
					.map(e -> {
						Map<String, Long> outMap = new LinkedHashMap<>();


						outMap.put(e.getKey().toString(), e.getValue().sum());
						return outMap;
					})
					.collect(Collectors.toList());
				item.put("hits", items);
			});
		}

		if (!output.isEmpty()) {
			String outputDirName = Resources.getLocalProperty("arq.out.dir");
			File parent = tag != null ? new File(outputDirName, tag) : new File(outputDirName);
			File file = new File(parent, "similarity.yaml");
			FileOutput.withWriter(file, writer -> {
				DumperOptions options = new DumperOptions();
				options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);

				Yaml yaml = new Yaml(options);
				yaml.dump(output, writer);
				writer.println("---");
			});
			map1.clear();
			map2.clear();
			qe1s.clear();
		}
	}

	private static <T> List<Entry<QueryEntry, T>> sortedMap(Map<QueryEntry, T> map) {
		return map.entrySet().stream().sorted((a, b) -> {
			try {
				String t1 = a.getKey().origin.replaceAll(".*:", "");
				String t2 = b.getKey().origin.replaceAll(".*:", "");
				int i = Integer.parseInt(t1);
				int j = Integer.parseInt(t2);
				return i - j;
			} catch (Exception ignored) {
			}
			return 0;
		}).collect(Collectors.toList());
	}
}
