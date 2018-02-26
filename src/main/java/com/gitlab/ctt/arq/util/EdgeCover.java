package com.gitlab.ctt.arq.util;

import com.gitlab.ctt.arq.sparql.SparqlGraph;
import com.gitlab.ctt.arq.utilx.Resources;
import fj.data.Either;
import org.apache.commons.math3.optim.PointValuePair;
import org.apache.commons.math3.optim.linear.*;
import org.apache.commons.math3.optim.nonlinear.scalar.GoalType;
import org.apache.jena.query.Query;
import org.apache.jena.sparql.syntax.Element;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;
import java.util.stream.Stream;


public class EdgeCover {
	public static void main(String[] args) {
		String sparqlStr = Resources.getResourceAsString("sample/misc/scrap.sparql");
		Either<Exception, Query> mQuery = SparqlUtil.get().toQuery(sparqlStr);
		if (mQuery.isRight()) {
			Element element = mQuery.right().value().getQueryPattern();
			System.out.println(edgeCoverNumber(element));
		} else {
			mQuery.left().value().printStackTrace(System.err);
		}
	}

	public static double edgeCoverNumber(Element element) {
		List<List<Object>> triples = new ArrayList<>();
		SparqlGraph.collectTriples(element, (o1, o2, p3) -> {
			List<Object> triple = Arrays.asList(o1, o2, p3);
			triples.add(triple);
		});
		return edgeCoverNumber(triples);
	}

	private static double edgeCoverNumber(Collection<List<Object>> triples) {
		Map<Object, Integer> obj2int = new LinkedHashMap<>();
		AtomicInteger counter = new AtomicInteger();
		for (List<Object> triple : triples) {
			for (Object obj : triple) {
				obj2int.computeIfAbsent(obj, k -> counter.getAndIncrement());
			}
		}

		int n = obj2int.keySet().size();
		List<LinearConstraint> constraintList = triples.stream().map(t -> {
			ArrayList<Double> list = new ArrayList<>(
				Stream.generate(() -> (0d)).limit(n).collect(Collectors.toList()));
			for (int i = 0; i < n; i++) {
				for (Object obj : t) {
					int index = obj2int.get(obj);
					list.set(index, 1d);
				}
			}
			return list.stream().mapToDouble(d -> d).toArray();
		}).map(da -> new LinearConstraint(da, Relationship.LEQ, 1d))
			.collect(Collectors.toList());
		LinearConstraint[] constraintsArray = constraintList.toArray(
			new LinearConstraint[constraintList.size()]);

		double[] coefficients = DoubleStream.generate(() -> 1d).limit(n).toArray();
		LinearObjectiveFunction function = new LinearObjectiveFunction(coefficients, 0d);
		LinearConstraintSet constraints = new LinearConstraintSet(
			constraintsArray
		);
		SimplexSolver simplex = new SimplexSolver();
		try {
			PointValuePair pair = simplex.optimize(
				function,
				constraints,
				new NonNegativeConstraint(true),
				PivotSelectionRule.BLAND,
				GoalType.MAXIMIZE
			);

			return pair.getValue();
		} catch (UnboundedSolutionException ignored) {
			return Double.NaN;
		}
	}









}
