package com.gitlab.ctt.arq.analysis.aspect;

import com.gitlab.ctt.arq.analysis.FileOutput;
import com.gitlab.ctt.arq.analysis.Job;
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

	private AtomicLong totalCounter = new AtomicLong();
	private StructureCount structureCount;
	private FeatureCount featureCount;
	private String tag;

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
		if (maybeQuery.isRight()) {
			Query query = maybeQuery.right().value();
			handleQuery(query);
		}
		return null;
	}

	private void handleQuery(Query query) {
		Element element = query.getQueryPattern();
		if (element != null) {
			long currentCount = totalCounter.getAndIncrement();



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
		totalCounter.set(0L);
	}

	private Map<String, Long> getStringLongMap() {
		Map<String, Long> map = new LinkedHashMap<>();
		map.put("totalElements", totalCounter.get());
		structureCount.mapCounters(map);
		featureCount.mapCounters(map);
		return map;
	}
}
