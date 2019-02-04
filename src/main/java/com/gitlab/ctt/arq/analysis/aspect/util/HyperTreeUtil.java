package com.gitlab.ctt.arq.analysis.aspect.util;

import com.gitlab.ctt.arq.sparql.SparqlGraph;
import com.gitlab.ctt.arq.util.GraphShape;
import com.gitlab.ctt.arq.util.SparqlUtil;
import com.gitlab.ctt.arq.utilx.Resources;
import fj.data.Either;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.jena.query.Query;
import org.apache.jena.sparql.syntax.Element;
import org.jgrapht.DirectedGraph;
import org.jgrapht.ext.GmlImporter;
import org.jgrapht.ext.ImportException;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.DirectedPseudograph;
import org.rapidoid.util.NullOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

public class HyperTreeUtil {
	public static final int HYPER_TREE_ERROR = -1;

	public static void main(String[] args) {
		String sparqlStr = Resources.getResourceAsString("sample/misc/scrap.sparql");
		Either<Exception, Query> mQuery = SparqlUtil.get().toQuery(sparqlStr);
		Query query = mQuery.right().value();
		StringWriter sw1 = new StringWriter();
		StringWriter sw2 = new StringWriter();
		PrintWriter pw1 = new PrintWriter(sw1);
		PrintWriter pw2 = new PrintWriter(sw2);
		toDatalog(query, pw1, pw2, false, true);
		Element element = query.getQueryPattern();
		DirectedGraph<Object, DefaultEdge> graph = SparqlGraph.graphFromQuery(element);
		System.out.println(GraphShape.hasHiddenCycle(element, graph));
		System.out.println("----");
		System.out.println(sw2);
		System.out.println("----");
		System.out.println(sw1);
		System.out.println("----");
		System.out.println("htw: " + hyperTreeWidth(query, false, true));
		System.out.println("tw: " + hyperTreeWidth(query, true, false));
	}


	private static final Logger LOGGER = LoggerFactory.getLogger(HyperTreeUtil.class);
	private static final int LIMIT = 8;
	private static final File MARKER = new File(".");

	@SafeVarargs
	public static Pair<Integer, Integer> hyperTreeCheck(Query query, List<String>... hes) {
		for (int i = 1; i <= LIMIT; i++) {
			int nodeCount = hyperTreeNodeCount(query, i, false, true, hes);
			if (nodeCount != HYPER_TREE_ERROR) {
				return Pair.of(i, nodeCount);
			}
		}
		return Pair.of(0, 0);
	}

	@SafeVarargs
	public static int hyperTreeWidth(Query query,
			boolean skipPredicates, boolean skipConstants, List<String>... hes) {
		int errCode = 0;
		for (int i = 1; i <= LIMIT; i++) {
			if (detkdecomp(query, i, skipPredicates, skipConstants, Objects::nonNull, hes)) {
				return i;
			}
		}
		return errCode;
	}

	private static AtomicLong counter = new AtomicLong();

	
	@SafeVarargs
	private static <T> T detkdecomp(Query query, int k,
			boolean skipPredicates, boolean skipConstants, Function<File, T> f,
			List<String>... hes) {
		T result = f.apply(null);
		try {
			long id = counter.getAndIncrement(); 
			String basename = String.valueOf(id);
			String dtlFileName = basename + ".dtl";
			String metaFileName = basename + ".yaml";
			String gmlFileName = basename + ".gml";
			File dir = new File(System.getProperty("java.io.tmpdir"), "arq");
			if (!dir.mkdirs()) {
				LOGGER.trace("Temp dir not created: {}", dir);
			}
			File dtlFile = new File(dir, dtlFileName);
			File ymlFile = new File(dir, metaFileName);
			FileOutputStream fos1 = new FileOutputStream(dtlFile);
			FileOutputStream fos2 = new FileOutputStream(ymlFile);
			PrintWriter pw1 = new PrintWriter(
				new OutputStreamWriter(fos1, StandardCharsets.UTF_8));
			PrintWriter pw2 = new PrintWriter(
				new OutputStreamWriter(fos2, StandardCharsets.UTF_8));
			int n = toDatalog(query, pw1, pw2, skipPredicates, skipConstants, hes);
			pw1.close();
			pw2.close();
			if (n > 0) {
				ProcessBuilder pb = new ProcessBuilder(
					"detkdecomp", String.valueOf(k), dtlFileName);
				pb.directory(dir);
				Process process = pb.start();
				InputStream inputStream = process.getInputStream();
				InputStream errorStream = process.getErrorStream();
				IOUtils.copy(inputStream, new NullOutputStream());
				IOUtils.copy(errorStream, new NullOutputStream());
				inputStream.close();
				errorStream.close();
				process.getOutputStream().close();
				process.waitFor();
				File gml = new File(dir, gmlFileName);
				if (gml.exists()) {
					result = f.apply(gml);
					if (gml.delete()) {
						LOGGER.trace("Not deleted: {}", gml);
					}
				}
			} else {
				result = f.apply(MARKER);
			}
			if (dtlFile.delete()) {
				LOGGER.trace("Not deleted: {}", dtlFile);
			}
			if (ymlFile.delete()) {
				LOGGER.trace("Not deleted: {}", ymlFile);
			}
		} catch (IOException | InterruptedException e) {
			LOGGER.error("Decomposition exception", e);
		}
		return result;
	}











	@SafeVarargs
	private static int hyperTreeNodeCount(Query query, int k,
			boolean skipPredicates, boolean skipConstants,
			List<String>... hes) {
		return detkdecomp(query, k, skipPredicates, skipConstants, file -> {
			if (file != null) {
				if (MARKER.getName().equals(file.getName())) {
					return 0;
				}
				GmlImporter<String, String> gmlImporter = new GmlImporter<>(
					(label, attributes) -> label,
					(from, to, label, attributes) -> label
				);
				DirectedGraph<String, String> graph = new DirectedPseudograph<>(String.class);
				try {
					gmlImporter.importGraph(graph, file);
					return graph.vertexSet().size();
				} catch (ImportException e) {
					LOGGER.error("GML import exception", e);
				}
			}
			return HYPER_TREE_ERROR;
		}, hes);
	}

	@SafeVarargs
	private static int toDatalog(Query query,
			PrintWriter writer1, PrintWriter writer2,
			boolean skipPredicates, boolean skipConstants,
			List<String>... hes) {
		Element element = query.getQueryPattern();
		Element2Triples e2t = new Element2Triples(element, skipPredicates, skipConstants);
		for (List<String> he : hes) {
			e2t.addTriple(he);
		}
		return toDatalog(e2t, writer1, writer2);
	}

	private static int toDatalog(Element2Triples e2t,
			PrintWriter writer1, PrintWriter writer2) {
		if (e2t.triples.size() == 0) {
			return 0;
		}

		DumperOptions options = new DumperOptions();
		options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
		Yaml yaml = new Yaml(options);
		String yamlStr = yaml.dump(e2t.rMap);
		writer2.println(yamlStr);
		writer2.flush();

		boolean touchedList = false;
		int heCount = 0;
		StringBuilder sb = new StringBuilder();
		for (List<String> triple : e2t.triples) {



			if (touchedList) {
				sb.append(",\n");
			}
			touchedList = true;
			sb.append("HE");
			sb.append(heCount++);
			sb.append("(");
			boolean touchedItem = false;
			for (String str : triple) {
				if (touchedItem) {
					sb.append(",");
				}
				touchedItem = true;
				int num = e2t.map.getOrDefault(str, 0);
				sb.append(num);
			}
			sb.append(")");
		}
		sb.append(".");
		writer1.println(sb.toString());
		writer1.flush();
		return e2t.triples.size();
	}
}
