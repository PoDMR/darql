package com.gitlab.ctt.arq.util;

import com.gitlab.ctt.arq.sparql.ElementDeepWalker;
import fj.data.Either;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.Syntax;
import org.apache.jena.shared.PrefixMapping;
import org.apache.jena.sparql.core.Prologue;
import org.apache.jena.sparql.path.Path;
import org.apache.jena.sparql.path.PathVisitor;
import org.apache.jena.sparql.syntax.*;

import java.util.Map;

public class SparqlUtil {
	private static final SparqlUtil INSTANCE = new SparqlUtil();

	public static SparqlUtil get() {
		return INSTANCE;
	}






	


























	public void handleQuery(Element element, PathVisitor pathVisitor) {
		ElementVisitor elementVisitor = makePathVisitor(pathVisitor);
		if (element != null) {
			ElementDeepWalker.walk(element, elementVisitor);
		} 
	}

	private ElementVisitor makePathVisitor(PathVisitor pathVisitor) {
		return new ElementVisitorBase() {
			@Override
			public void visit(ElementPathBlock el) {
				el.patternElts().forEachRemaining(triplePath -> {
					Path path = triplePath.getPath();
					if (path != null) {
						path.visit(pathVisitor);
					}
				});
				super.visit(el);
			}
		};
	}

	@Deprecated
	public Either<Exception, Element> toElement(String sparqlStr) {
		try {
			Query query = QueryFactory.create(sparqlStr);
			return Either.right(query.getQueryPattern());
		} catch (Exception e) {
			return Either.left(e);
		}
	}

	public Either<Exception, Query> toQuery(String sparqlStr) {
		try {
			Query query = QueryFactory.create(sparqlStr);
			return Either.right(query);
		} catch (Exception e) {
			return Either.left(e);
		}
	}


	public static Query parseWithPrefixes(String queryString, Map<String, String> map) {
		PrefixMapping prefixMap = PrefixMapping.Factory.create()
			.withDefaultMappings(PrefixMapping.Extended).setNsPrefixes(map);
		Prologue prologue = new Prologue(prefixMap);
		Query query = new Query(prologue);
		String baseURI = null;
		return QueryFactory.parse(query, queryString, baseURI, Syntax.defaultQuerySyntax);
	}

	
	public static String normalize(String queryStr) {
		String queryStr2 = QueryFixer.get().fix(queryStr);
		Either<Exception, Query> maybeQuery = SparqlUtil.get().toQuery(queryStr2);
		if (maybeQuery.isRight()) {
			Query query = maybeQuery.right().value();
			try {
				return query.toString();
			} catch (Exception e) {
				return queryStr;
			}
		}
		return queryStr;
	}
}
