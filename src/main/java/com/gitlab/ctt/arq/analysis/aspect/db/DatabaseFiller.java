package com.gitlab.ctt.arq.analysis.aspect.db;

import com.gitlab.ctt.arq.analysis.aspect.util.BaseDeduplicator;
import com.gitlab.ctt.arq.core.format.QueryEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.locks.ReentrantLock;


public class DatabaseFiller extends BaseDeduplicator {
	private static final Logger LOGGER = LoggerFactory.getLogger(DatabaseFiller.class);
	public static final String QUERIES_TABLE_NAME = "Queries";
	public static final String DUPLICATES_TABLE_NAME = "Duplicates";
	public static final String TRANSACTION_GLOBAL = "global";
	public static final String TRANSACTION_LOCAL = "local";

	private DbUtil db = new DbUtil();

	private boolean useTransactions = false;
	private final ReentrantLock lock = new ReentrantLock();

	@Override
	public void init() {
		super.initConcurrency();
		db.init();
		if (!db.checkExists(QUERIES_TABLE_NAME)) {
			db.createTableQueries(QUERIES_TABLE_NAME);
		} else {
			onTableExists(QUERIES_TABLE_NAME);
		}
		if (!db.checkExists(DUPLICATES_TABLE_NAME)) {
			db.createTableDuplicates(DUPLICATES_TABLE_NAME);
		} else {
			onTableExists(DUPLICATES_TABLE_NAME);
		}

		if (useTransactions) {
			db.getHandle().begin();
			db.getHandle().checkpoint(TRANSACTION_GLOBAL);
		}
	}

	protected void onTableExists(String tableName) {
		LOGGER.warn("Table {} exists", tableName);

	}

	@Override
	public void commit() {
		if (useTransactions) {
			db.getHandle().commit();
		}

		db.close();

	}

	@Override
	protected void onNew(QueryEntry queryEntry, long num) {
		super.onNew(queryEntry, num);
		QueryRecord record = new QueryRecord();
		record.id = (int) num;
		record.origin = queryEntry.origin;
		record.queryStr = queryEntry.queryStr.replaceAll("\0", "");
		record.maybeQuery = queryEntry.maybeQuery;
		RecordProcessor.processFull(record);

		submitRecord(record);
	}

	@Override
	protected void onDupe(QueryEntry queryEntry, long num, long dupeNum) {
		super.onDupe(queryEntry, dupeNum, num);

		useDatabase(queryEntry.queryStr, () ->
			db.insertDupe(DUPLICATES_TABLE_NAME, num, queryEntry.origin, dupeNum)
		);
	}

	private void submitRecord(QueryRecord record) {
		useDatabase(record.queryStr, () ->
			db.insertRecord(QUERIES_TABLE_NAME, record)
		);
	}

	private void useDatabase(String msg, Runnable closure) {
		lock.lock();
		try {
			if (useTransactions) {
				db.getHandle().checkpoint(TRANSACTION_LOCAL);
			}
			closure.run();
			if (useTransactions) {
				db.getHandle().release(TRANSACTION_LOCAL);
			}
		} catch (Exception e) {
			if (useTransactions) {
				try {
					db.getHandle().rollback(TRANSACTION_LOCAL);
				} catch (Exception e2) {
					LOGGER.warn("Problem while rolling back.", e2);
				}
			}
			onInsertFailed(msg, e);
		} finally {
			lock.unlock();
		}
	}


	private synchronized void onInsertFailed(String str, Exception e) {
		LOGGER.warn("Failed insert.\nCaused by: {}\nQuery: {}", e, str);

		db.close();
		db.init();
	}
}
