package com.gitlab.ctt.arq.analysis.aspect.util;

import com.gitlab.ctt.arq.analysis.Job;
import com.gitlab.ctt.arq.core.format.QueryEntry;
import com.gitlab.ctt.arq.utilx.Resources;
import fj.data.Either;
import org.apache.jena.query.Query;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.Serializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Paths;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class BaseDeduplicator implements Job<QueryEntry, Boolean> {
	private static final Logger LOGGER = LoggerFactory.getLogger(BaseDeduplicator.class);
	private static final long LOG_THRESHOLD = 100_000;

	private static final Pattern KILL_PATTERN = Pattern.compile("(?<=\\n)####[^\\n]*\\n");

	protected boolean useFile = false;
	protected DB db;
	protected ConcurrentMap<String, Long> query2num;


	protected AtomicLong countUnique = new AtomicLong();
	protected AtomicLong countQuery = new AtomicLong();
	protected AtomicLong countTotal = new AtomicLong();

	protected void initConcurrency() {































		String pwd = Resources.getLocalProperty("arq.pwd");
		String filename = Paths.get(pwd, "mapdb.db").toAbsolutePath().toString();
		boolean exists = new File(filename).exists();
		if (!useFile && !exists) {
			query2num = new ConcurrentHashMap<>();
		} else {
			db = DBMaker
				.fileDB(filename)
				.fileMmapEnableIfSupported()
				.closeOnJvmShutdown()
				.checksumHeaderBypass()




				.make();
			query2num = db
				.hashMap("query2num", Serializer.STRING, Serializer.LONG)
				.createOrOpen();
			query2num.clear();
		}
	}

	@Override
	public Boolean apply(QueryEntry queryEntry) {
		Either<Exception, Query> maybeQuery = queryEntry.maybeQuery;
		countTotal.getAndIncrement();

		if (maybeQuery != null) {
			if (maybeQuery.isRight()) {
				Query query = maybeQuery.right().value();
				try {


					queryEntry.queryStr = query.toString();  
					handleQueryStr(queryEntry);
				} catch (Exception e) {
					LOGGER.warn("Unhandled", e);

				}
			} 
		} else {
			handleQueryStr(queryEntry);
		}
		return false;
	}

	@Override
	public void commit() {
		if (useFile && db != null) {
			db.close();
		}
	}

	private void handleQueryStr(QueryEntry queryEntry) {
		long runningNum = countQuery.getAndIncrement();
		if (runningNum % LOG_THRESHOLD == 0L) {
			logStatus();
		}
		Matcher matcher = KILL_PATTERN.matcher(queryEntry.queryStr);
		if (matcher.matches()) {
			LOGGER.warn("Query contains line delimiter");
			queryEntry.queryStr = matcher.replaceAll("");
		}
		Long num = query2num.putIfAbsent(queryEntry.queryStr, runningNum);
		if (num == null) {
			countUnique.getAndIncrement();


			onNew(queryEntry, runningNum);
		} else {
			onDupe(queryEntry, runningNum, num);
		}
	}

	protected void onNew(QueryEntry queryEntry, long num) {

	}

	protected void onDupe(QueryEntry queryEntry, long num, long dupeNum) {

	}

	protected void logStatus() {
		LOGGER.trace("unique/query/total={}/{}/{}", countUnique, countQuery, countTotal);
	}




}
