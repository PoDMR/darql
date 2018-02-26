package com.gitlab.ctt.arq.sparql;

import com.gitlab.ctt.arq.core.BatchProcessor.BailException;
import com.gitlab.ctt.arq.util.SparqlUtil;
import fj.data.Either;
import org.apache.jena.query.Query;
import org.apache.jena.sparql.core.Var;
import org.apache.jena.sparql.core.VarExprList;
import org.apache.jena.sparql.syntax.*;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import static com.gitlab.ctt.arq.sparql.SparqlAlgorithms.tripleVars;

public class SparqlQueries {
	public static void main(String[] args) {

		process("PREFIX : <p:> SELECT * WHERE { ?x :p ?y . FILTER EXISTS {?y :p ?z} }");
		process("PREFIX : <p:> ASK { ?x :p ?y . FILTER EXISTS {?y :p ?z} }");
	}

	private static void process(String sparqlStr) {
		Either<Exception, Query> maybeQuery = SparqlUtil.get().toQuery(sparqlStr);
		if (maybeQuery.isRight()) {
			Query query = maybeQuery.right().value();
			Optional<Boolean> projection = maybeProjection(query);
			System.out.println("projection: " + projection);
			Optional<Boolean> askProjection = maybeAskProjection(query);
			System.out.println("Askprojection: " + askProjection);
		} else {
			throw new RuntimeException(maybeQuery.left().value());
		}
	}

	
	public static Optional<Boolean> maybeProjection(Query query) {
		return maybeProjection(query, Query::isSelectType);
	}

	public static Optional<Boolean> maybeAskProjection(Query query) {
		return maybeProjection(query, Query::isAskType);
	}

	private static Optional<Boolean> maybeProjection(Query query,
			Predicate<Query> typeTest) {
		if (!typeTest.test(query)) {
			return Optional.of(false);
		}
		if (query.getGroupBy().size() > 0) {
			return Optional.empty();
		}
		VarExprList project = query.getProject();
		Set<Var> projVars = new HashSet<>(project.getVars());
		Set<Var> bodyVars = new HashSet<>();
		Set<Var> scopeVars = new HashSet<>();
		Element element = query.getQueryPattern();
		try {
			element.visit(new ElementVisitorBase() {
				@Override
				public void visit(ElementPathBlock el) {

					bodyVars.addAll(tripleVars(el));
					scopeVars.addAll(tripleVars(el));
				}

				@Override
				public void visit(ElementTriplesBlock el) {
					bodyVars.addAll(tripleVars(el));
					scopeVars.addAll(tripleVars(el));
				}

				@Override
				public void visit(ElementGroup el) {
					for (Element element : el.getElements()) {
						element.visit(this);
					}
				}

				@Override
				public void visit(ElementFilter el) {
					Set<Var> filterVars = SparqlAlgorithms.filterVars(el);
					bodyVars.addAll(filterVars);
				}

				@Override
				public void visit(ElementUnion el) {
					for (Element element : el.getElements()) {
						element.visit(this);
					}
				}

				@Override
				public void visit(ElementOptional el) {
					el.getOptionalElement().visit(this);
				}

				@Override
				public void visit(ElementNamedGraph el) {
					el.getElement().visit(this);
				}

				@Override
				public void visit(ElementSubQuery el) {
					throw new BailException("unsure");
				}

				@Override
				public void visit(ElementMinus el) {

					Set<Var> minusVars = tripleVars(el.getMinusElement());
					bodyVars.addAll(minusVars);
				}

				@Override
				public void visit(ElementBind el) {
					throw new BailException("unsure");
				}

				@Override
				public void visit(ElementAssign el) {
					throw new BailException("unsure");
				}

				@Override
				public void visit(ElementData el) {
					throw new BailException("unsure");
				}

				@Override
				public void visit(ElementService el) {

					el.getElement().visit(this);
				}
			});
		} catch (BailException ex) {
			return Optional.empty();
		}
		if (query.isQueryResultStar()) {

			return scopeVars.containsAll(bodyVars) ? Optional.of(false) : Optional.of(true);
		} else {
			return projVars.containsAll(bodyVars) ? Optional.of(false) : Optional.of(true);
		}
	}


















}
