package com.gitlab.ctt.arq.analysis.aspect.util;

import com.gitlab.ctt.arq.sparql.ElementDeepWalker;
import com.gitlab.ctt.arq.sparql.SparqlAlgorithms;
import com.gitlab.ctt.arq.util.SparqlUtil;
import com.gitlab.ctt.arq.utilx.Resources;
import fj.data.Either;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.apache.jena.query.Query;
import org.apache.jena.sparql.expr.*;
import org.apache.jena.sparql.syntax.*;

import java.util.Arrays;
import java.util.BitSet;
import java.util.List;

import static com.gitlab.ctt.arq.sparql.SparqlGraph.exprWalkerWalk;



public class FlagWalker {
	public static void main(String[] args) {
		String sparqlStr = Resources.getResourceAsString("sample/misc/scrap.sparql");
		Either<Exception, Query> mQuery = SparqlUtil.get().toQuery(sparqlStr);
		FlagWalker flagWalker = new FlagWalker();
		flagWalker.consume(mQuery.right().value().getQueryPattern());
		System.out.println(flagWalker.asLong());
	}

	public MutableBoolean and = new MutableBoolean();      
	public MutableBoolean union = new MutableBoolean();    
	public MutableBoolean optional = new MutableBoolean(); 
	public MutableBoolean filter = new MutableBoolean();   
	public MutableBoolean graph = new MutableBoolean();    
	public MutableBoolean subquery = new MutableBoolean();
	public MutableBoolean exists = new MutableBoolean();
	public MutableBoolean notExists = new MutableBoolean();
	public MutableBoolean service = new MutableBoolean();
	public MutableBoolean bind = new MutableBoolean();
	public MutableBoolean assign = new MutableBoolean();
	public MutableBoolean minus = new MutableBoolean();
	public MutableBoolean data = new MutableBoolean();
	public MutableBoolean dataset = new MutableBoolean();

	public static final int AND = 1;
	public static final int UNION = 2;
	public static final int OPTIONAL = 3;
	public static final int FILTER = 4;
	public static final int GRAPH = 5;
	public static final int SUBQUERY = 6;
	public static final int EXISTS = 7;
	public static final int NOTEXISTS = 8;
	public static final int SERVICE = 9;
	public static final int BIND = 10;
	public static final int ASSIGN = 11;
	public static final int MINUS = 12;
	public static final int DATA = 13;
	public static final int DATASET = 14;




	private List<MutableBoolean> flagList() {
		return Arrays.asList(
			and, union, optional, filter, graph, subquery,
			exists, notExists, service, bind, assign, minus, data, dataset);
	}

	public BitSet toBitSet() {
		BitSet bitSet = new BitSet();
		List<MutableBoolean> flags = flagList();
		for (int i = 0; i < flags.size(); i++) {
			MutableBoolean flag = flags.get(i);
			if (flag.isTrue()) {
				bitSet.set(i);
			}
		}
		return bitSet;
	}





	public long asLong() {
		BitSet bitSet = toBitSet();
		long[] longs = bitSet.toLongArray();
		return longs.length == 1 ? longs[0] : 0L;
	}

	public void consume(Element element) {
		ElementDeepWalker.walk(element, new ElementVisitorBase() {
			@Override
			public void visit(ElementPathBlock el) {
				if (el.getPattern().size() > 1) {
					and.setTrue();
				}
			}


			@Override
			public void visit(ElementTriplesBlock el) {
				if (el.getPattern().size() > 1) {
					and.setTrue();
				}
			}

			@Override
			public void visit(ElementGroup el) {
				if (SparqlAlgorithms.isConjunctive(el)) {
					and.setTrue();
				}
			}

			@Override
			public void visit(ElementOptional el) {
				optional.setTrue();
			}

			@Override
			public void visit(ElementFilter el) {
				filter.setTrue();
				Expr expr = el.getExpr();
				ExprVisitorBase visitor = new ExprVisitorBase() {
					@Override
					public void visit(ExprFunctionOp func) {
						if (func instanceof E_Exists) {
							exists.setTrue();
						}
						if (func instanceof E_NotExists) {
							notExists.setTrue();
						}
					}
				};
				exprWalkerWalk(visitor, expr);
			}

			@Override
			public void visit(ElementUnion el) {
				union.setTrue();
			}

			@Override
			public void visit(ElementNamedGraph el) {
				graph.setTrue();
			}

			@Override
			public void visit(ElementSubQuery el) {
				subquery.setTrue();
			}

			@Override
			public void visit(ElementExists el) {
				exists.setTrue();
			}

			@Override
			public void visit(ElementNotExists el) {
				notExists.setTrue();
			}

			@Override
			public void visit(ElementService el) {
				service.setTrue();
			}

			@Override
			public void visit(ElementBind el) {
				bind.setTrue();
			}

			@Override
			public void visit(ElementAssign el) {
				assign.setTrue();
			}

			@Override
			public void visit(ElementMinus el) {
				minus.setTrue();
			}

			@Override
			public void visit(ElementData el) {
				data.setTrue();
			}

			@Override
			public void visit(ElementDataset el) {
				dataset.setTrue();
			}
		});
	}

	public void flush() {

	}
}
