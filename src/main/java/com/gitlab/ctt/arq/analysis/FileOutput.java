package com.gitlab.ctt.arq.analysis;

import com.gitlab.ctt.arq.utilx.Resources;
import org.apache.commons.io.output.NullWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

public class FileOutput {
	private static final Logger LOGGER = LoggerFactory.getLogger(FileOutput.class);

	public static FileOutput from(String tag, String baseName) {
		String outputDirName = Resources.getLocalProperty("arq.out.dir");
		File parent = tag != null ? new File(outputDirName, tag) : new File(outputDirName);
		File file = new File(parent, baseName);
		FileOutput fileOutput = new FileOutput(file.getPath());
		fileOutput.init();
		return fileOutput;
	}

	private File file;
	private FileOutputStream fos = null;

	public PrintWriter getWriter() {
		return writer;
	}

	private PrintWriter writer = new PrintWriter(new NullWriter());

	public FileOutput(String filename) {
		file = new File(filename);
	}

	public void init() {
		try {

			boolean mkdirs = file.getParentFile().mkdirs();
			if (!mkdirs) {
				LOGGER.warn("Could not create dir: {}", file.getParentFile());
			}
			fos = new FileOutputStream(file);
			OutputStreamWriter osw = new OutputStreamWriter(fos, StandardCharsets.UTF_8);
			writer = new PrintWriter(osw, true);  
		} catch (FileNotFoundException e) {
			LOGGER.warn("Unhandled", e);
		}
	}

	public void commit() {
		writer.flush();
		try {
			if (fos != null) {
				fos.close();
				fos = null;
			}
		} catch (IOException e) {
			LOGGER.warn("Unhandled", e);
		}
	}

	public static void withWriter(File file, Consumer<PrintWriter> consumer) {
		FileOutput fileOutput = new FileOutput(file.getPath());
		fileOutput.init();
		PrintWriter writer = fileOutput.getWriter();
		consumer.accept(writer);
		fileOutput.commit();  
	}
}
