package com.gitlab.ctt.arq.analysis;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.gitlab.ctt.arq.Main;
import com.gitlab.ctt.arq.core.FileHandler;
import com.gitlab.ctt.arq.util.LogParse;
import com.gitlab.ctt.arq.utilx.Resources;
import com.gitlab.ctt.arq.vfs.FileEntry;
import fj.data.Either;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class StreakExperiment {
	public static void main(String[] args) {
		Resources.getLocalProperty("null");  
		disableLogger(FileHandler.class.getName());
		disableLogger("STATUS");












		Main.withTime(StreakExperiment::compareWindows);
	}

	public static void disableLogger(String name) {
		org.slf4j.Logger logger = LoggerFactory.getLogger(name);
		if (logger instanceof Logger) {
			Logger logbackLogger = (Logger) logger;
			logbackLogger.setLevel(Level.OFF);
		}
	}

	private static void compareWindows() {
		int w1 = 20, w2 = 80, ws = 10;
		int t1 = 0, t2 = 100, ts = 1;
		String pathname = Resources.getLocalProperty("arq.sample.log");
		PrintWriter writer = new PrintWriter(System.out, true);
		for (int w = w1; w <= w2; w += ws) {
			writer.printf("# %s\n", w);
			writer.format("window\tthreshold\tavg\tmax\twAvg\twMax\n");
			for (int t = t1; t <= t2; t += ts) {
				double td = t / 100d;
				StreakExperiment streakProcessor = process(pathname, w, td);
				double avg = avg(streakProcessor.lenCount);
				double max = max(streakProcessor.lenCount);
				double wAvg = avg(streakProcessor.wLenCount);
				double wMax = max(streakProcessor.wLenCount);
				writer.format("%s\t%s\t%s\t%s\t%s\t%s\n", w, td, avg, max, wAvg, wMax);
			}
		}
	}


	private static void compareRange(int w, int low, int high) {
		String pathname = Resources.getLocalProperty("arq.sample.log");
		Map<Double, StreakExperiment> streaks = new LinkedHashMap<>();
		for (int percent = low; percent <= high; percent += 1) {
			double threshold = percent / 100d;
			StreakExperiment streakProcessor = process(pathname, w, threshold);
			streaks.put(threshold, streakProcessor);
		}
		PrintWriter writer = new PrintWriter(System.out, true);
		writer.format("threshold\tavg\tmax\n");
		for (Map.Entry<Double, StreakExperiment> entry : streaks.entrySet()) {
			double threshold = entry.getKey();
			StreakExperiment streakProcessor = entry.getValue();
			double max = max(streakProcessor.wLenCount);
			double avg = avg(streakProcessor.wLenCount);
			writer.format("%s\t%s\t%s\n", threshold, avg, max);
		}
	}

	private static <T extends Number> double max(Map<T, Long> lenCount) {
		return lenCount.entrySet().stream()
			.mapToDouble(e -> e.getKey().doubleValue())
			.max().orElse(0);
	}

	private static <T extends Number> double avg(Map<T, Long> lenCount) {
		double count = lenCount.entrySet().stream()
			.mapToDouble(e -> e.getValue().doubleValue())
			.sum();
		double sum = lenCount.entrySet().stream()
			.mapToDouble(e -> e.getKey().doubleValue() * e.getValue())
			.sum();
		return sum / count;
	}

	private static void writeLog(int w, double t) {
		System.out.println("---");
		System.out.println(String.format("# t=%s w=%s", t, w));
		String pathname = Resources.getLocalProperty("arq.sample.log");
		StreakExperiment streakProcessor = process(pathname, w, t);







		List<List<Object>> niceList = streakProcessor.flushedStreaks.stream()
			.map(StreakExperiment::niceList).collect(Collectors.toList());
		writeYaml(System.out, niceList);
	}

	private static void biSimulation() {
		StreakExperiment sa1 = new StreakExperiment(900, 0.78);
		StreakExperiment sa2 = new StreakExperiment(900, 0.79);
		String pathname = Resources.getLocalProperty("arq.sample.log");
		Path path = Paths.get(pathname);
		try (Stream<String> lines = Files.lines(path)) {
			AtomicInteger num = new AtomicInteger();
			lines.forEach(line -> {
				System.out.printf("line: %s\n", num.getAndIncrement());
				Either<String, String> parsedLine = LogParse.get().queryFromLogLine(line);
				if (parsedLine.isRight()) {
					sa2.accept(parsedLine.right().value(), num.get());
					sa1.accept(parsedLine.right().value(), num.get());
					if (sa1.streaks.size() > sa2.streaks.size()) {
						System.out.printf("hit on line: %s\n", num.get());
					}
				}
			});
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private static void writeYaml(OutputStream outputStream, Object data) {
		PrintWriter writer = new PrintWriter(outputStream);
		DumperOptions options = new DumperOptions();
		options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);

		Yaml yaml = new Yaml(options);

		yaml.dump(data, writer);
	}

	private static StreakExperiment process(String pathname, int w, double t) {
		StreakExperiment streakProcessor = new StreakExperiment(w, t);
		FileHandler fileHandler = FileHandler.pair((line, num) -> {
			Either<String, String> parsedLine = LogParse.get().queryFromLogLine(line);
			if (parsedLine.isRight()) {
				streakProcessor.accept(parsedLine.right().value(), num);
			}
		});
		File file = new File(pathname);
		try {
			FileEntry fileEntry = new FileEntry(null,
				"sample", file.length(), new FileInputStream(file));
			fileHandler.acceptFile(fileEntry);
			streakProcessor.flushRest();
			return streakProcessor;
		} catch (FileNotFoundException e) {
			throw new RuntimeException(e);
		}
	}

	public static double compare(String str1, String str2) {
		int distance = StringUtils.getLevenshteinDistance(str1, str2);
		int maxLen = Math.max(str1.length(), str2.length());
		return (maxLen - distance) * 1.d / maxLen;
	}

	public static double compareWithTrim(String str1, String str2) {
		String trimStr1 = PREFIX_PATTERN.matcher(str1).replaceAll("");
		String trimStr2 = PREFIX_PATTERN.matcher(str2).replaceAll("");
		return compare(trimStr1, trimStr2);
	}

	public static final Pattern PREFIX_PATTERN = Pattern.compile("(PREFIX\\s[^>]+>\\s+)+");
	private int bufferSize = 20;
	private double threshold = 0.9d;
	private LinkedList<StreakItem> items = new LinkedList<>();
	private LinkedList<Integer> sizes = new LinkedList<>();
	private LinkedList<List<StreakItem>> streaks = new LinkedList<>();
	private List<List<StreakItem>> flushedStreaks = new LinkedList<>();
	private Map<Long, Long> lenCount = new TreeMap<>();
	private Map<Double, Long> wLenCount = new TreeMap<>();
	private IdentityHashMap<Object, Integer> streakIds = new IdentityHashMap<>();
	private int accepted = 0;
	private int totalHits = 0;
	private int multiHits = 0;

	public StreakExperiment(int bufferSize, double threshold) {
		this.bufferSize = bufferSize;
		this.threshold = threshold;
	}

	public static class StreakItem {
		public String string;
		public double weight;

		public StreakItem(String string, double weight) {
			this.string = string;
			this.weight = weight;
		}

		@Override
		public String toString() {
			return string;
		}
	}

	public void accept(String string, long id) {
		String trimString = PREFIX_PATTERN.matcher(string).replaceAll("");
		accepted++;
		List<Integer> matches = new ArrayList<>();
		for (int i = 0; i < items.size(); i++) {
			StreakItem other = items.get(i);
			double score = compare(trimString, other.string);
			if (score >= threshold) {
				matches.add(i);
			}
		}
		int hits = matches.size();
		for (Integer i : matches) {
			List<StreakItem> currentStreak = streaks.get(i);
			int csSize = currentStreak.size();

			double w0 = 1.0d;
			double w1 = w0 / hits;
			currentStreak.add(new StreakItem(string, w1));
			items.set(i, new StreakItem(trimString, w1));
			sizes.set(i, sizes.get(i) + 1);
		}

		if (hits <= 0) {
			while (items.size() >= bufferSize) {
				flushFirst();
			}
			LinkedList<StreakItem> streak = new LinkedList<>();
			streakIds.put(streak, streakIds.size());
			streaks.add(streak);
			streak.add(new StreakItem(string, 1.0d));
			items.add(new StreakItem(trimString, 1.0d));
			sizes.add(1);
		}
		if (hits > 1) {
			multiHits += hits - 1;
		}
		totalHits += hits;
	}

	public void flushRest() {
		while (!sizes.isEmpty()) {
			flushFirst();
		}
	}

	private void flushFirst() {
		StreakItem s = items.removeFirst();
		List<StreakItem> streak = streaks.removeFirst();
		flushedStreaks.add(streak);  

		long streakSize = sizes.removeFirst();
		Long valBoxed = lenCount.get(streakSize);
		long val = valBoxed == null ? 0 : (long)valBoxed;
		lenCount.put(streakSize, val + 1);

		double streakLen = streak.stream().mapToDouble(e -> e.weight).sum();
		Long wValBoxed = wLenCount.get(streakLen);
		long wVal = wValBoxed == null ? 0 : (long)wValBoxed;
		wLenCount.put(streakLen, wVal + 1);
	}

	private static List<Object> niceList(List<StreakItem> streak) {
		List<Object> niceList = new ArrayList<>();
		double wSize = streak.stream().mapToDouble(e -> e.weight).sum();
		niceList.add(wSize);
		niceList.add(streak.size());
		streak.stream().findFirst().ifPresent(s -> niceList.add(s.toString()));
		for (int i = 0; i < streak.size() - 1; i++) {
			StreakItem s1 = streak.get(i);
			StreakItem s2 = streak.get(i + 1);
			double d = compareWithTrim(s1.toString(), s2.toString());
			niceList.add(d);
			niceList.add(s2.toString());
		}
		return niceList;
	}
}
