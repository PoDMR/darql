package com.gitlab.ctt.arq.analysis.aspect;

import com.gitlab.ctt.arq.analysis.FileOutput;
import com.gitlab.ctt.arq.analysis.Job;
import com.gitlab.ctt.arq.sparql.SparqlAlgorithms;
import com.gitlab.ctt.arq.util.SparqlUtil;
import com.gitlab.ctt.arq.utilx.Resources;
import fj.data.Either;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.Node_URI;
import org.apache.jena.query.Query;
import org.apache.jena.sparql.core.TriplePath;
import org.apache.jena.sparql.syntax.Element;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.PrintWriter;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.Collectors;

public class UriCounter implements Job<Either<Exception, Query>, Void> {
	public static void main(String[] args) {
		UriCounter uriCounter = new UriCounter();
		uriCounter.init();
		String sparqlStr = Resources.getResourceAsString("sample/misc/scrap.sparql");
		Either<Exception, Query> mQuery = SparqlUtil.get().toQuery(sparqlStr);
		uriCounter.apply(mQuery);
		Map<String, Long> map = UriCounter.addersToNum(uriCounter.map);
		System.out.println(map);
	}

	private String tag;
	protected Map<String, LongAdder> map;

	@Override
	public void setTag(String tag) {
		this.tag = tag;
	}

	protected String getBasename() {
		return "uri_count.yaml";
	}

	@Override
	public void init() {
		map = new ConcurrentHashMap<>();
	}

	@Override
	public void commit() {
		if (map != null) {
			String outputDirName = Resources.getLocalProperty("arq.out.dir");
			File parent = tag != null ? new File(outputDirName, tag) : new File(outputDirName);
			File file = new File(parent, getBasename());
			FileOutput fileOutput = new FileOutput(file.getPath());
			fileOutput.init();
			PrintWriter writer = fileOutput.getWriter();
			DumperOptions options = new DumperOptions();
			options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
			Yaml yaml = new Yaml(options);
			yaml.dump(addersToNum(map), writer);
			fileOutput.commit();  
		}
		map = null;
	}

	private static Map<String, Long> addersToNum(Map<String, LongAdder> map) {
		return map.entrySet().stream().collect(Collectors.toMap(
			Map.Entry::getKey, e -> e.getValue().sum()
		));
	}

	@Override
	public Void apply(Either<Exception, Query> maybeQuery) {
		if (maybeQuery.isRight()) {
			Query query = maybeQuery.right().value();
			Element element = query.getQueryPattern();
			if (element != null) {
				Set<TriplePath> tps = SparqlAlgorithms.collectTriples(element);
				for (TriplePath tp : tps) {
					increment(tp.getSubject());
					increment(tp.getObject());
					increment(tp.getPredicate());
				}
			}
		}
		return null;
	}

	private void increment(Node node) {
		if (node instanceof Node_URI) {
			Node_URI uri = (Node_URI) node;
			String uriStr = uri.toString(true);
			map.computeIfAbsent(uriStr, k -> new LongAdder()).increment();
		}
	}
}
