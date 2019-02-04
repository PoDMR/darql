import com.bigdata.rdf.sail.BigdataSail
@Grab(group='com.blazegraph', module='bigdata-core', version='2.1.4')
import com.bigdata.rdf.sail.BigdataSail
import com.bigdata.rdf.sail.BigdataSailRepository
import org.openrdf.model.Statement
import org.openrdf.model.Value
import org.openrdf.model.impl.StatementImpl
import org.openrdf.model.impl.URIImpl
import org.openrdf.query.*
import org.openrdf.repository.Repository
import org.openrdf.repository.RepositoryConnection
import org.openrdf.repository.RepositoryException

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths
import java.util.concurrent.*

public class BlazeGraph {
	public static final int WARMUPS = 0;
	public static final int REPEATS = 1;
	public static final int TIMEOUT = 60;

	public static void main(String[] args) throws Exception {
		if (args.length < 3) {
			System.err.println("... *.tsv -q *.sparql > output.txt");
		} else {
			List<String> l = new ArrayList<>(Arrays.asList(args));
			doInit = ! "-i".equals(l.get(0));
//			if (!doInit)
			l.remove(0);
			doExec = ! "-e".equals(l.get(0));
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
			new BlazeGraph().process(dataSets, queries);
		}
	}

	private List<String> dataSets;
	private List<String> queries;
	private static boolean doInit = true;
	private static boolean doExec = true;
	private Repository repo;
	private RepositoryConnection cxn;

	public void process(List<String> dataSets, List<String> queries) throws Exception {
		this.dataSets = dataSets;
		this.queries = queries;
		try {
			init();
			iterateDataSets();
		} catch (Exception ex) {
//			cxn.rollback();
			throw new RuntimeException(ex);
		} finally {
			if (cxn != null) cxn.close();
			if (repo != null) repo.shutDown();
		}
	}

	private void init() throws RepositoryException {
		String journalPath = new File(new File(System.getProperty("java.io.tmpdir"),
			"blazegraph"), "test.jnl").getAbsolutePath();
		Properties props = new Properties();
		props.put(BigdataSail.Options.BUFFER_MODE, "DiskRW");
		props.put(BigdataSail.Options.FILE, journalPath);
		BigdataSail sail = new BigdataSail(props);
		repo = new BigdataSailRepository(sail);
		repo.initialize();
		cxn = repo.getConnection();
	}

	private void iterateDataSets() throws Exception {
		for (String filename : dataSets) {
			if (doInit) {
				fillDatabase(filename);
			}
			if (doExec) {
				iterateQueries();
			}
		}
	}

	private void fillDatabase(String filename) {
		// DROP SILENT GRAPH <http://example.org/gmark>
		cxn.clear(new URIImpl("http://example.org/gmark"));
		long time1 = System.nanoTime();
		cxn.begin();
		List<String> lines = Files.readAllLines(Paths.get(filename));
		long count = 0L;
		for (String line : lines) {
			addDataItem(line);
			count++;
		}
		cxn.commit();
		long time2 = System.nanoTime();
		long delta = time2 - time1;
		printFields("insert", filename, String.valueOf(delta), String.valueOf(count));
	}

	private void addDataItem(String line) throws RepositoryException {
		String[] a = line.split("\\s");
		if (a.length == 3) {
			String src = a[0], label = a[1], tgt = a[2];
			addDataItem(src, tgt, label);
		}
	}

	private void addDataItem(String src, String tgt, String label) throws RepositoryException {
		String ns = "http://example.org/gmark/";
		URIImpl subject = new URIImpl(ns + "o" + src);
		URIImpl predicate = new URIImpl(ns + "p" + label);
		Value object =  new URIImpl(ns + "o" + tgt);
		Statement stmt = new StatementImpl(subject, predicate, object);
		cxn.add(stmt);
	}

	private void iterateQueries() throws IOException {
		for (String filename : queries) {
			byte[] bytes = Files.readAllBytes(Paths.get(filename));
			String queryStr = new String(bytes, StandardCharsets.UTF_8);
			for (int i = 0; i < WARMUPS; i++) {
				tryQuery(filename, queryStr);  // warmup
			}
			long deltaSum = 0L;
			long deltaCount = 0L;
			long count = -1L;
			for (int i = 0; i < REPEATS; i++) {
				Long[] pair = tryQuery(filename, queryStr);
				if (pair != null) {
					Long delta = pair[0];
					count = pair[1];
					deltaSum += delta;
					deltaCount++;
				}
			}
			if (deltaCount > 0L) {
				double delta = (deltaSum * 1d) / deltaCount;
				printFields("query", filename, String.valueOf(delta), String.valueOf(count));
			}
		}
	}

	private Long[] tryQuery(String filename, String queryStr) {
		ExecutorService executor = Executors.newSingleThreadExecutor();
		Future<Long[]> future = executor.submit(new Callable<Long[]>() {
			@Override
			public Long[] call() throws Exception {
				cxn.begin();
				Long[] pair = performQuery(queryStr, filename);
				cxn.commit();
				return pair;
			}
		});
		Long[] pair = null;
		try {
			pair = future.get(TIMEOUT, TimeUnit.SECONDS);
		} catch (TimeoutException ignored) {  // TimeoutException
			try {
				cxn.rollback();
			} catch (RepositoryException ex) {
				throw new RuntimeException(ex);
			}
			System.out.println(String.join(",", "query", filename, "TIMEOUT"));
//			printFields("query", filename, "TIMEOUT");
//			printFields("query", filename,  String.valueOf(0));  // TIMEOUT
		} catch (InterruptedException | ExecutionException ex) {
			System.err.println(String.join(",", "query", filename, "EX"));
//			printFields("query", filename, "EX");
		}
		executor.shutdownNow();
		return pair;
	}

	private Long[] performQuery(String queryStr, String filename) throws Exception {
		Query query = cxn.prepareQuery(QueryLanguage.SPARQL, queryStr);
		long time1 = System.nanoTime();
		if (query instanceof TupleQuery) {
			TupleQuery tupleQuery = (TupleQuery) query;
			TupleQueryResult result = tupleQuery.evaluate();
			long time2 = System.nanoTime();
			long delta = time2 - time1;
			long count = 0;
			long val = 0;
			while (result.hasNext()) {
				count++;
				val = result.next();
			}
			result.close();
			List<Long> pair = Arrays.asList(delta, val);  // or: count instead of val
			return pair.toArray(new Long[pair.size()]);
		} else if (query instanceof BooleanQuery) {
			BooleanQuery booleanQuery = (BooleanQuery) query;
			boolean answer = booleanQuery.evaluate();
			long time2 = System.nanoTime();
			long delta = time2 - time1;
			List<Long> pair = Arrays.asList(delta, answer ? 1L : 0L);
			return pair.toArray(new Long[pair.size()]);
		}
		else {
			throw new RuntimeException("unhandled type");
		}
	}

	private static void printFields(String... args) {
		System.out.println(String.join(",", args));
	}

//	private void printTriples() throws Exception {
//		TupleQueryResult tqr = cxn.prepareTupleQuery(QueryLanguage.SPARQL,
//			"select ?x ?p ?o where { ?x ?p ?o . }").evaluate();
//		while (tqr.hasNext()) {
//			System.out.println(tqr.next());
//		}
//	}
}
