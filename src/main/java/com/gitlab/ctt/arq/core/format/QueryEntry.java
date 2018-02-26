package com.gitlab.ctt.arq.core.format;

import fj.data.Either;
import org.apache.jena.query.Query;


public class QueryEntry {
	public QueryEntry(Either<Exception, Query> maybeQuery, String queryStr, String origin) {
		this.maybeQuery = maybeQuery;
		this.queryStr = queryStr;
		this.origin = origin;
	}

	public Either<Exception, Query> maybeQuery;
	public String queryStr;
	public String origin;
}
