package com.gitlab.ctt.arq.analysis.aspect.util;

import com.gitlab.ctt.arq.util.SparqlUtil;
import com.gitlab.ctt.arq.utilx.Resources;
import fj.data.Either;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.jena.query.Query;
import org.apache.jena.sparql.syntax.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;


public class HtdTool {
	public static void main(String[] args) {
		String sparqlStr = Resources.getResourceAsString("sample/misc/scrap.sparql");
		Either<Exception, Query> mQuery = SparqlUtil.get().toQuery(sparqlStr);
		Query query = mQuery.right().value();
		toHtdInputLp(query, new PrintWriter(System.out), false, true);
		Pair<Integer, Integer> pair = HtdTool.hyperTreeCheck(query, false, true);
		System.out.println(pair.getLeft() + ", " + pair.getRight());
	}

	private static final Logger LOGGER = LoggerFactory.getLogger(HtdTool.class);

	@SafeVarargs
	public static Pair<Integer, Integer> hyperTreeCheck(Query query, List<String>... hes) {
		return hyperTreeCheck(query, false, true, hes);
	}

	@SafeVarargs
	public static int hyperTreeWidth(Query query,
			boolean skipPredicates,
			boolean skipConstants,
			List<String>... hes) {
		return hyperTreeCheck(query, skipPredicates, skipConstants, hes).getLeft();
	}

	@SafeVarargs
	public static Pair<Integer, Integer> hyperTreeCheck(Query query,
			boolean skipPredicates,
			boolean skipConstants,
			List<String>... hes) {
		try {
			ProcessBuilder pb = new ProcessBuilder(
				"htd_main", "--input", "lp", "--type", "hypertree", "--output", "width");
			Process process = pb.start();
			InputStream inputStream = process.getInputStream();

			OutputStream outputStream = process.getOutputStream();
			toHtdInputLp(query, new PrintWriter(outputStream), skipPredicates, skipConstants, hes);
			outputStream.close();

			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			IOUtils.copy(inputStream, bos);
			inputStream.close();
			process.waitFor();
			String str = new String(bos.toByteArray(), StandardCharsets.UTF_8);
			Matcher m = Pattern.compile("^([0-9]+), ([0-9]+)").matcher(str);
			if (m.find()) {
				return Pair.of(
					Integer.parseInt(m.group(1)) - 1,
					Integer.parseInt(m.group(2)));
			}
		} catch (IOException | InterruptedException e) {
			LOGGER.error("Decomposition exception", e);
		}
		return Pair.of(0, 0);
	}

	@SafeVarargs
	private static void toHtdInputLp(Query query,
			PrintWriter writer,
			boolean skipPredicates,
			boolean skipConstants,
			List<String>... hes) {
		Element element = query.getQueryPattern();
		Element2Triples e2t = new Element2Triples(element, skipPredicates, skipConstants);
		for (List<String> he : hes) {
			e2t.addTriple(he);
		}

		for (List<String> triple : e2t.triples) {
			writer.println("edge(" + String.join(",", triple.stream().map(
				t ->String.valueOf(e2t.map.get(t))
			).collect(Collectors.toList())) + ").");
		}
		writer.flush();
	}
}
