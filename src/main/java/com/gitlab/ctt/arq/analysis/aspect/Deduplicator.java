package com.gitlab.ctt.arq.analysis.aspect;

import com.gitlab.ctt.arq.analysis.FileOutput;
import com.gitlab.ctt.arq.analysis.aspect.util.BaseDeduplicator;
import com.gitlab.ctt.arq.core.format.LineDelimFormat;
import com.gitlab.ctt.arq.utilx.Resources;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.PrintWriter;

public class Deduplicator extends BaseDeduplicator {
	private static final Logger LOGGER = LoggerFactory.getLogger(Deduplicator.class);

	private String filename;
	private FileOutput uniqQueryFile;
	private PrintWriter uniqQueryWriter;

	public Deduplicator() {
		this(Resources.getLocalProperty("arq.uniq_sparql_file"));
	}

	public Deduplicator(String filename) {
		this.filename = filename;

	}

	@Override
	public void init() {

		uniqQueryFile = new FileOutput(filename);
		super.initConcurrency();
		uniqQueryFile.init();
		uniqQueryWriter = uniqQueryFile.getWriter();

		uniqQueryWriter = new PrintWriter(new BufferedWriter(uniqQueryWriter, 33_554_432)); 
	}



	private  void logQuery(String queryStr, int num) {
		uniqQueryWriter.print(LineDelimFormat.HASH_DELIM);
		uniqQueryWriter.print(" ");
		uniqQueryWriter.print(num);
		uniqQueryWriter.print("\n");

		uniqQueryWriter.print(queryStr);  

		uniqQueryWriter.print(LineDelimFormat.HASH_DELIM);
		uniqQueryWriter.print("\n");
		uniqQueryWriter.flush();  
	}

	@Override
	public void commit() {
		super.commit();
		if (uniqQueryFile != null) {
			LOGGER.debug("Query collection done");
			logStatus();

			writeCompleteQueryFile();
			uniqQueryFile.commit();
			query2num.clear();
		}
		uniqQueryFile = null;
	}

	private void writeCompleteQueryFile() {
		LOGGER.debug("Start: Complete query file write");
		int i = 0;
		for (String queryStr : query2num.keySet()) {
			logQuery(queryStr, ++i);
		}
		LOGGER.debug("End: Complete query file write");
	}
}
