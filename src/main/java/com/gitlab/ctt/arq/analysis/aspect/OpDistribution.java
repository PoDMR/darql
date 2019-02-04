package com.gitlab.ctt.arq.analysis.aspect;

import com.gitlab.ctt.arq.analysis.FileOutput;
import com.gitlab.ctt.arq.analysis.Job;
import com.gitlab.ctt.arq.analysis.aspect.util.FlagWalker;
import com.gitlab.ctt.arq.sparql.SparqlProperties;
import com.gitlab.ctt.arq.util.SparqlUtil;
import com.gitlab.ctt.arq.utilx.Resources;
import fj.data.Either;
import org.apache.commons.lang3.StringUtils;
import org.apache.jena.ext.com.google.common.collect.ImmutableMap;
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
		OpDistribution op = new OpDistribution(false);
		op.init();
		op.apply(mQuery);
		Map<String, Integer> name2count = op.transformMap();
		System.out.println(name2count);
	}

	private static final String BASENAME = "op_distribution.yaml";
	private static final String BASENAME2 = "op_distribution_sa.yaml";
	private static final Map<Integer, String> mask2name =
		ImmutableMap.<Integer, String>builder()
			.put(0b1 << (FlagWalker.AND - 1), "AND")
			.put(0b1 << (FlagWalker.OPTIONAL - 1), "OPTIONAL")
			.put(0b1 << (FlagWalker.UNION - 1), "UNION")
			.put(0b1 << (FlagWalker.FILTER - 1), "FILTER")
			.put(0b1 << (FlagWalker.GRAPH - 1), "GRAPH")
			.put(0b1 << (FlagWalker.DATA - 1), "DATA")
			.build();
	private static int mask = mask2name.keySet().stream().reduce(0, (x, y) -> x | y);

	protected Map<Integer, Integer> bitset2count;
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
		bitset2count = new TreeMap<>();
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
				boolean onlySA = !onlySelectAsk ||
					(query.isAskType() || query.isSelectType() || query.isConstructType());
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
		if (bitset2count != null) {
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
		bitset2count = null;
	}

	private Map<String, Integer> transformMap() {
		Map<String, Integer> result = new LinkedHashMap<>();
		for (Map.Entry<Integer, Integer> entry : bitset2count.entrySet()) {
			int bitset = entry.getKey();

			if ((bitset & ~mask) == 0) {
				StringBuilder labelBuilder = new StringBuilder();
				mask2name.forEach((k, v) -> {
					if ((bitset & k) != 0) {
						labelBuilder.append(",").append(v);
					}
				});
















				String label = labelBuilder.toString();
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
		Integer val = bitset2count.get(bitset);
		if (val == null) {
			val = 0;
		}
		bitset2count.put(bitset, val + 1);
	}
}
