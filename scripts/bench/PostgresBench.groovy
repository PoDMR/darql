@GrabConfig(systemClassLoader=true)
@Grab("org.postgresql:postgresql:9.4.1212")
import java.sql.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;

@SuppressWarnings("SqlNoDataSourceInspection")
@SuppressWarnings("SqlDialectInspection")
class PostgresBench {
//	private static String TABLE_NAME = "edge";
	public static final int WARMUPS = 0;
	public static final int REPEATS = 1;
	public static final int TIMEOUT = 60;
	private List<String> dataSets;
	private List<String> queries;
	private static boolean doInit = true;
	private static boolean doExec = true;
	private String url;
	private String user;
	private String password;
	private Connection con;
	private Statement stmt;

	public static void main(String[] args) {
		if (args.length < 3) {
			System.err.println("DB_USER=postgres DB_PASS=postgres " +
			 "./benchmark.groovy *.tsv -q *.sql > output.txt");
		} else {
			List<String> l = new ArrayList<>(Arrays.asList(args));
			doInit = !"-i".equals(l.get(0));
//			if (!doInit)
			l.remove(0);
			doExec = !"-e".equals(l.get(0));
//			if (!doExec)
			l.remove(0);
			int pos = l.indexOf("-q");
			if (pos < 0) {
				pos = l.size();
			}
			List<String> dataSets = l.subList(0, pos);
			List<String> queries = pos + 1 <= l.size() ?
				l.subList(pos + 1, l.size()) :
				new ArrayList<>();
			new PostgresBench().process(dataSets, queries);
		}
	}

	public void process(List<String> dataSets, List<String> queries) {
		this.dataSets = dataSets;
		this.queries = queries;
		init();
		foreachDataSet();
	}

	private void init() {
		Runtime.runtime.addShutdownHook(new Thread() {
			public void run() {
				shutdown();
			}
		});
		url = System.getenv().get("DB_URL");
//		user = System.getProperty("user.name");
//		password = System.getProperty("user.password");
		user = System.getenv().get("DB_USER");
		password = System.getenv().get("DB_PASS");
		if (url == null) url = "jdbc:postgresql://localhost:5432/postgres";
		if (user == null) user = "postgres";
		if (password == null) password = "postgres";
		con = DriverManager.getConnection(url, user, password);
	}

	private void shutdown() {
		if (stmt != null) {
			stmt.cancel();
			stmt.close();
		}
		if (con != null) con.close();
	}

	private void setupDatabase() {
		stmt.executeUpdate("DROP TABLE IF EXISTS edge;");
		stmt.executeUpdate("CREATE TABLE edge (src int, label int, trg int);");
	}

	private void performIndex() {
		stmt.executeUpdate("CREATE INDEX ind1 ON edge (src);");
		stmt.executeUpdate("CREATE INDEX ind2 ON edge (trg);");
		stmt.executeUpdate("CREATE INDEX ind3 ON edge (label);");
	}

	private void foreachDataSet() {
		for (String filename : dataSets) {
			stmt = con.createStatement();
			if (doInit) {
				fillDatabase(filename);
			}
			if (doExec) {
				foreachQuery();
			}
		}
	}

	private void fillDatabase(String filename) {
		long time1 = System.nanoTime();
		setupDatabase();
		List<String> lines = Files.readAllLines(Paths.get(filename));
		long count = 0L;
		lines.forEach({it -> addDataItem(it); count++;});  // groovy syntax
//			performIndex();
		long time2 = System.nanoTime();
		long delta = time2 - time1;
		System.out.printf("insert,%s,%s,%s\n", filename, delta, count);
	}

	private void addDataItem(String line) {
		String[] a = line.split("\\s");
		if (a.length == 3) {
			String src = a[0], label = a[1], tgt = a[2];
			stmt.executeUpdate(String.format(
				"INSERT INTO edge VALUES (%s, %s, %s);", src, label, tgt));
		}
	}

	private void foreachQuery() {
		for (String filename : queries) {
			byte[] bytes = Files.readAllBytes(Paths.get(filename));
			String queryStr = new String(bytes, StandardCharsets.UTF_8);
			for (int i = 0; i < WARMUPS; i++) {
				executeQueryWithTimeout(filename, queryStr);  // warmup
			}
			long deltaSum = 0L;
			long deltaCount = 0L;
			long count = -1L;
			for (int i = 0; i < REPEATS; i++) {
				Long[] pair = executeQueryWithTimeout(filename, queryStr);
				if (pair != null) {
					Long delta = pair[0];
					count = pair[1];
					deltaSum += delta;
					deltaCount++;
				}
			}
			if (deltaCount > 0L) {
				double delta = (deltaSum * 1d) / deltaCount;
				System.out.printf("query,%s,%s,%s\n", filename, delta, count);
			}
		}
	}

	private Long[] executeQueryWithTimeout(String filename, String queryStr) {
		ExecutorService executor = Executors.newSingleThreadExecutor();
		Future<Long[]> future = executor.submit(new Callable<Long[]>() {
			@Override
			public Long[] call() throws Exception {
				return executeQuery(filename, queryStr);
			}
		});
		Long[] pair = null;
		try {
			pair = future.get(TIMEOUT, TimeUnit.SECONDS);
		} catch (TimeoutException ignored) {
			if (stmt != null) stmt.cancel();
			System.out.printf("query,%s,%s\n", filename, "TIMEOUT", 0);
//			System.out.printf("query,%s,%s\n", filename, 0);
		}
		executor.shutdownNow();
		return pair;
	}

	private Long[] executeQuery(String filename, String queryStr) {
		long time1 = System.nanoTime();
		ResultSet rs = stmt.executeQuery(queryStr);
		long time2 = System.nanoTime();
		long delta = time2 - time1;
		long count = 0;
		long val = 0;
		while (rs.next()) {
			count++;
			val = rs.getInt(1)
//			System.out.printf(",%s", rs.getInt(1));
		}
		rs.close();

		List<Long> pair = Arrays.asList(delta, val);  // or: count instead of val
		return pair.toArray(new Long[pair.size()]);
	}
}
