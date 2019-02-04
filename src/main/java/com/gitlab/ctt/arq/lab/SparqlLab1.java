package com.gitlab.ctt.arq.lab;

import com.gitlab.ctt.arq.sparql.ElementDeepWalker;
import com.gitlab.ctt.arq.util.QueryFixer;
import com.gitlab.ctt.arq.util.SparqlUtil;
import com.gitlab.ctt.arq.utilx.Resources;
import fj.data.Either;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.sparql.syntax.*;



public class SparqlLab1 {
	public static void main(String[] args) {
		test2();
	}

	private static void test2() {


		String queryStr = Resources.getResourceAsString("sample/misc/scrap.sparql");
		String queryStr2 = QueryFixer.get().fix(queryStr);
		Either<Exception, Query> maybeQuery = SparqlUtil.get().toQuery(queryStr2);
		Query query = maybeQuery.right().value();
		System.out.println(query.toString());
	}

	void test1() {






		String queryString = "PREFIX : <s:/> SELECT * WHERE {?x :a/:b|:c* ?y}";

		new SparqlLab1().withJena(queryString);
	}

	void withJena(String queryString) {
		Query query = QueryFactory.create(queryString);

		ElementVisitor elementVisitor = new ElementVisitorBase() {
			@Override
			public void visit(ElementPathBlock el) {
				el.patternElts().forEachRemaining(tp -> {


					StringMakePathVisitor pathVisitor = new StringMakePathVisitor();
					tp.getPath().visit(pathVisitor);
					System.out.println(pathVisitor.getResult());
				});
				super.visit(el);
			}
		};
		Element element = query.getQueryPattern();
		ElementDeepWalker.walk(element, elementVisitor);
		System.out.println(query);
	}
}
