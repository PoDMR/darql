package com.gitlab.ctt.arq.analysis.aspect;

import com.gitlab.ctt.arq.analysis.FileOutput;
import com.gitlab.ctt.arq.analysis.Job;
import com.gitlab.ctt.arq.analysis.support.ConsumePathVisitor;
import com.gitlab.ctt.arq.core.format.LineDelimFormat;
import com.gitlab.ctt.arq.sparql.ElementDeepWalker;
import com.gitlab.ctt.arq.util.SparqlUtil;
import fj.data.Either;
import org.apache.jena.query.Query;
import org.apache.jena.sparql.path.Path;
import org.apache.jena.sparql.path.PathVisitor;
import org.apache.jena.sparql.syntax.Element;
import org.apache.jena.sparql.syntax.ElementPathBlock;
import org.apache.jena.sparql.syntax.ElementVisitorBase;

import java.io.PrintWriter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PathHunter implements Job<Either<Exception, Query>, Void> {
	public static void main(String[] args) {
		String sparqlStr = "PREFIX  Terminator_2: <http://0.0.0.0/Terminator_2/>\n" +
			"PREFIX  dcterms: <http://0.0.0.0/dcterms/>\n" +
			"PREFIX  skos: <http://www.w3.org/2004/02/skos/core#>\n" +
			"\n" +
			"SELECT  ?c (COUNT(?c) AS ?pCount)\n" +
			"WHERE\n" +
			"  { <http://dbpedia.org/resource/Terminator_2:_Judgment_Day> (dcterms:subject/skos:related)* ?c }\n" +
			"GROUP BY ?c";
		PathHunter pathHunter = new PathHunter();
		Either<Exception, Query> maybeQuery = SparqlUtil.get().toQuery(sparqlStr);
		pathHunter.init();
		pathHunter.apply(maybeQuery);
	}


	private static final String BASENAME = "prop_queries.txt";

	private final static Set<String> watchList = Collections.synchronizedSet(new HashSet<>(Arrays.asList(
		"((a/b)|(c/d))*",
		"(a/b)*",
		"(((a/b)|(c/d))|(c/e))*"
	)));

	public static String anonymize(String pathStr) {
		Pattern pattern = Pattern.compile("<[^>]+>");
		Matcher matcher = pattern.matcher(pathStr);
		LinkedHashSet<String> symbols = new LinkedHashSet<>();
		while (matcher.find()) {
			symbols.add(matcher.group());
		}
		char c = 'a';
		for (String s : symbols) {
			pathStr = pathStr.replaceAll(Pattern.quote(s), String.valueOf(c++));
		}
		return pathStr;
	}

	private String tag;
	private FileOutput fileOutput;
	private PrintWriter writer;

	@Override
	public void setTag(String tag) {
		this.tag = tag;
	}

	@Override
	public void init() {
		fileOutput = FileOutput.from(tag, BASENAME);
		writer = fileOutput.getWriter();
	}

	@Override
	public Void apply(Either<Exception, Query> maybeQuery) {
		if (maybeQuery.isRight()) {
			Query query = maybeQuery.right().value();
			Element element = query.getQueryPattern();
			if (element != null) {
				ElementDeepWalker.walk(element, new ElementVisitorBase() {
					@Override
					public void visit(ElementPathBlock el) {
						el.patternElts().forEachRemaining(triplePath -> {
							Path maybePath = triplePath.getPath();
							if (maybePath != null) {
								PathVisitor pathVisitor = new ConsumePathVisitor(path -> {
									String pathStr = path.toString();
									String anonStr = anonymize(pathStr);

									if (watchList.contains(anonStr)) {
										submit(query, anonStr);
									}
								});
								maybePath.visit(pathVisitor);
							}
						});
					}
				});
			}
		}
		return null;
	}

	private synchronized void submit(Query query, String anonStr) {
		writer.print(LineDelimFormat.HASH_DELIM);
		writer.print(" ");
		writer.print(anonStr);
		writer.print(" ");
		writer.print(watchList.contains(anonStr));
		writer.print("\n");
		writer.print(query.toString());
		writer.print(LineDelimFormat.HASH_DELIM);
		writer.print("\n");
		writer.flush();
	}

	@Override
	public void commit() {
		if (fileOutput != null) {
			fileOutput.commit();
		}
		fileOutput = null;
	}
}
