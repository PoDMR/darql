@Grapes([
	@Grab(group='org.apache.poi', module='poi', version='3.15'),
	@Grab(group='org.yaml', module='snakeyaml', version='1.17'),
	@Grab(group='org.apache.poi', module='poi-ooxml', version='3.15')
])
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.stream.*;
import java.util.function.Function;
import java.util.function.Supplier
import java.util.regex.Matcher
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/*
groups dataSetN/tableN.yaml
usage:
	echo /data/khan/arq/output/batch/170307170234/* | xargs -n 1 cp scripts/cat.yaml
	./scripts/XLSX.groovy ./data.xlsx /data/khan/arq/output/batch/170307170234/*
	serve_here
	lt -s khatun -p 8000

	list=($(ls -1 /data/khan/arq/output/batch))
	last=${list[$((${#list[@]}-1))]}
	./scripts/XLSX.groovy - ./out/sa.xlsx /data/khan/arq/output/batch/${last}/*
 */
public class XLSX {
	public static final String RE_NUM_SUFFIX = "(.+)_([0-9.]+)"

	public static void main(String[] args) throws Exception {
		// other idea: first 3 arguments by using a single yaml input
		String orderFileName = args[0];
		String mapFileName = args[1];
		String outFileName = args[2];
		List<Path> paths = new ArrayList<>();
		for (String dir : Arrays.asList(args).subList(3, args.length)) {
			String pattern = "*.yaml"; // File.pathSeparator
			PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + pattern);
			Files.walkFileTree(Paths.get(dir), new SimpleFileVisitor<Path>() {
				@Override
				public FileVisitResult visitFile(Path file,
						BasicFileAttributes attrs) throws IOException {
					Path relPath = Paths.get(dir).relativize(file);
					if (matcher.matches(relPath)) {
						paths.add(file);
					}
//					System.out.println(file);
					return super.visitFile(file, attrs);
				}
			});
		}
		Map<String, Map<String, Map<?, ?>>> tables = new LinkedHashMap<>();
		for (Path path : paths) {
			Map<?, ?> map = loadYaml(path, new LinkedHashMap<Object, Object>());
			String dataKey = path.getFileName().toString()
				.replaceAll("\\.yaml" + '$', "");
			String colKey = path.getParent().getFileName().toString();
			Map<String, Map<?, ?>> table = tables.computeIfAbsent(dataKey,
				new Function<String, Map<String, Map<?, ?>>>() {
					@Override
					public Map<String, Map<?, ?>> apply(String v) {
						return new LinkedHashMap<>();
					}
				}
			);
			table.put(colKey, map);
		}
		Map<?, ?> prop2cat = loadYaml(Paths.get(mapFileName), new LinkedHashMap<Object, Object>());
		List<String> order = loadYaml(Paths.get(orderFileName), new ArrayList<String>());
		write(tables, outFileName, prop2cat, order);
	}

	private static <T> T loadYaml(Path path, T defaultVal) throws Exception {
		try {
			Yaml yaml = new Yaml();
			Reader reader = new FileReader(path.toFile());
			Object data = StreamSupport.stream(
				yaml.loadAll(reader).spliterator(), false).findFirst().get();
			reader.close();
			if (data instanceof T) {
				return (T) data;
			}
		} catch (Exception ignored) {
		}
		return defaultVal;
	}

	private static void write(Map<String, Map<String, Map<?, ?>>> tables,
			String fileName, Map<?, ?> prop2cat, List<String> order) throws Exception {
		File file = new File(fileName);
//		FileInputStream fis = new FileInputStream(file);
		XSSFWorkbook workbook = new XSSFWorkbook();

		for (Map.Entry<String, Map<String, Map<?, ?>>> tableEntry : tables.entrySet()) {
			XSSFSheet sheet = workbook.createSheet(tableEntry.getKey());
			Map<String, Map<?, ?>> table = tableEntry.getValue();

			Set<Object> keys = table.values().stream()
					.flatMap(new Function<Map<?, ?>, Stream<?>>() {
				@Override
				public Stream<?> apply(Map<?, ?> map) {
					return map.keySet().stream();
				}
			}).collect(Collectors.toCollection(new Supplier<LinkedHashSet<Object>>() {
				@Override
				public LinkedHashSet<Object> get() {
					return new LinkedHashSet<Object>();
				}
			}));
			List<String> names = new ArrayList<>(table.keySet());
//			Set<Object> sKeys = new TreeSet<>(new MyComparator<>(new ArrayList<>(keys)));
//			sKeys.addAll(keys);
			if (order.size() >= names.size()) {
				Collections.sort(names, new MyComparator<>(order));
			}

			Set<Object> sKeys = new LinkedHashSet<>();
			Set<Object> nKeys = new LinkedHashSet<>();  // keys with num suffix
			for (Object key : keys) {
				if (!String.valueOf(key).matches(RE_NUM_SUFFIX)) {
					sKeys.add(key);
				} else {
					nKeys.add(key);
				}
			}

			remapCategorized(sKeys, prop2cat, workbook, names, table);
			writeSheet(sheet, names, sKeys, table);
			counterSheetDemux(nKeys, workbook, names, table);
		}

		FileOutputStream fos = new FileOutputStream(file);
		workbook.write(fos);
		fos.close();
	}

	// move some keys to different new named sheets
	private static void remapCategorized(Set<Object> oKeys, Map<?, ?> prop2cat,
			XSSFWorkbook workbook, ArrayList<String> names, Map<String, Map<?, ?>> table) {
		Map<Object, Set<Object>> cat2prop = new LinkedHashMap<>();
		for (Map.Entry<?, ?> e : prop2cat.entrySet()) {
			Object prop = e.getKey();
			Object cat = e.getValue();
			cat2prop.computeIfAbsent(cat, new Function<Object, Set<Object>>() {
				@Override
				Set<Object> apply(Object o) {
					return new LinkedHashSet<Object>();
				}
			}).add(prop);
		}
		for (Map.Entry<Object, Set<Object>> e : cat2prop.entrySet()) {
			Object cat = e.getKey();
			Set<Object> cKeys = e.getValue();
			Set<Object> uKeys = new LinkedHashSet<>();
			for (Object key : oKeys) {
				if (cKeys.contains(key)) {
					uKeys.add(key);
				}
			}
			if (uKeys.size() > 0) {
				XSSFSheet sheet = workbook.createSheet(String.valueOf(cat));
				writeSheet(sheet, names, uKeys, table);
				oKeys.removeAll(uKeys);
			}
		}
	}

	private static void writeSheet(XSSFSheet sheet, List<String> names,
			Set<Object> keys, Map<String, Map<?, ?>> table) {
		XSSFRow row0 = sheet.createRow(0);
		int columnCount = 1;
		for (String name : names) {
			XSSFCell cell = row0.createCell(columnCount++);
			cell.setCellValue(name);
		}

		int rowCount = 1;
		for (Object key : keys) {
			XSSFRow row = sheet.createRow(rowCount++);
			String prop = String.valueOf(key);
			// print number properties without prefix
			Matcher m = Pattern.compile(RE_NUM_SUFFIX).matcher(prop);
			prop = m.matches() ? m.group(2) : prop;
			if (m.matches()) {
				row.createCell(0).setCellValue(Double.valueOf(prop));
			} else {
				row.createCell(0).setCellValue(prop);
			}
			columnCount = 1;
			for (String name : names) {
				XSSFCell cell = row.createCell(columnCount++);
				Object value = table.get(name).getOrDefault(key, null);
				if (value instanceof Number) {
					Number castVal = (Number) value;
					cell.setCellValue(castVal.doubleValue());
				} else if (value instanceof String) {
					String castVal = (String) value;
					cell.setCellValue(castVal);
				}
			}
		}
	}

	// move properties with num suffix to own sheet
	private static counterSheetDemux(Set<Object> nKeys, XSSFWorkbook workbook,
			ArrayList<String> names, Map<String, Map<?, ?>> table) {
		Map<String, Set<Object>> prop2keys = new LinkedHashMap<>();
		for (Object key : nKeys) {
			Matcher m = Pattern.compile(RE_NUM_SUFFIX).matcher(String.valueOf(key));
			m.matches();
			prop2keys.computeIfAbsent(m.group(1), new Function<String, Set<Object>>() {
				@Override
				Set<Object> apply(String s) {
					return new TreeSet<Object>(new MyComparator2<>());
				}
			}).add(key);
		}

		Set<Object> sheetNames = new TreeSet<String>(prop2keys.keySet());
		for (String property : sheetNames) {
			XSSFSheet sheet = workbook.createSheet(property);
			Set<Object> keys = prop2keys.get(property);
			writeSheet(sheet, names, keys, table);
		}
	}

	private static class MyComparator<T> implements Comparator<T> {
		private final List<T> l;

		public MyComparator(List<T> l) {
			this.l = l;
		}

		@Override
		public int compare(T o1, T o2) {
//			boolean m1 = String.valueOf(o1).matches(RE_NUM_SUFFIX);
//			boolean m2 = String.valueOf(o2).matches(RE_NUM_SUFFIX);
//			if (!m1 && m2) {
//				return -1;
//			} else if (m1 && !m2) {
//				return 1;
//			}
			int i1 = l.indexOf(o1);
			int i2 = l.indexOf(o2);
//			return Integer.compare(i1, i2);
			return i1 >= 0 && i2 >= 0 ? Integer.compare(i1, i2) : -1;
		}
	}

	private static class MyComparator2<T> implements Comparator<T> {
		@Override
		public int compare(T o1, T o2) {
			Matcher m1 = Pattern.compile(RE_NUM_SUFFIX).matcher(String.valueOf(o1));
			m1.matches();
			Matcher m2 = Pattern.compile(RE_NUM_SUFFIX).matcher(String.valueOf(o2));
			m2.matches();
			double d1 = Double.parseDouble(m1.group(2));
			double d2 = Double.parseDouble(m2.group(2));
			return Double.compare(d1, d2);
		}
	}
}
