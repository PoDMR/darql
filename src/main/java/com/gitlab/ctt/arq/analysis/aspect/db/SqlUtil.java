package com.gitlab.ctt.arq.analysis.aspect.db;

import jdk.nashorn.internal.ir.annotations.Ignore;
import org.apache.commons.codec.binary.Hex;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.*;
import java.util.regex.Pattern;

public class SqlUtil {
	public static void main(String[] args) {
		System.out.println(SqlUtil.createTable("Queries", QueryRecord.class));
		QueryRecord record = new QueryRecord();
		record.queryStr = "SELECT * WHERE { ?x a ?y }";
		System.out.println(SqlUtil.insertRecord("Queries", record));
	}

	private static final String ID_KEY = "id";
	private static Set<String> EXCLUDES = new HashSet<>(Arrays.asList(
		ID_KEY,
		"maybeQuery"
	));

	public static String checkExists(String tableName) {

		return String.format("SELECT EXISTS (\n" +
			"   SELECT 1\n" +
			"   FROM   information_schema.tables \n" +
			"   WHERE  table_schema = 'public'\n" +
			"   AND    table_name = '%s'\n" +
			"   );", tableName);
	}

	public static String createTable(String tableName, Class<?> type) {
		Field[] fields = QueryRecord.class.getFields();
		List<String> fieldStrings = new ArrayList<>();
		for (Field field : fields) {

			String name = field.getName();
			if (!EXCLUDES.contains(name) && !field.isAnnotationPresent(Ignore.class)) {
				String typeName = field.getType().getSimpleName();
				typeName = typeName.replaceAll(Integer.class.getSimpleName(), "INT");
				typeName = typeName.replaceAll(String.class.getSimpleName(), "TEXT");
				typeName = typeName.replaceAll(Long.class.getSimpleName(), "BIGINT");
				typeName = typeName.replaceAll(Boolean.class.getSimpleName(), "BOOL");
				typeName = typeName.replaceAll(Double.class.getSimpleName(), "REAL");
				typeName = typeName.replaceAll(Pattern.quote(byte[].class.getSimpleName()), "BYTEA");
				fieldStrings.add(String.format("\"%s\" %s", name, typeName));
			}
		}
		return String.format("CREATE TABLE \"%s\"(\n" +
			"    id INT PRIMARY KEY NOT NULL,\n" +
			"    %s\n" +
			");", tableName, String.join(",\n    ", fieldStrings));
	}










	public static String insertRecord(String tableName, QueryRecord record) {
		return insertObject(tableName, record);
	}

	public static String insertObject(String tableName, Object object) {
		Field[] fields = object.getClass().getFields();
		List<String> fieldStrings = new ArrayList<>();
		List<String> fieldValues = new ArrayList<>();


		for (Field field : fields) {
			String name = field.getName();
			if (!EXCLUDES.contains(name) || ID_KEY.equals(name)) {
				try {
					Object value = field.get(object);
					if (value != null) {
						String valueStr = value.toString();
						if (value.getClass().equals(String.class)) {
							valueStr = "'" + escapeString(valueStr) + "'";
						} else if (value.getClass().equals(byte[].class)) {
							String hashStr = Hex.encodeHexString((byte[]) value);
							valueStr = "decode('" + hashStr + "', 'hex')";
						}
						fieldStrings.add("\"" + name + "\"");
						fieldValues.add(valueStr);
					}
				} catch (IllegalAccessException e) {
					throw new RuntimeException("Reflection exception", e);
				}
			}
		}

		return String.format("INSERT INTO \"%s\" (%s)\n" +
			"VALUES (%s); ", tableName,
			String.join(",", fieldStrings),
			String.join(",", fieldValues));
	}

	public static String insertDupe(String tableName, long id, String origin, long dupeOf) {

		return String.format("INSERT INTO \"%s\" (\"id\", \"origin\", \"copyOfId\")\n" +
				"VALUES (%s, '%s', %s); ", tableName,
			id, escapeString(origin), dupeOf);
	}

	private static String escapeString(String sqlStr) {
		return sqlStr.replaceAll("'", "''");
	}
}
