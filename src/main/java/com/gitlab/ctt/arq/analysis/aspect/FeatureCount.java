package com.gitlab.ctt.arq.analysis.aspect;

import com.gitlab.ctt.arq.analysis.aspect.util.FlagWalker;
import com.gitlab.ctt.arq.sparql.SparqlProperties;
import com.gitlab.ctt.arq.sparql.SparqlQueries;
import org.apache.jena.query.Query;
import org.apache.jena.sparql.expr.ExprAggregator;
import org.apache.jena.sparql.expr.aggregate.*;
import org.apache.jena.sparql.syntax.Element;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

class FeatureCount {
	private AtomicLong projectionCounter = new AtomicLong();
	private AtomicLong projectionUnsureCounter = new AtomicLong();
	private AtomicLong askProjectionCounter = new AtomicLong();
	private AtomicLong askProjectionUnsureCounter = new AtomicLong();
	private AtomicLong projectionCpfCounter = new AtomicLong();

	private AtomicLong distinctCounter = new AtomicLong();
	private AtomicLong limitCounter = new AtomicLong();
	private AtomicLong selectCounter = new AtomicLong();
	private AtomicLong constructCounter = new AtomicLong();
	private AtomicLong askCounter = new AtomicLong();
	private AtomicLong describeCounter = new AtomicLong();

	private AtomicLong countCounter = new AtomicLong();
	private AtomicLong sumCounter = new AtomicLong();
	private AtomicLong avgCounter = new AtomicLong();
	private AtomicLong minCounter = new AtomicLong();
	private AtomicLong maxCounter = new AtomicLong();

	private AtomicLong andCounter = new AtomicLong();
	private AtomicLong unionCounter = new AtomicLong();
	private AtomicLong optionalCounter = new AtomicLong();
	private AtomicLong filterCounter = new AtomicLong();
	private AtomicLong graphCounter = new AtomicLong();

	private AtomicLong subqueryCounter = new AtomicLong();
	private AtomicLong existsCounter = new AtomicLong();
	private AtomicLong notExistsCounter = new AtomicLong();

	private AtomicLong serviceCounter = new AtomicLong();
	private AtomicLong bindCounter = new AtomicLong();
	private AtomicLong assignCounter = new AtomicLong();
	private AtomicLong minusCounter = new AtomicLong();
	private AtomicLong dataCounter = new AtomicLong();
	private AtomicLong datasetCounter = new AtomicLong();

	public void processQuery(Query query) {
		handleQuery(query);
		Element element = query.getQueryPattern();
		if (element != null) {
			handleElement(element);
		}
		handleAggregator(query);
	}

	private void handleQuery(Query query) {
		specialTests(query);
		switch (query.getQueryType()) {
			case Query.QueryTypeSelect:
				selectCounter.getAndIncrement();
				break;
			case Query.QueryTypeConstruct:
				constructCounter.getAndIncrement();
				break;
			case Query.QueryTypeAsk:
				askCounter.getAndIncrement();
				break;
			case Query.QueryTypeDescribe:
				describeCounter.getAndIncrement();
				break;
		}
		boolean distinct = query.isDistinct();
		if (distinct) {
			distinctCounter.getAndIncrement();
		}
		boolean limit = query.hasLimit();
		if (limit) {
			limitCounter.getAndIncrement();
		}
	}

	private void specialTests(Query query) {
		Optional<Boolean> projection = SparqlQueries.maybeProjection(query);
		if (!projection.isPresent()) {
			projectionUnsureCounter.getAndIncrement();
		} else if (projection.orElse(false)) {
			projectionCounter.getAndIncrement();
			Element element = query.getQueryPattern();
			if (element != null) {
				boolean cpf = SparqlProperties.get().isCpf(element);
				if (cpf) {
					projectionCpfCounter.getAndIncrement();
				}
			}
		}
		Optional<Boolean> askProjection = SparqlQueries.maybeAskProjection(query);
		if (!askProjection.isPresent()) {
			askProjectionUnsureCounter.getAndIncrement();
		} else if (askProjection.orElse(false)) {
			askProjectionCounter.getAndIncrement();
		}
	}

	private void handleElement(Element element) {
		FlagWalker flagWalker = new FlagWalker() {
			@Override
			public void flush() {
				if (and.isTrue()) {
					andCounter.getAndIncrement();
				}
				if (union.isTrue()) {
					unionCounter.getAndIncrement();
				}
				if (optional.isTrue()) {
					optionalCounter.getAndIncrement();
				}
				if (filter.isTrue()) {
					filterCounter.getAndIncrement();
				}
				if (graph.isTrue()) {
					graphCounter.getAndIncrement();
				}
				if (subquery.isTrue()) {
					subqueryCounter.getAndIncrement();
				}
				if (exists.isTrue()) {
					existsCounter.getAndIncrement();
				}
				if (notExists.isTrue()) {
					notExistsCounter.getAndIncrement();
				}
				if (service.isTrue()) {
					serviceCounter.getAndIncrement();
				}
				if (bind.isTrue()) {
					bindCounter.getAndIncrement();
				}
				if (assign.isTrue()) {
					assignCounter.getAndIncrement();
				}
				if (minus.isTrue()) {
					minusCounter.getAndIncrement();
				}
				if (data.isTrue()) {
					dataCounter.getAndIncrement();
				}
				if (dataset.isTrue()) {
					datasetCounter.getAndIncrement();
				}
			}
		};
		flagWalker.consume(element);
		flagWalker.flush();
	}

	private void handleAggregator(Query query) {

		boolean count = false;
		boolean sum = false;
		boolean avg = false;
		boolean min = false;
		boolean max = false;
		List<ExprAggregator> aggregators = query.getAggregators();
		for (ExprAggregator exprAggregator : aggregators) {
			Aggregator aggregator = exprAggregator.getAggregator();
			if (aggregator instanceof AggCount) {
				count = true;
			}
			if (aggregator instanceof AggSum) {
				sum = true;
			}
			if (aggregator instanceof AggAvg) {
				avg = true;
			}
			if (aggregator instanceof AggMin) {
				min = true;
			}
			if (aggregator instanceof AggMax) {
				max = true;
			}
		}
		if (count) {
			countCounter.getAndIncrement();
		}
		if (sum) {
			sumCounter.getAndIncrement();
		}
		if (avg) {
			avgCounter.getAndIncrement();
		}
		if (min) {
			minCounter.getAndIncrement();
		}
		if (max) {
			maxCounter.getAndIncrement();
		}
	}

	public void mapCounters(Map<String, Long> map) {
		map.put("projection", projectionCounter.get());
		map.put("projectionUnsure", projectionUnsureCounter.get());
		map.put("askProjection", askProjectionCounter.get());
		map.put("askProjectionUnsure", askProjectionUnsureCounter.get());
		map.put("projectionCpf", projectionCpfCounter.get());
		map.put("select", selectCounter.get());
		map.put("construct", constructCounter.get());
		map.put("ask", askCounter.get());
		map.put("describe", describeCounter.get());
		map.put("distinct", distinctCounter.get());
		map.put("limit", limitCounter.get());
		map.put("count", countCounter.get());
		map.put("sum", sumCounter.get());
		map.put("avg", avgCounter.get());
		map.put("min", minCounter.get());
		map.put("max", maxCounter.get());

		map.put("and", andCounter.get());
		map.put("union", unionCounter.get());
		map.put("optional", optionalCounter.get());
		map.put("filter", filterCounter.get());
		map.put("graph", graphCounter.get());
		map.put("subquery", subqueryCounter.get());
		map.put("exists", existsCounter.get());
		map.put("notExists", notExistsCounter.get());
		map.put("service", serviceCounter.get());
		map.put("bind", bindCounter.get());
		map.put("assign", assignCounter.get());
		map.put("minus", minusCounter.get());
		map.put("data", dataCounter.get());
		map.put("dataset", datasetCounter.get());
	}
}
