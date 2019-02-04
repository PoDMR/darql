package com.gitlab.ctt.arq.analysis.aspect.util;

import com.gitlab.ctt.arq.util.SparqlUtil;
import com.gitlab.ctt.arq.utilx.Resources;
import fj.data.Either;
import org.apache.commons.io.IOUtils;
import org.apache.jena.query.Query;
import org.apache.jena.sparql.syntax.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class JdrasilTWTool {
	public static void main(String[] args) throws IOException {
		String sparqlStr = args.length > 0
			? new String(Files.readAllBytes(Paths.get(args[0])), StandardCharsets.UTF_8)
			: Resources.getResourceAsString("sample/misc/h8.sparql");
		if (args.length == 0) {
			sparqlStr = fromStdin();
		}
		Either<Exception, Query> mQuery = SparqlUtil.get().toQuery(sparqlStr);


		String str = JdrasilTWTool.call(mQuery.right().value(), true, false);
		System.out.println(str);
	}

	public static String fromStdin() {
		try {
			StringBuilder sb = new StringBuilder();
			BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
			String s = br.readLine();
			while (s != null) {
				if (".".equals(s)) {
					break;
				}
				sb.append(s);
				sb.append(System.lineSeparator());
				s = br.readLine();
			}
			return sb.toString();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private static final Logger LOGGER = LoggerFactory.getLogger(JdrasilTWTool.class);

	public static int hyperTreeWidth(Query query, boolean skipPredicates, boolean skipConstants) {
		String str = call(query, skipPredicates, skipConstants);
		if (str != null) {
			Matcher m = Pattern.compile("^s td [0-9]+ ([0-9]+) [0-9]+").matcher(str);
			if (m.find()) {
				return Integer.parseInt(m.group(1)) - 1;
			}
		}
		return 0;
	}

	public static String call(Query query, boolean skipPredicates, boolean skipConstants) {
		try {
			ProcessBuilder pb = new ProcessBuilder("jdrasil.exact");
			Process process = pb.start();
			InputStream inputStream = process.getInputStream();

			OutputStream outputStream = process.getOutputStream();
			toInputGr(query, new PrintWriter(outputStream), skipPredicates, skipConstants);

			outputStream.close();

			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			IOUtils.copy(inputStream, bos);
			inputStream.close();
			int code = process.waitFor();
			if (code == 0) {
				return new String(bos.toByteArray(), StandardCharsets.UTF_8);
			}
		} catch (IOException | InterruptedException e) {
			LOGGER.error("Decomposition exception", e);
		}
		return null;
	}

	private static void toInputGr(Query query,
		PrintWriter writer,
		boolean skipPredicates,
		boolean skipConstants)
	{
		Element element = query.getQueryPattern();
		Element2Triples e2t = new Element2Triples(element, skipPredicates, skipConstants);



		int vertexCount = e2t.triples.stream()
			.flatMap(List::stream).collect(Collectors.toSet()).size();

		writer.println(String.format("p tw %s %s", vertexCount, e2t.triples.size()));

		for (List<String> triple : e2t.triples) {

			List<String> ints = triple.stream()
				.map(str -> String.valueOf(1 + e2t.map.getOrDefault(str, 0)))
				.collect(Collectors.toList());
			if (ints.size() == 2) {
				writer.println(String.join(" ", ints));
			}
		}
		writer.flush();
	}
}
