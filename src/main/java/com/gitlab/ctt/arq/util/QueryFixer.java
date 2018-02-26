package com.gitlab.ctt.arq.util;

import com.gitlab.ctt.arq.utilx.Resources;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class QueryFixer {
	private final static String TEST_QUERY_STR1 = "PREFIX a: <http://a/>\n" +
		"\n" +
		"# 12:34\n" +
		"SELECT ?x ?y WHERE {\n" +
		"?x a:a ?y.\n" +
		"?x b:b ?y.\n" +
		"?x c-x:c-x ?y.\n" +
		"}";



	public static void main(String[] args) {
		String fixedQueryStr = new QueryFixer().fix(TEST_QUERY_STR1);
		System.out.println(fixedQueryStr);
	}

	private static final QueryFixer INSTANCE = new QueryFixer();

	public static QueryFixer get() {
		return INSTANCE;
	}

	private static final Pattern PREFIX_PATTERN = Pattern.compile(

		"(PREFIX\\s+)?([A-Za-z][\\w-]*):[^/]");


	private Map<String, String> predefinedMap;

	public QueryFixer(Map<String, String> predefinedMap) {
		this.predefinedMap = predefinedMap;
	}

	public QueryFixer() {
		this(loadMap());
	}

	private static Map<String, String> loadMap() {
		try (InputStream inputStream = Resources.getResourceAsStream("ns_map.yaml")) {
			Yaml yaml = new Yaml();
			Object object = yaml.load(inputStream);
			if (object instanceof Map) {

				return (Map<String, String>) object;
			}
		} catch (IOException ignored) {
		}
		return new HashMap<>();
	}


	public String fix(String queryStr) {
		queryStr = fixSelectWithCommas(queryStr);
		Set<String> undeclared = findUndeclared(queryStr);
		if (undeclared.isEmpty()) {
			return queryStr;
		} else {
			return fixUndeclared(queryStr, undeclared);
		}
	}

	private static String fixSelectWithCommas(String queryStr) {
		Pattern ps = Pattern.compile("(?i:SELECT(?: DISTINCT)? (.*)WHERE)");
		Matcher ms = ps.matcher(queryStr);
		if (!ms.find()) {
			return queryStr;
		}
		String select = ms.group(1);
		Pattern pv = Pattern.compile("(\\?[\\w_-]+),\\s?");
		Matcher mv = pv.matcher(select);
		if (!mv.find()) {
			return queryStr;
		}
		String repl = mv.replaceAll("$1 ");
		return queryStr.replace(select, repl);
	}

	private String fixUndeclared(String queryStr, Set<String> undeclared) {
		StringBuilder sb = new StringBuilder();
		for (String prefix : undeclared) {
			String val = predefinedMap.get(prefix);
			sb.append("PREFIX ");
			sb.append(prefix);
			sb.append(": <");
			if (val != null) {
				sb.append(val);
				sb.append(">  # PREDEFINED\n");
			} else {
				sb.append("http://0.0.0.0/");
				sb.append(prefix);
				sb.append("/>  # GENERATED\n");
			}
		}
		if (sb.length() == 0) {
			return queryStr;
		} else {
			sb.append(queryStr);
			return sb.toString();
		}
	}

	private static Set<String> findUndeclared(String queryStr) {
		Matcher m = PREFIX_PATTERN.matcher(queryStr);
		Set<String> declared = new LinkedHashSet<>();
		Set<String> usedOrUndeclared = new LinkedHashSet<>();
		while (m.find()) {
			String keyword = m.group(1);
			String prefix = m.group(2);
			if (keyword != null) {
				declared.add(prefix);
			} else {
				usedOrUndeclared.add(prefix);
			}
		}


		usedOrUndeclared.removeAll(declared);  

		return usedOrUndeclared;
	}
}
