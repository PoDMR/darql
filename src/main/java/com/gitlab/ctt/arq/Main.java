package com.gitlab.ctt.arq;

import com.gitlab.ctt.arq.core.BatchProcessor;
import com.gitlab.ctt.arq.utilx.Resources;
import org.apache.commons.cli.*;
import org.apache.commons.lang3.time.DurationFormatUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

public class Main {
	private static final Logger LOGGER;

	static {
		Resources.getLocalProperty("null");  
		LOGGER = LoggerFactory.getLogger(Main.class);
	}

	private static final String OPT_LOWER = "l";


	public static void main(String[] args) {
		CommandLineParser parser = new DefaultParser();
		Options options = new Options();
		HelpFormatter formatter = new HelpFormatter();
		try {
			CommandLine commandLine = parser.parse(options, args);
			List<String> argList = Arrays.asList(commandLine.getArgs());
			coreTimed(argList);
		} catch (ParseException ignored) {
			formatter.printHelp(Main.class.getPackage().getName(), options);
		}
	}

	private static void coreTimed(List<String> filenames) {
		withTime(() -> core(filenames));
	}

	public static void withTime(Runnable r) {
		long startMillis = System.currentTimeMillis();
		LOGGER.info("Start: {}", startMillis);
		r.run();
		long endMillis = System.currentTimeMillis();
		LOGGER.info("Stop: {}", endMillis);
		long millis = endMillis - startMillis;
		String duration = DurationFormatUtils.formatDuration(millis, "H:mm:ss", true);
		LOGGER.info("Total run time: {}", duration);
	}

	private static void core(List<String> argList) {

		Thread waitThread  = Thread.currentThread();
		Thread thread = new Thread(new Runnable() {
			@Override
			public void run() {
				try {



					String mainClassName = System.getenv("MAIN_CLASS");
					Class<?> type = mainClassName != null ?
						Class.forName(mainClassName) :
						BatchProcessor.class;
					Method method = type.getMethod("main", String[].class);
					String[] args = argList.toArray(new String[argList.size()]);
					method.invoke(null, (Object) args);
					waitThread.interrupt();
				} catch (Throwable e) {
					LOGGER.error("Unhandled", e);
				}
			}
		});
		thread.setName("proc");

		thread.start();
		if (waitForIt()) {
			thread.interrupt();
		}
		try {
			thread.join();
		} catch (InterruptedException ignored) {

		}
	}

	private static boolean waitForIt() {
		try {
			BufferedReader br = new BufferedReader(
				new InputStreamReader(System.in));

			while (!br.ready()) {  
				Thread.sleep(2000);  
			}
			br.readLine();


			return true;
		} catch (IOException ignored) {

		} catch (InterruptedException ignored) {
			return false;  
		}
		return false;
	}
}
