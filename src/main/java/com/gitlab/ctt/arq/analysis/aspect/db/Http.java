package com.gitlab.ctt.arq.analysis.aspect.db;

import com.gitlab.ctt.arq.util.SparqlUtil;
import com.gitlab.ctt.arq.utilx.Resources;
import org.apache.commons.io.IOUtils;
import org.apache.jena.ext.com.google.common.collect.ImmutableMap;
import org.rapidoid.http.MediaType;
import org.rapidoid.http.Req;
import org.rapidoid.setup.App;
import org.rapidoid.setup.On;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


public class Http {
	private static final boolean corsPermit = true;
	public static final String QUERY_STR_KEY = "queryStr";

	public static void main(String[] args) {
		httpInit(args);

		String defaultBaseUrlStr = "";    
		String baseUrlStr = System.getProperty("http.baseurl", defaultBaseUrlStr);
		On.get(baseUrlStr + "/").html(req  -> {        
			return req.response().code(302).redirect(baseUrlStr + "/index").done();
		});

		wireResources(baseUrlStr);






		DbUtil db = new DbUtil();
		db.init();

		String apiEndpoint = baseUrlStr + "/query";

		On.post(apiEndpoint).json((req) -> {
			cors(req);




			try {
				String sqlQueryStr = String.valueOf(req.data().get("sqlQueryStr"));
				String sessionId = String.valueOf(req.data().get("sessionId"));
				db.reserve(sessionId);
				List<Map<String, Object>> mapList = db.submit(sessionId, sqlQueryStr, 60);
				mapList = mapList.stream().map(m -> {
					LinkedHashMap<String, Object> m2 = new LinkedHashMap<>(m);
					Object queryStr = m2.get(QUERY_STR_KEY);
					if (queryStr != null) {
						String nStr = SparqlUtil.normalize(queryStr.toString());
						m2.put(QUERY_STR_KEY, nStr);
					}
					return m2;
				}).collect(Collectors.toList());
				return mapList;
			} catch (Exception e) {
				ByteArrayOutputStream baos = new ByteArrayOutputStream();
				try (PrintStream ps = new PrintStream(baos, true, "utf-8")) {
					e.printStackTrace(ps);
					String errStr = new String(baos.toByteArray(), StandardCharsets.UTF_8);
					ps.close();
					return ImmutableMap.builder()
						.put(QUERY_STR_KEY, errStr)
						.build();
				}
			}
		});
		On.options(apiEndpoint).plain(req -> {
			cors(req);
			return req.response().code(204).result("");
		});






		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			db.close();
			On.setup().shutdown();
		}));
	}

	private static void httpInit(String[] args) {
		App.bootstrap(args);
		if (args.length == 0) {
			String portStr = Resources.getLocalPropertyOr("http.port", "8888");
			if (portStr != null) {
				try {
					On.port(Integer.parseInt(portStr));
				} catch (NumberFormatException e) {
					On.port(8888);
				}
			}
			String addressStr = Resources.getLocalPropertyOr("http.address", "0.0.0.0");
			if (addressStr != null) {
				On.address(addressStr);
			}
			App.bootstrap(new String[] {""});
		}
	}

	private static void wireResources(String baseUrlStr) {
		for (String name : Resources.listResources("web")) {
			String urlStr = baseUrlStr + "/" + name;
			String resourceName = "web" + "/" + name;
			MediaType mediaType;
			if (name.matches(".*\\.html$")) {
				mediaType = MediaType.HTML_UTF_8;
				urlStr = urlStr.replaceAll("\\.html$", "");
			} else if (name.matches(".*\\.css$")) {
				mediaType = MediaType.CSS_UTF_8;
			} else if (name.matches(".*\\.ico$")) {
				mediaType = MediaType.create("image/x-icon");
			} else {
				mediaType = MediaType.PLAIN_TEXT_UTF_8;
			}
			final boolean isText = mediaType.toString().startsWith("text");
			On.get(urlStr).serve(req -> {
				cors(req);
				byte[] bytes = get(resourceName);
				Object returnedContent = bytes;
				if (isText) {
					returnedContent = new String(bytes, StandardCharsets.UTF_8);
				}
				return req.response().contentType(mediaType).code(200)
					.result(returnedContent);
			});
		}
	}

	private static void cors(Req req) {
		if (corsPermit) {
			req.response().headers().put("Access-Control-Allow-Origin", "*");
			req.response().headers().put("Access-Control-Allow-Credentials", "true");
			req.response().headers().put("Access-Control-Allow-Headers", String.join(",",
				"DNT",
				"X-CustomHeader",
				"Keep-Alive",
				"User-Agent",
				"X-Requested-With",
				"If-Modified-Since",
				"Cache-Control",
				"Content-Type"));
		}
	}

	private static byte[] get(String resourceName) {
		try {
			return IOUtils.toByteArray(Resources.getResourceAsStream(resourceName));
		} catch (IOException ignored) {
			return new byte[0];
		}
	}
}
