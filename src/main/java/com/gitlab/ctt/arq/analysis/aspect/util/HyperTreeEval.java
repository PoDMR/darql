package com.gitlab.ctt.arq.analysis.aspect.util;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.jena.query.Query;

import java.util.List;

public interface HyperTreeEval {
	Pair<Integer, Integer> hyperTreeCheck(Query query);
	int hyperTreeWidth(Query query, boolean skipPredicates, boolean skipConstants);






	HyperTreeEval DETKDECOMP_EVAL = new HyperTreeEval() {
		@Override
		public final Pair<Integer, Integer> hyperTreeCheck(Query query) {
			return HyperTreeUtil.hyperTreeCheck(query);
		}

		@Override
		public final int hyperTreeWidth(Query query, boolean skipPredicates, boolean skipConstants) {
			return JdrasilTWTool.hyperTreeWidth(query, skipPredicates, skipConstants);

		}












	};

	HyperTreeEval HTD_EVAL = new HyperTreeEval() {
		@Override
		public final Pair<Integer, Integer> hyperTreeCheck(Query query) {
			return HtdTool.hyperTreeCheck(query);
		}

		@Override
		public final int hyperTreeWidth(Query query, boolean skipPredicates, boolean skipConstants) {
			return HtdTool.hyperTreeWidth(query, skipPredicates, skipConstants);
		}












	};

	static HyperTreeEval get() {
		return DETKDECOMP_EVAL;
	}

	@SafeVarargs
	static Pair<Integer, Integer> hyperTreeCheck(Query query, List<String>... hes) {
		return HyperTreeUtil.hyperTreeCheck(query, hes);
	}
}
