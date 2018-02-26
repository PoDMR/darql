package com.gitlab.ctt.arq.utilx;

import org.apache.commons.io.input.CountingInputStream;

import java.io.InputStream;
import java.util.function.Consumer;

public class MeteredInputStream extends CountingInputStream {
	private static final long NANOS_IN_SECOND = 1_000_000_000;
	private static final long NANO_THRESHOLD = 4 * NANOS_IN_SECOND;
	private final Consumer<Double> action;
	private long lastNanoCount;
	private long lastByteCount;
	private long deltaNanoCount;
	private long deltaByteCount;
	private long sumNanoCount;
	private long sumByteCount;

	public MeteredInputStream(InputStream in, Consumer<Double> action) {
		super(in);
		this.action = action;
		deltaNanoCount = System.nanoTime();
		deltaByteCount = 0;
	}


	public void measure() {
		long absNanoCount =  System.nanoTime();
		long absByteCount = getByteCount();
		deltaNanoCount = absNanoCount - lastNanoCount;
		deltaByteCount = absByteCount - lastByteCount;
		sumNanoCount += deltaNanoCount;
		sumByteCount += deltaByteCount;
		lastNanoCount = absNanoCount;
		lastByteCount = absByteCount;
		checkForThreshold();
	}

	private void checkForThreshold() {
		if (sumNanoCount > NANO_THRESHOLD) {
			long seconds = sumNanoCount / NANOS_IN_SECOND;
			try {
				double bytesPerSeconds = sumByteCount / seconds;
				action.accept(bytesPerSeconds);
			} catch (ArithmeticException ignored) {
			}
			resetSums();
		}
	}

	private void resetSums() {
		sumNanoCount = 0;
		sumByteCount = 0;
	}
}

