package com.gitlab.ctt.arq.analysis.aspect.db;

import com.gitlab.ctt.arq.analysis.aspect.HyperTreeUtil;
import com.gitlab.ctt.arq.analysis.aspect.StructureCount;
import com.gitlab.ctt.arq.analysis.aspect.util.FlagWalker;
import com.gitlab.ctt.arq.sparql.*;
import com.gitlab.ctt.arq.sparql.check.DesignCheck;
import com.gitlab.ctt.arq.sparql.check.OptCheck;
import com.gitlab.ctt.arq.sparql.check.OptCheck2;
import com.gitlab.ctt.arq.util.EdgeCover;
import com.gitlab.ctt.arq.util.GraphShape;
import com.gitlab.ctt.arq.util.SeriesParallel;
import com.gitlab.ctt.arq.util.SparqlUtil;
import fj.data.Either;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.jena.query.Query;
import org.apache.jena.sparql.core.TriplePath;
import org.apache.jena.sparql.expr.ExprAggregator;
import org.apache.jena.sparql.expr.aggregate.*;
import org.apache.jena.sparql.syntax.Element;
import org.jgrapht.DirectedGraph;
import org.jgrapht.graph.DefaultEdge;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class RecordProcessor {
	public static void main(String[] args) {
		QueryRecord record = new QueryRecord();
		record.queryStr = "SELECT * WHERE { ?x a ?y }";
		processFull(record);
		System.out.println(record);
	}

	public static void processFull(QueryRecord record) {
		Either<Exception, Query> maybeQuery = record.maybeQuery;
		if (record.maybeQuery == null) {

			maybeQuery = SparqlUtil.get().toQuery(record.queryStr);
		}

		if (maybeQuery.isRight()) {
			Query query = maybeQuery.right().value();
			withQuery(record, query);
		} else {
			record.parseError = true;
		}
	}

	private static void withQuery(QueryRecord record, Query query) {
		record.parseError = false;
		Element element = query.getQueryPattern();
		if (element != null) {
			analyzeQuery(record, query);

			withBody(record, element, query);
		}



	}

	private static void analyzeQuery(QueryRecord record, Query query) {
		record.select = query.isSelectType();
		record.ask = query.isAskType();
		record.construct = query.isConstructType();
		record.describe = query.isDescribeType();

		record.distinct = query.isDistinct();
		record.limit = query.hasLimit();

		Optional<Boolean> projection = SparqlQueries.maybeProjection(query);
		if (!projection.isPresent()) {
			record.projectionUnsure = true;
		} else if (projection.orElse(false)) {
			record.projection = true;
		} else {
			record.projection = false;
			record.projectionUnsure = false;
		}
		Optional<Boolean> askProjection = SparqlQueries.maybeAskProjection(query);
		if (!askProjection.isPresent()) {
			record.askProjectionUnsure = true;
		} else if (askProjection.orElse(false)) {
			record.askProjection = true;
		} else {
			record.askProjection = false;
			record.askProjectionUnsure = false;
		}

		List<ExprAggregator> aggregators = query.getAggregators();
		record.count = record.sum = record.avg = record.min = record.max = false;
		for (ExprAggregator exprAggregator : aggregators) {
			Aggregator aggregator = exprAggregator.getAggregator();
			record.count = (aggregator instanceof AggCount);
			record.sum = (aggregator instanceof AggSum);
			record.avg = (aggregator instanceof AggAvg);
			record.min = (aggregator instanceof AggMin);
			record.max = (aggregator instanceof AggMax);
		}
	}

	private static void withBody(QueryRecord record, Element element, Query query) {
		record.regex = SparqlProperties.get().hasPath(element);
		record.teePredicate = SparqlGraph.hasTeePredicate(element);
		record.var_predicate = SparqlGraph.hasVarPredicate(element);

		analyzeOperators(record, element);

		if (!record.regex && !record.teePredicate) {
			record.wb = SparqlProperties.get().isWellBehaved(element);
			record.uwd = DesignCheck.isUwd(element);
			record.uwwd = DesignCheck.isUwwd(element);

			if (record.afou && record.optional) {
				Element unionNormalized = SparqlTransducer.get().unionNormalize(element);
				record.uwdComp = DesignCheck.unionDecomposition(
					unionNormalized).map(x -> x.apply(DesignCheck::isUwd)).orElse(false);
				record.uwwdComp = DesignCheck.unionDecomposition(
					unionNormalized).map(x -> x.apply(DesignCheck::isUwwd)).orElse(false);
			}

			if (record.afo) {
				record.cq = !record.filter && !record.optional;
				analyzeShapes(record, element, query);
			}
		}

		Set<TriplePath> tps = SparqlAlgorithms.collectTriples(element);
		record.tripleCount = tps.size();
	}

	private static void analyzeOperators(QueryRecord record, Element element) {
		FlagWalker flagWalker = new FlagWalker();
		flagWalker.consume(element);
		record.and = flagWalker.and.isTrue();
		record.filter = flagWalker.filter.isTrue();
		record.optional = flagWalker.optional.isTrue();
		record.union = flagWalker.union.isTrue();
		record.graph = flagWalker.graph.isTrue();
		record.subquery = flagWalker.subquery.isTrue();  
		record.exists = flagWalker.exists.isTrue();
		record.notExists = flagWalker.notExists.isTrue();
		record.service = flagWalker.service.isTrue();
		record.bind = flagWalker.bind.isTrue();
		record.assign = flagWalker.assign.isTrue();
		record.minus = flagWalker.minus.isTrue();
		record.data = flagWalker.data.isTrue();
		record.dataset = flagWalker.dataset.isTrue();

		long flagLong = flagWalker.asLong();
		record.afo = ((flagLong & ~13L) == 0);
		record.afou = ((flagLong & ~0b1111) == 0);

		Set<String> keywords = new HashSet<>();
		StructureCount.keywordAnalyze(record.queryStr, keywords::add);
		record.HAVING = keywords.contains("HAVING");
		record.GROUP__BY = keywords.contains("GROUP BY");
		record.ORDER__BY = keywords.contains("ORDER BY");
		record.OFFSET = keywords.contains("OFFSET");
		record.VALUES = keywords.contains("VALUES");
		record.SAMPLE = keywords.contains("SAMPLE");
		record.GROUP_CONCAT = keywords.contains("GROUP_CONCAT");
	}

	private static void analyzeShapes(QueryRecord record, Element element, Query query) {
		DirectedGraph<Object, DefaultEdge> graph = SparqlGraph.graphFromQuery(element);
		if (graph != null) {
			handleGraph(record, graph, element, query);
		} else {
			record.bad_filter = true;
		}
	}

	private static void handleGraph(QueryRecord record,
			DirectedGraph<Object, DefaultEdge> graph, Element element, Query query) {
		record.bad_filter = false;

		if (record.optional) {
			OptCheck optCheck = OptCheck.check(element);
			record.opt_bad_nesting = optCheck.isBadNesting();
			record.opt_bad_interface = optCheck.isBadInterface();
			if (record.uwd && !record.opt_bad_interface) {
				record.wdpt = OptCheck2.wdpt(element);
				record.cq_fo = true;
			}
		} else {
			record.cq_fo = true;
			record.cq_f = true;
		}

		if (!record.var_predicate && (record.wdpt == null || record.wdpt)) {
			record.chainSet = GraphShape.isChainSetU(graph);
			record.star = GraphShape.isStarU(graph);
			record.circle = GraphShape.isCircleU(graph);
			record.utree = GraphShape.isTreeU(graph);
			record.uforest = GraphShape.isForestU(graph);
			record.cycletree = GraphShape.isCycleTreeU(graph);
			record.bicycle = GraphShape.isBicycleUSG(graph);
			record.flower = SeriesParallel.isFlower(graph, false);
			record.flowerSet = SeriesParallel.isGarden(graph, false);
			record.spFlower = SeriesParallel.isFlower(graph, true);
			record.spFlowerSet = SeriesParallel.isGarden(graph, true);

			record.shapeless = !record.chainSet &&
				!record.star &&
				!record.circle &&
				!record.utree &&
				!record.uforest &&
				!record.cycletree &&
				!record.bicycle &&
				!record.flower &&
				!record.flowerSet &&
				!record.spFlower &&
				!record.spFlowerSet;
		}




		if (record.var_predicate) {

			if (record.shapeless) {
				Pair<Integer, Integer> widthXnodeCount = HyperTreeUtil.hyperTreeCheck(query);
				int hyperTreeWidth = widthXnodeCount.getLeft();

				if (hyperTreeWidth > 0) {
					record.hypertreeWidth = hyperTreeWidth;
				}
			}
		}

		record.edgeCover = EdgeCover.edgeCoverNumber(element);
	}
}
