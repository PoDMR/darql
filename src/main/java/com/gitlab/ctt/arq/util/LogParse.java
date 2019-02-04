package com.gitlab.ctt.arq.util;

import fj.data.Either;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LogParse {
	private static final String TEST_LINE = "ip - - [28/Sep/2014 00:00:00 +0200] \"GET /sparql?query=SELECT+%3Fabstract+WHERE+%7B+%3Fs+rdfs%3Alabel+%27Completing%27%40en+.%0A%3Fs+dbpedia-owl%3Aabstract+%3Fabstract+.%0AFILTER+langMatches%28+lang%28%3Fabstract%29%2C+%27en%27%29%7D+LIMIT+1000&default-graph-uri=http://dbpedia.org&format=JSON HTTP/1.0\" 200 119 \"\" \"Java/1.6.0_51\" ";
	private static final Pattern PATTERN = Pattern.compile("[^\"]*\"(?:GET )?/sparql/?\\?([^\"\\s]*)[^\"]*\".*");


	public static void main(String[] args) {
		try {
			System.in.read();
		} catch (IOException e) {
			e.printStackTrace();
		}
		String requestStr = new LogParse().queryFromLogLine(TEST_LINE).right().value();
		System.out.println("---");
		System.out.println(requestStr);
		System.out.println("---");
	}

	private static final LogParse INSTANCE = new LogParse();

	public static LogParse get() {
		return INSTANCE;
	}

	public Either<String, String> queryFromLogLine(String line) {









		Matcher matcher = PATTERN.matcher(line);
		if (matcher.find()) {

			String requestStr = matcher.group(1);
			String queryStr = queryFromRequest(requestStr);
			return queryStr != null ? Either.right(queryStr) : Either.left(requestStr);
		} else {
			return Either.left(line);
		}
	}

	public String queryFromRequest(String requestStr) {
		List<NameValuePair> pairs = URLEncodedUtils.parse(requestStr,
			StandardCharsets.UTF_8);
		for (NameValuePair pair : pairs) {
			if ("query".equals(pair.getName())) {
				return pair.getValue();
			}
		}
		return null;
	}
}
