package com.gitlab.ctt.arq.lab;

import com.gitlab.ctt.arq.sparql.ElementDeepWalker;
import com.gitlab.ctt.arq.sparql.SparqlAlgorithms;
import com.gitlab.ctt.arq.sparql.SparqlQueries;
import com.gitlab.ctt.arq.util.SparqlUtil;
import com.gitlab.ctt.arq.utilx.Resources;
import fj.data.Either;
import org.apache.jena.query.Query;
import org.apache.jena.sparql.core.Var;
import org.apache.jena.sparql.expr.Expr;
import org.apache.jena.sparql.expr.ExprFunction2;
import org.apache.jena.sparql.expr.ExprVisitorBase;
import org.apache.jena.sparql.path.PathVisitorBase;
import org.apache.jena.sparql.syntax.*;

import java.util.Optional;
import java.util.Set;

import static com.gitlab.ctt.arq.sparql.SparqlGraph.exprWalkerWalk;

public class SparqlLab2 {
	public static final String SPARQL_STR = "PREFIX wd: <http://www.wikidata.org/entity/>\n" +
		"PREFIX wdt: <http://www.wikidata.org/prop/direct/>\n" +
		"SELECT ?x WHERE {\n" +
		"  {?x wdt:P31 wd:Q729} UNION\n" +
		"  {?x wdt:P31 wd:Q3142}.\n" +
		"}";

	public static final String SPARQL_OPT_STR = "PREFIX wd: <http://www.wikidata.org/entity/>\n" +
		"PREFIX wdt: <http://www.wikidata.org/prop/direct/>\n" +
		"SELECT ?x ?y WHERE {\n" +
		"  ?x wdt:P31 wd:Q729 OPTIONAL {?x wdt:P31 wd:Q3142} OPTIONAL {?y wdt:P31 wd:Q3142}.\n" +
		"  ?y wdt:P31 wd:Q729.\n" +
		"}";

	public static void main(String[] args) {

		SparqlLab2 processor = new SparqlLab2();

		String sparqlStr = Resources.getResourceAsString("sample/misc/group.sparql");
		processor.analyze(sparqlStr);
	}

	void analyze(String sparqlStr) {

		Either<Exception, Query> maybeQuery = SparqlUtil.get().toQuery(sparqlStr);
		if (!maybeQuery.isRight()) {
			throw new RuntimeException(maybeQuery.left().value());
		}
		Query query = maybeQuery.right().value();
		Element element = query.getQueryPattern();
		System.out.println(element);
		SparqlUtil.get().handleQuery(element, new PathVisitorBase());

		ElementDeepWalker.walk(element, new ElementVisitorBase() {
			@Override
			public void visit(ElementTriplesBlock el) {
				System.out.println(el);
			}

			@Override
			public void visit(ElementPathBlock el) {
				System.out.println(el);
			}

			@Override
			public void visit(ElementFilter el) {
				analyzeFilter(el);
			}
		});

		Optional<Boolean> projectionType = SparqlQueries.maybeProjection(query);
		System.out.println("projectionType: " + projectionType);

		Set<Var> vars = SparqlAlgorithms.tripleVars(element);
		System.out.println(vars);



		System.out.println("===");
		System.out.println(query.toString());
	}

	void analyzeFilter(ElementFilter filter) {
		Expr expr = filter.getExpr();
		ExprVisitorBase visitor = new ExprVisitorBase() {
			@Override
			public void visit(ExprFunction2 func) {

				System.out.println(func);
			}
		};
		exprWalkerWalk(visitor, expr);
	}
}
