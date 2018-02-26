package com.gitlab.ctt.arq.lab.support;

import org.apache.jena.sparql.syntax.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.LinkedHashMap;
import java.util.Map;

public class OpCounter {
	private static final Logger LOGGER = LoggerFactory.getLogger(OpCounter.class);

	private Map<String, Integer> map = new LinkedHashMap<>();
	private OpVisitor opVisitor = new OpVisitor(map);
	private String filename;

	public OpCounter(String filename) {
		this.filename = filename;
	}

	public void accept(Element element) {

	}

	public void commit() {
		File file = new File(filename);
		try (FileOutputStream fos =  new FileOutputStream(file)) {
			PrintWriter writer = new PrintWriter(fos);
			for (Map.Entry<String, Integer> entry : map.entrySet()) {
				String key = entry.getKey();
				Integer value = entry.getValue();
				writer.printf("%s,%s\n", key, value);
			}
			writer.flush();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			LOGGER.warn("Unhandled", e);
		}
	}
}
