package com.gitlab.ctt.arq.analysis.aspect.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.skife.jdbi.v2.DBI;
import org.skife.jdbi.v2.Handle;
import org.skife.jdbi.v2.NoOpStatementRewriter;
import org.skife.jdbi.v2.Query;
import org.skife.jdbi.v2.util.BooleanColumnMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.*;

public class DbUtil {
	public static void main(String[] args) {
		String testQueryStr = "INSERT INTO db1 ('''')";
		DbUtil db = new DbUtil();
		Properties properties = new Properties();


		properties.setProperty("jdbcUrl", "jdbc:sqlite::memory:");
		db.init(properties);
		db.getHandle().insert(testQueryStr);
		db.close();
	}

	private static final Logger LOGGER = LoggerFactory.getLogger(DbUtil.class);

	private boolean persistentConnection;

	protected HikariDataSource ds;
	protected DBI dbi;
	protected Handle h;

	public DbUtil() {
		this(true);
	}

	public DbUtil(boolean persistentConnection) {
		this.persistentConnection = persistentConnection;
	}

	public void init() {
		Properties properties = fromEnv();
		init(properties);
	}

	public void init(Properties properties) {
		HikariConfig config = new HikariConfig(properties);
		ds = new HikariDataSource(config);
		dbi = new DBI(ds);
		if (persistentConnection) {
			h = dbi.open();
		}
		h.setStatementRewriter(new NoOpStatementRewriter());
	}

	public Properties fromEnv() {
		String url = System.getenv().get("DB_URL");


		String username = System.getenv().get("DB_USER");
		String password = System.getenv().get("DB_PASS");
		if (url == null) url = "jdbc:postgresql://localhost:5432/postgres";
		if (username == null) username = "postgres";
		if (password == null) password = "postgres";

		Properties properties = new Properties();


		properties.setProperty("dataSource.user", username);
		properties.setProperty("dataSource.password", password);



		properties.setProperty("jdbcUrl", url);
		return properties;
	}

	public void close() {
		try {
			if (persistentConnection) {
				h.close();
			}
			ds.close();
		} catch (Throwable e) {
			LOGGER.warn("Problem closing connection.", e);
		}
	}

	public Handle getHandle() {
		return h;
	}

	public boolean checkExists(String tableName) {
		return h.createQuery(SqlUtil.checkExists(tableName))
			.map(BooleanColumnMapper.PRIMITIVE).first();
	}

	public void createTableQueries(String tableName) {
		h.execute(SqlUtil.createTable(tableName, QueryRecord.class));
	}

	public void createTableDuplicates(String tableName) {

		h.execute(SqlUtil.createTable(tableName, DupeRecord.class));
	}

	public int insertRecord(String tableName, QueryRecord record) {
		return h.insert(SqlUtil.insertRecord(tableName, record));
	}

	public int insertDupe(String tableName, long id, String origin, long dupeOf) {
		return h.insert(SqlUtil.insertDupe(tableName, id, origin, dupeOf));
	}

	public int insertObject(String tableName, Object object) {
		return h.insert(SqlUtil.insertObject(tableName, object));
	}

	public List<Map<String, Object>> query(String queryStr) {
		Query<Map<String, Object>> query = h.createQuery(queryStr);
		return queryToList(query);
	}

	private static ExecutorService executor = Executors.newSingleThreadExecutor();
	private static Map<String, Runnable> sessionMap = new ConcurrentHashMap<>();

	public List<Map<String, Object>> submit(String sessionId, String queryStr, long timeoutSecs) {
		Future<List<Map<String, Object>>> future = executor.submit(() -> dbi.withHandle(h -> {
			Query<Map<String, Object>> query = h.createQuery(queryStr);
			Runnable closer = () -> {
				try {
					query.getContext().getStatement().cancel();
					query.getContext().getStatement().close();
				} catch (SQLException ignored2) {
				}
			};
			sessionMap.put(sessionId, closer);
			try {
				return queryToList(query);
			} catch (Exception ignored) {
				return null;
			}
		}));

		try {
			return future.get(timeoutSecs, TimeUnit.SECONDS);
		} catch (ExecutionException | InterruptedException | TimeoutException ignored) {
			reserve(sessionId);
			return null;
		} finally {
			sessionMap.remove(sessionId);
		}
	}

	public void reserve(String sessionId) {
		sessionMap.computeIfPresent(sessionId, (k, v) -> {
			v.run();
			return null;
		});
	}

	public List<Map<String, Object>> query(String queryStr, long timeoutSecs) {
		Query<Map<String, Object>> query = h.createQuery(queryStr);
		Future<List<Map<String, Object>>> future = executor.submit(() -> queryToList(query));
		try {
			return future.get(timeoutSecs, TimeUnit.SECONDS);
		} catch (ExecutionException | InterruptedException | TimeoutException ignored) {
		}
		return null;
	}

	private List<Map<String, Object>> queryToList(Query<Map<String, Object>> query) {

		return mapQuery(query).list();
	}

	private Query<Map<String, Object>> mapQuery(Query<Map<String, Object>> query) {
		return query.map((index, rs, ctx) -> {
			Map<String, Object> map = new LinkedHashMap<>();
			int n = rs.getMetaData().getColumnCount();
			for (int i = 1; i <= n; i++) {


				String columnName = rs.getMetaData().getColumnLabel(i);
				map.put(columnName, rs.getObject(i));
			}
			return map;
		});
	}
}
