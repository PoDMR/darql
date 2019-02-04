package com.gitlab.ctt.arq.core;

import com.gitlab.ctt.arq.analysis.Job;
import com.gitlab.ctt.arq.analysis.SimilaritySearch;
import com.gitlab.ctt.arq.analysis.StreakAnalysis;
import com.gitlab.ctt.arq.analysis.aspect.*;
import com.gitlab.ctt.arq.analysis.aspect.db.DatabaseFiller2;
import com.gitlab.ctt.arq.core.format.LineDelimFormat;
import com.gitlab.ctt.arq.core.format.LineItem;
import com.gitlab.ctt.arq.core.format.QueryEntry;
import com.gitlab.ctt.arq.core.format.WikiTsvLineFormat;
import com.gitlab.ctt.arq.util.LogParse;
import com.gitlab.ctt.arq.util.QueryFixer;
import com.gitlab.ctt.arq.util.SparqlUtil;
import com.gitlab.ctt.arq.utilx.BlockingExecutor;
import com.gitlab.ctt.arq.utilx.Resources;
import com.gitlab.ctt.arq.vfs.FileEntry;
import fj.data.Either;
import org.apache.jena.ext.com.google.common.collect.ImmutableMap;
import org.apache.jena.query.Query;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class FileDispatcher implements Consumer<FileEntry> {
	private PathExtractor pathExtractor = new PathExtractor();
	private PathHunter pathHunter = new PathHunter();
	private DesignChecker designChecker = new DesignChecker();
	private NonAcyclicCollector nonAcyclicCollector = new NonAcyclicCollector();
	private Deduplicator deduplicator = new Deduplicator();

	private PropertyCounter propertyCounter = new PropertyCounter();
	private OpDistribution opDistribution = new OpDistribution();
	private OpDistribution opDistribution2 = new OpDistribution(true);
	private OptAnalysis optAnalysis = new OptAnalysis();
	private UriCounter uriCounter = new UriCounter();
	private StreakAnalysis<String> streakAnalysis = StreakAnalysis.stringStreakAnalysis(30, 0.75d);
	private DatabaseFiller2 databaseFiller = new DatabaseFiller2();
	private SimilaritySearch similaritySearch = new SimilaritySearch();

	private List<Job<Either<Exception, Query>, ?>> jobs = new ArrayList<>(Arrays.asList(
		pathExtractor,      
		pathHunter,         
		designChecker,      

		propertyCounter,    
		optAnalysis,        
		uriCounter,         
		opDistribution2,    
		opDistribution      
	));
	private List<Job<QueryEntry, Boolean>> extJobs = new ArrayList<>(Arrays.asList(
		similaritySearch,
		deduplicator,       
		databaseFiller
	));
	@SuppressWarnings("ArraysAsListWithZeroOrOneArgument")
	private List<Job<Either<Exception, Query>, ?>> jobsNoAsync =  new ArrayList<>(Arrays.asList(
		streakAnalysis      
	));
	private List<String> validJobs =  Arrays.asList(
		"pathExtractor",
		"propertyCounter",
		"propertyCounter",
		"opDistribution"
	);

	private FileHandler clfHandler;
	private FileHandler tsvHandler;
	private FileHandler delimHandlerHash;
	private FileHandler delimHandlerDash;
	private FileHandler wikiTsvHandler;

	private Map<String, Supplier<FileHandler>> handlers = ImmutableMap.
		<String, Supplier<FileHandler>>builder()
		.put("clfHandler", () -> clfHandler)
		.put("tsvHandler", () -> tsvHandler)
		.put("delimHandlerHash", () -> delimHandlerHash)
		.put("delimHandlerDash", () -> delimHandlerDash)
		.put("wikiTsvHandler", () -> wikiTsvHandler)
		.build();
	private Map<String, String> handlerMap;

	private ThreadPoolExecutor executor;
	private int maxParallel = 8;
	private String tag;

	public FileDispatcher() {
		this(8);
	}

	public FileDispatcher(int maxParallel) {
		this.maxParallel = maxParallel;
		clfHandler = FileHandler.record(this::actOnClfLine);
		tsvHandler = FileHandler.record(this::actOnTSVLine);
		LineDelimFormat lineDelimHash = new LineDelimFormat(this::actOnQuery);  
		LineDelimFormat lineDelimDash = new LineDelimFormat(this::actOnQuery, LineDelimFormat.DASH_DELIM);
		WikiTsvLineFormat wikiTsvLineFormat = new WikiTsvLineFormat(this::actOnQuery);
		wikiTsvHandler = FileHandler.record(wikiTsvLineFormat::acceptLine);


		delimHandlerHash = FileHandler.record(lineDelimHash::acceptLine);
		delimHandlerDash = FileHandler.record(lineDelimDash::acceptLine);





		readAndSetMaxParallel();
		executor = new BlockingExecutor(maxParallel, maxParallel);
	}

	public String getTag() {
		return tag;
	}

	public void setTag(String tag) {
		this.tag = tag;
	}

	public void setValidJobs(List<String> validJobs) {
		this.validJobs = validJobs;
	}

	public void setHandlerMap(Map<String, String> handlerMap) {
		this.handlerMap = handlerMap;
	}

	private void readAndSetMaxParallel() {
		String maxCoresStr = Resources.getLocalPropertyOr("arq.cores", "8");
		try {
			maxParallel = Integer.parseInt(maxCoresStr);
		} catch (NumberFormatException ignored) {
		}
	}

	public void init() {
		jobsInit(jobs);
		jobsInit(jobsNoAsync);
		jobsInit(extJobs);
	}

	private void jobsInit(List<? extends Job<?, ?>> jobs) {

			validJobs = validJobs.stream().map(String::toLowerCase)
				.collect(Collectors.toList());
			jobs.removeIf(
				j -> !validJobs.contains(j.getClass().getSimpleName().toLowerCase())
			);

		jobs.forEach(job -> job.setTag(tag));
		jobs.forEach(Job::init);
	}


	public void commit() {
		waitForAll();
		jobs.forEach(Job::commit);
		jobsNoAsync.forEach(Job::commit);
		extJobs.forEach(Job::commit);
	}


	public void waitForAll() {
		try {
			executor.shutdown();  
			executor.awaitTermination(24L, TimeUnit.HOURS);
		} catch (InterruptedException ignored) {
			executor.shutdown();
		}
		executor = new BlockingExecutor(maxParallel, maxParallel);
	}


	@Override
	public void accept(FileEntry file) {
		String name = file.getName();



		if (handlerMap == null) {

			if (name.matches(".*(RKB-Explorer|tsv/|TSV/).*")) {
				tsvHandler.acceptFile(file);
			} else if (name.matches(".*tsv")) {
				wikiTsvHandler.acceptFile(file);
			} else if (name.matches(".*(sparql|SPARQL|wikidata).*")) {


				if (!name.matches(".*(biomed/|biomed_uw13|hash|HASH).*")) {
					delimHandlerHash.acceptFile(file);
				} else {
					delimHandlerDash.acceptFile(file);  
				}
			} else {

				clfHandler.acceptFile(file);

			}
		} else {

			for (Map.Entry<String, String> e : handlerMap.entrySet()) {
				String pattern = e.getKey();
				String handlerName = e.getValue();
				if (name.matches(pattern)) {
					handlers.get(handlerName).get().acceptFile(file);
					break;
				}
			}
		}
	}

	private void actOnTSVLine(LineItem line) {
		handleTSVLine(line);
	}

	private void handleTSVLine(LineItem line) {
		String[] split = line.lineStr.split("\t");
		if (split.length >= 4) {
			if ("sparql".equals(split[2])) {
				actOnQuery(new LineItem(split[3], line));
			}
		}
	}

	private void handleClfLine(LineItem line) {
		Either<String, String> parsedLine = LogParse.get().queryFromLogLine(line.lineStr);
		if (parsedLine.isRight()) {
			String queryStr = parsedLine.right().value();
			handleQuery(new LineItem(queryStr, line));
		}
	}

	private void handleClfLineGeneric(LineItem line, Consumer<LineItem> consumer) {
		Either<String, String> parsedLine = LogParse.get().queryFromLogLine(line.lineStr);
		if (parsedLine.isRight()) {
			String queryStr = parsedLine.right().value();
			consumer.accept(new LineItem(queryStr, line));
		}
	}



	private void handleQuery(LineItem queryStr) {
		doParse(queryStr);

	}

	private void doParse(LineItem queryStr) {
		String queryStr2 = QueryFixer.get().fix(queryStr.lineStr);  
		Either<Exception, Query> maybeQuery = SparqlUtil.get().toQuery(queryStr2);
		logBad(maybeQuery, queryStr.lineStr);

		QueryEntry queryEntry = new QueryEntry(maybeQuery, queryStr.lineStr,
			String.format("%s:%s", queryStr.origin, queryStr.num));
		processEntry(queryEntry);
	}

	protected void processEntry(QueryEntry queryEntry) {
		jobs.forEach(j -> j.apply(queryEntry.maybeQuery));

		extJobs.forEach(j -> {
			j.apply(queryEntry);
		});
	}

	private void logBad(Either<Exception, Query> maybeQuery, String queryStr) {
		if (isDebug && maybeQuery.isLeft()) {
			System.err.println("#### bad\n" + queryStr);
		}
	}

	public static final boolean isDebug = initDebugFlag();

	private static boolean initDebugFlag() {
		return System.getenv("DEBUG") != null



			;
	}

	private void sequentialHandleQuery(LineItem queryStr) {
		if (jobsNoAsync.contains(streakAnalysis)) {
			streakAnalysis.accept(queryStr.lineStr, 0);
		}

	}




	private void asyncHandleClfLine(LineItem line) {
		executor.execute(() -> handleClfLine(line));
	}

	private void asyncHandleQuery(LineItem queryStr) {
		executor.execute(() -> handleQuery(queryStr));
	}



	private void actOnClfLine(LineItem line) {
		if (jobsNoAsync.size() > 0) {
			handleClfLineGeneric(line, this::sequentialHandleQuery);
		}
		if (jobs.size() > 0 || extJobs.size() > 0) {
			asyncHandleClfLine(line);
		}
	}


	private void actOnQuery(LineItem queryStr) {
		if (jobsNoAsync.size() > 0) {
			sequentialHandleQuery(queryStr);
		}
		if (jobs.size() > 0 || extJobs.size() > 0 || jobsNoAsync.size() == 0) {
			asyncHandleQuery(queryStr);
		}
	}
}
