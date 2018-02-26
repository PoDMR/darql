package com.gitlab.ctt.arq.core;

import com.gitlab.ctt.arq.core.format.LineItem;
import com.gitlab.ctt.arq.utilx.MeteredInputStream;
import com.gitlab.ctt.arq.vfs.FileEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class FileHandler {
	private static final Logger LOGGER = LoggerFactory.getLogger(FileHandler.class);
	private static final Logger STATUS_LOGGER = LoggerFactory.getLogger("STATUS");
	private static final int LOG_THRESHOLD = 100_000;

	private long countFile = 0;
	private long countLines = 0;
	private long countBytes = 0;

	private Consumer<LineItem> lineHandler;


	private FileHandler(Consumer<LineItem> lineItem) {
		this.lineHandler = lineItem;
	}

	public static FileHandler single(Consumer<String> lineHandler) {
		return new FileHandler(lineItem -> lineHandler.accept(lineItem.lineStr));
	}

	public static FileHandler pair(BiConsumer<String, Long> lineHandler) {
		return new FileHandler(lineItem -> lineHandler.accept(lineItem.lineStr, lineItem.num));
	}

	public static FileHandler record(Consumer<LineItem> lineItem) {
		return new FileHandler(lineItem);
	}

	public void acceptFile(FileEntry file) {
		String name = file.getName();

		STATUS_LOGGER.info("begin: num={}, file={}", ++countFile, name);
		MeteredInputStream cis = new MeteredInputStream(file.getStream(), this::logRate);

		try (InputStreamReader isr = new InputStreamReader(cis,
				StandardCharsets.UTF_8)) {
			try (BufferedReader br = new BufferedReader(isr)) {
				String line = br.readLine();
				long ti = 0;
				while (line != null) {
					cis.measure();
					if (Thread.currentThread().isInterrupted()) {
						LOGGER.info("Interrupted");
						throw new BatchProcessor.BailException(file, ti);
					}
					if ((ti + 1) % LOG_THRESHOLD == 0) {

						STATUS_LOGGER.info("{}E5 lines", (ti + 1) / LOG_THRESHOLD);
					}
					lineHandler.accept(new LineItem(line, ti, file.getName()));
					ti++;
					line = br.readLine();
				}

				logEnd(cis, ti);
			}
		} catch (IOException e) {
			LOGGER.warn("Unhandled", e);
		}
	}

	private void logEnd(MeteredInputStream cis, long ti) {
		long byteCount = cis.getByteCount();
		STATUS_LOGGER.info("end: lines={}, bytes={}", ti, byteCount);
		countLines += ti;
		countBytes += byteCount;
		STATUS_LOGGER.debug("sums: lines={}, bytes={}", countLines, countBytes);
	}

	private void logRate(double bytesPerSecond) {
		String bytesPerSecondStr = String.format(Locale.US, "%,.2f", bytesPerSecond);
		LOGGER.debug("{} b/s}", bytesPerSecondStr);
	}
}
