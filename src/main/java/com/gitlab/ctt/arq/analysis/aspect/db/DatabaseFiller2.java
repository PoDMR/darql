package com.gitlab.ctt.arq.analysis.aspect.db;

import com.gitlab.ctt.arq.analysis.aspect.util.BaseDeduplicator;
import com.gitlab.ctt.arq.core.format.QueryEntry;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.jena.query.Query;
import org.skife.jdbi.v2.util.IntegerColumnMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class DatabaseFiller2 extends BaseDeduplicator {
	private static final Logger LOGGER = LoggerFactory.getLogger(DatabaseFiller2.class);
	public static final String QUERIES_TABLE_NAME = "Queries";
	public static final String DUPLICATES_TABLE_NAME = "Duplicates";

	private static final Pattern ORIGIN_PATTERN = Pattern.compile("(?:([^/]+)/)?([^:]+)(?::(\\d+))?");
	private static final boolean useStats = true;

	private DbUtil db = new DbUtil();
	private final ReentrantLock lock = new ReentrantLock();
	private int baseId = 0;
	private String tag;

	@Override
	public void init() {
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

		if (useStats) {

			db.getHandle().execute("CREATE TABLE IF NOT EXISTS \"OriginStats\" (\n" +
				"\"originMajor\" text UNIQUE,\n" +
				"nodupe INT,\n" +
				"dupe INT,\n" +
				"valid INT,\n" +
				"\"order\" INT\n" +
				");");
		}
	}

	protected void onTableExists(String tableName) {
		LOGGER.info("Table {} exists", tableName);

		if (QUERIES_TABLE_NAME.equals(tableName) && baseId == 0) {








			Integer offset1 = db.getHandle()
				.createQuery("SELECT max(id) FROM \"Queries\"")
				.map(IntegerColumnMapper.WRAPPER).first();
			Integer offset2 = db.getHandle()
				.createQuery("SELECT max(id) FROM \"Duplicates\"")
				.map(IntegerColumnMapper.WRAPPER).first();
			if (offset1 != null || offset2 != null) {
				int offset1ub = offset1 != null ? offset1 : 0;
				int offset2ub = offset2 != null ? offset2 : 0;
				int offset = Math.max(offset1ub, offset2ub);
				baseId = 1 + offset;
				LOGGER.info("Using ID offset: {}", offset);
			}
		}
	}

	@Override
	public void commit() {
		if (useStats) {

			db.getHandle().execute("INSERT INTO \"OriginStats\" (\"originMajor\", nodupe, dupe, valid)\n" +
				"    SELECT t.\"originMajor\", nodupe, dupe, valid\n" +
				"    FROM (\n" +
				"        SELECT \"originMajor\",\n" +
				"        count(q.id) AS nodupe,\n" +
				"        count(q.id) FILTER (WHERE \"parseError\" = false) AS valid\n" +
				"        FROM \"Queries\" AS q\n" +
				"        --LEFT JOIN \"Origins\" AS o ON q.id = o.id\n" +
				"        --WHERE \"parseError\" = false\n" +
				"        GROUP BY \"originMajor\"\n" +
				"    ) AS t LEFT JOIN (\n" +
				"        SELECT \"originMajor\", count(d.id) AS dupe FROM \"Duplicates\" AS d\n" +
				"        --LEFT JOIN \"Origins\" AS o ON d.id = o.id\n" +
				"        GROUP BY \"originMajor\"\n" +
				"    ) AS d ON t.\"originMajor\" = d.\"originMajor\"\n" +
				"ON CONFLICT (\"originMajor\") DO UPDATE SET \n" +
				"nodupe = EXCLUDED.nodupe,\n" +
				"dupe = EXCLUDED.dupe,\n" +
				"valid = EXCLUDED.valid;");
		}

		db.close();


	}

	@Override
	public void setTag(String tag) {
		this.tag = tag;
	}

	@Override
	public Boolean apply(QueryEntry queryEntry) {
		if (!normalize(queryEntry)) return false;

		lock.lock();
		try {

			long id = baseId + countQuery.getAndIncrement();
			byte[] hash = DigestUtils.sha256(queryEntry.queryStr.getBytes());
			String hashStr = Hex.encodeHexString(hash);

			Integer did = db.getHandle()
				.createQuery("SELECT id FROM \"Queries\" WHERE hash = " +
				"decode('" + hashStr + "', 'hex');")
				.map(IntegerColumnMapper.WRAPPER).first();
			if (did != null) {

				DupeRecord dupeRecord = new DupeRecord();
				dupeRecord.id = (int) id;
				dupeRecord.origin = queryEntry.origin;
				dupeRecord.copyOfId = did;
				putDupeOrigin(dupeRecord);
				db.insertObject(DUPLICATES_TABLE_NAME, dupeRecord);
			} else {
				QueryRecord record = new QueryRecord();
				record.id = (int) id;
				record.origin = queryEntry.origin;
				putQueryOrigin(record);
				record.queryStr = queryEntry.queryStr.replaceAll("\0", "");  
				record.maybeQuery = queryEntry.maybeQuery;
				RecordProcessor.processFull(record);
				record.hash = hash;

				db.insertRecord(QUERIES_TABLE_NAME, record);
			}
		} catch (Exception e) {
			LOGGER.warn("Database problem.\nProblem location: {}\n" +
				"Problem query: {}\n" +
				"Problem stacktrace: {}",
				queryEntry.origin, queryEntry.queryStr, e);
		} finally {
			lock.unlock();
		}

		return false;
	}

	private boolean normalize(QueryEntry queryEntry) {
		if (queryEntry.maybeQuery != null) {
			if (queryEntry.maybeQuery.isRight()) {
				Query query = queryEntry.maybeQuery.right().value();
				try {
					queryEntry.queryStr = query.toString();  
				} catch (Exception e) {
					LOGGER.warn("Normalization error: {}\n{}\n{}",
						e, queryEntry.queryStr, queryEntry.origin);

				}
			}



		}
		return true;
	}

	private void putQueryOrigin(QueryRecord record) {
		if (!useStats) return;
		Matcher matcher = ORIGIN_PATTERN.matcher(record.origin);
		if (matcher.matches()) {
			record.originMajor = matcher.group(1);
			record.originMinor = matcher.group(2);
			record.originLinum = Integer.parseInt(matcher.group(3));
		}

	}

	private void putDupeOrigin(DupeRecord dupeRecord) {
		if (!useStats) return;
		Matcher matcher = ORIGIN_PATTERN.matcher(dupeRecord.origin);
		if (matcher.matches()) {
			dupeRecord.originMajor = matcher.group(1);
			dupeRecord.originMinor = matcher.group(2);
			dupeRecord.originLinum = Integer.parseInt(matcher.group(3));
		}

	}
}
