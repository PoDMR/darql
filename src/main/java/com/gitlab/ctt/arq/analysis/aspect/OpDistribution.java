package com.gitlab.ctt.arq.analysis.aspect;

import com.gitlab.ctt.arq.analysis.FileOutput;
import com.gitlab.ctt.arq.analysis.Job;
import com.gitlab.ctt.arq.analysis.aspect.util.FlagWalker;
import com.gitlab.ctt.arq.sparql.SparqlProperties;
import com.gitlab.ctt.arq.util.SparqlUtil;
import com.gitlab.ctt.arq.utilx.Resources;
import fj.data.Either;
import org.apache.commons.lang3.StringUtils;
import org.apache.jena.query.Query;
import org.apache.jena.sparql.syntax.Element;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.PrintWriter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

public class OpDistribution implements Job<Either<Exception, Query>, Void> {
	public static void main(String[] args) {
		String sparqlStr = Resources.getResourceAsString("sample/misc/scrap.sparql");
		Either<Exception, Query> mQuery = SparqlUtil.get().toQuery(sparqlStr);
		OpDistribution op = new OpDistribution();
		op.init();
		op.apply(mQuery);
		Map<String, Integer> map = op.transformMap();
		System.out.println(map);
	}

	private static final String BASENAME = "op_distribution.yaml";
	private static final String BASENAME2 = "op_distribution_sa.yaml";
	protected Map<Integer, Integer> map;
	private String tag;
	private boolean onlySelectAsk;

	public OpDistribution() {
		this(false);
	}

	public OpDistribution(boolean onlySelectAsk) {
		this.onlySelectAsk = onlySelectAsk;
	}

	@Override
	public void init() {
		map = new TreeMap<>();
	}

	@Override
	public void setTag(String tag) {
		this.tag = tag;
	}

	protected String getBasename() {
		return onlySelectAsk ? BASENAME2 : BASENAME;
	}

	@Override
	public Void apply(Either<Exception, Query> maybeQuery) {
		if (maybeQuery.isRight()) {
			Query query = maybeQuery.right().value();
			Element element = query.getQueryPattern();
			if (element != null) {
				boolean onlySA = !onlySelectAsk || (query.isAskType() || query.isSelectType());
				if (onlySA) {
					if (!SparqlProperties.get().hasPath(element)) {
						accept(element);
					} else {
						increment(1 << 16);  
					}
				}
			}
		}
		return null;
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
			yaml.dump(transformMap(), writer);
			fileOutput.commit();  
		}
		map = null;
	}

	private Map<String, Integer> transformMap() {
		Map<String, Integer> result = new LinkedHashMap<>();
		for (Map.Entry<Integer, Integer> entry : map.entrySet()) {
			int bitset = entry.getKey();
			if (bitset <= 0b11111) {
				String label = "";
				if ((bitset & 0b00001) != 0) {
					label += ",AND";
				}
				if ((bitset & 0b00100) != 0) {
					label += ",OPT";
				}
				if ((bitset & 0b00010) != 0) {
					label += ",UNION";
				}
				if ((bitset & 0b01000) != 0) {
					label += ",FILTER";
				}
				if ((bitset & 0b10000) != 0) {
					label += ",GRAPH";
				}
				if (StringUtils.isEmpty(label)) {
					label = "none";
				} else {
					label = label.substring(1);
				}
				result.put(label, entry.getValue());
			} else {
				String restKey = "other";  
				int val = result.getOrDefault(restKey, 0);
				result.put(restKey, val + entry.getValue());
			}
		}
		return result;
	}

	public void accept(Element element) {
		FlagWalker flagWalker = new FlagWalker() {
			@Override
			public void flush() {
				increment((int) this.asLong());
			}
		};
		flagWalker.consume(element);
		flagWalker.flush();
	}

	private synchronized void increment(int bitset) {
		Integer val = map.get(bitset);
		if (val == null) {
			val = 0;
		}
		map.put(bitset, val + 1);
	}
}
