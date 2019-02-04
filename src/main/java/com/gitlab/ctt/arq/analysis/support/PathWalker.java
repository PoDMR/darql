package com.gitlab.ctt.arq.analysis.support;

import com.gitlab.ctt.arq.sparql.ElementDeepWalker;
import com.gitlab.ctt.arq.util.QueryFixer;
import com.gitlab.ctt.arq.util.SparqlUtil;
import com.gitlab.ctt.arq.utilx.Resources;
import fj.data.Either;
import org.apache.jena.query.Query;
import org.apache.jena.sparql.path.*;
import org.apache.jena.sparql.syntax.Element;
import org.apache.jena.sparql.syntax.ElementPathBlock;
import org.apache.jena.sparql.syntax.ElementVisitorBase;

import java.util.concurrent.atomic.LongAdder;

public class PathWalker implements PathVisitor {
	public static void main(String[] args) {
		String queryStr = Resources.getResourceAsString("sample/misc/scrap.sparql");
		String queryStr2 = QueryFixer.get().fix(queryStr);
		Either<Exception, Query> maybeQuery = SparqlUtil.get().toQuery(queryStr2);
		Query query = maybeQuery.right().value();
		System.out.println(PathWalker.symbolCount(query.getQueryPattern()));
	}

	public static long symbolCount(Element element) {
		LongAdder longAdder = new LongAdder();
		PathVisitor pathVisitor = new PathWalker() {
			@Override
			public void accept(Path path) {
				longAdder.increment();
			}
		};

		ElementDeepWalker.walkWithService(element, new ElementVisitorBase() {
			@Override
			public void visit(ElementPathBlock el) {
				el.patternElts().forEachRemaining(triplePath -> {
					Path path = triplePath.getPath();
					long beforeSum = longAdder.sum();
					if (path != null) {
						path.visit(pathVisitor);
						long afterSum = longAdder.sum();
						if (afterSum - beforeSum <= 0) {
							longAdder.increment();
						}
					} else {
						longAdder.increment();
					}
				});
			}
		});
		return longAdder.sum();
	}

	public void accept(Path path) {
	}

	@Override
	public void visit(P_Link pathNode) {
		accept(pathNode);
	}

	@Override
	public void visit(P_ReverseLink pathNode) {
		accept(pathNode);
	}

	@Override
	public void visit(P_NegPropSet pathNotOneOf) {
		for (P_Path0 node : pathNotOneOf.getNodes()) {
			node.visit(this);
		}
	}

	@Override
	public void visit(P_Inverse inversePath) {
		inversePath.getSubPath().visit(this);
	}

	@Override
	public void visit(P_Mod pathMod) {
		pathMod.getSubPath().visit(this);
	}

	@Override
	public void visit(P_FixedLength pFixedLength) {
		pFixedLength.getSubPath().visit(this);
	}

	@Override
	public void visit(P_Distinct pathDistinct) {
		pathDistinct.getSubPath().visit(this);
	}

	@Override
	public void visit(P_Multi pathMulti) {
		pathMulti.getSubPath().visit(this);
	}

	@Override
	public void visit(P_Shortest pathShortest) {
		pathShortest.getSubPath().visit(this);
	}

	@Override
	public void visit(P_ZeroOrOne path) {
		path.getSubPath().visit(this);
	}

	@Override
	public void visit(P_ZeroOrMore1 path) {
		path.getSubPath().visit(this);
	}

	@Override
	public void visit(P_ZeroOrMoreN path) {
		path.getSubPath().visit(this);
	}

	@Override
	public void visit(P_OneOrMore1 path) {
		path.getSubPath().visit(this);
	}

	@Override
	public void visit(P_OneOrMoreN path) {
		path.getSubPath().visit(this);
	}

	@Override
	public void visit(P_Alt pathAlt) {
		pathAlt.getLeft().visit(this);
		pathAlt.getRight().visit(this);
	}

	@Override
	public void visit(P_Seq pathSeq) {
		pathSeq.getLeft().visit(this);
		pathSeq.getRight().visit(this);
	}
}
