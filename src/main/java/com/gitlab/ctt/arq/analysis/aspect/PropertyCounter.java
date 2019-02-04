package com.gitlab.ctt.arq.analysis.aspect;

import com.gitlab.ctt.arq.analysis.FileOutput;
import com.gitlab.ctt.arq.analysis.Job;
import com.gitlab.ctt.arq.util.SparqlUtil;
import com.gitlab.ctt.arq.utilx.Resources;
import fj.data.Either;
import org.apache.jena.query.Query;
import org.apache.jena.sparql.syntax.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.PrintWriter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;


public class PropertyCounter implements Job<Either<Exception, Query>, Void> {
	private static final Logger LOGGER = LoggerFactory.getLogger(PropertyCounter.class);
	private static final String BASENAME = "property_count.yaml";

	private AtomicLong itemCounter = new AtomicLong();
	private AtomicLong validCounter = new AtomicLong();
	private AtomicLong elementCounter = new AtomicLong();
	private StructureCount structureCount;
	private FeatureCount featureCount;
	private String tag;

	public static void main(String[] args) {
		PropertyCounter processor = new PropertyCounter();
		processor.init();
		String sparqlStr = Resources.getResourceAsString("sample/misc/scrap.sparql");
		Either<Exception, Query> maybeQuery = SparqlUtil.get().toQuery(sparqlStr);
		if (maybeQuery.isRight()) {
			Query query = maybeQuery.right().value();
			processor.handleQuery(query);
		} else {
			throw new RuntimeException(maybeQuery.left().value());
		}
	}

	@Override
	public void setTag(String tag) {
		this.tag = tag;
	}

	@Override
	public void init() {
		structureCount = new StructureCount(tag);
		featureCount = new FeatureCount();
	}

	@Override
	public Void apply(Either<Exception, Query> maybeQuery) {
		itemCounter.getAndIncrement();
		if (maybeQuery.isRight()) {
			validCounter.getAndIncrement();
			Query query = maybeQuery.right().value();
			handleQuery(query);
		}
		return null;
	}

	private void handleQuery(Query query) {
		Element element = query.getQueryPattern();
		if (element != null) {
			long currentCount = elementCounter.getAndIncrement();



			structureCount.handleQuery(query);

		}
		featureCount.processQuery(query);
	}

	@Override
	public void commit() {
		if (structureCount != null && featureCount != null) {
			String outputDirName = Resources.getLocalProperty("arq.out.dir");
			File parent = tag != null ? new File(outputDirName, tag) : new File(outputDirName);
			File file = new File(parent, BASENAME);
			FileOutput fileOutput = new FileOutput(file.getPath());
			fileOutput.init();
			PrintWriter writer = fileOutput.getWriter();
			DumperOptions options = new DumperOptions();
			options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
			Yaml yaml = new Yaml(options);
			yaml.dump(getStringLongMap(), writer);
			fileOutput.commit();  
		}
		structureCount = null;
		featureCount = null;
		itemCounter.set(0L);
		validCounter.set(0L);
		elementCounter.set(0L);
	}

	private Map<String, Long> getStringLongMap() {
		Map<String, Long> map = new LinkedHashMap<>();
		map.put("totalQueries", itemCounter.get());
		map.put("totalValid", validCounter.get());
		map.put("totalElements", elementCounter.get());
		structureCount.mapCounters(map);
		featureCount.mapCounters(map);
		return map;
	}
}
