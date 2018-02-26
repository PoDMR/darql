package com.gitlab.ctt.arq.sparql.check;

import com.gitlab.ctt.arq.sparql.SparqlAlgorithms;
import com.gitlab.ctt.arq.sparql.check.PatternTree.*;
import com.gitlab.ctt.arq.util.SparqlUtil;
import com.gitlab.ctt.arq.utilx.Resources;
import fj.data.Either;
import org.apache.jena.ext.com.google.common.collect.Sets;
import org.apache.jena.query.Query;
import org.apache.jena.sparql.core.Var;
import org.apache.jena.sparql.syntax.Element;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;

import static com.gitlab.ctt.arq.sparql.check.PatternTree.*;


public class DesignCheck {
	public static void main(String[] args) {
		Resources.getLocalProperty("null");  






		String sparqlStr6 = Resources.getResourceAsString("sample/wd/wd_sample2.sparql");
		isWwdSparqlStr(sparqlStr6);

	}

	private static void testExamples() {
		System.out.println("----");
		String sparqlStr1 = Resources.getResourceAsString("sample/wwd/wwd_s1.sparql");
		String sparqlStr2 = Resources.getResourceAsString("sample/wwd/wwd_s2.sparql");
		String sparqlStr3 = Resources.getResourceAsString("sample/wwd/wwd_s3.sparql");
		String sparqlStr4 = Resources.getResourceAsString("sample/wwd/wwd_s4.sparql");


		isWwdSparqlStr(sparqlStr1);  
		isWwdSparqlStr(sparqlStr2);  
		isWwdSparqlStr(sparqlStr3);  
		isWwdSparqlStr(sparqlStr4);  

		System.out.println("----");
	}

	private static boolean isWwdSparqlStr(String sparqlStr1) {
		Either<Exception, Query> mQuery = SparqlUtil.get().toQuery(sparqlStr1);
		if (mQuery.isRight()) {
			Query query = mQuery.right().value();
			Element element = query.getQueryPattern();
			boolean uwd = isUwd(element);
			boolean uwwd = isUwwd(element);
			System.out.println(String.format("%s, %s", uwd, uwwd));
			return uwwd;
		} else {
			throw new RuntimeException(mQuery.left().value());
		}
	}



	private static class ResultTriple {
		private boolean wd;
		private Set<Var> vs;
		private Set<Var> ws;  

		private ResultTriple(boolean wd, Set<Var> vs, Set<Var> ws) {
			this.wd = wd;
			this.vs = vs;
			this.ws = ws;
		}
	}

	private static final ResultTriple FAIL_TRIPLE = new ResultTriple(
		false,
		Collections.emptySet(),
		Collections.emptySet()
	);





	private static final DesignCheck WWD_INSTANCE = new DesignCheck()  {
		@Override
		protected ResultTriple handleOpt(Opt2 p0) {
			return this.isRightWdBinary(p0.p1, p0.p2);  
		}

		@Override
		protected ResultTriple handleUnion(Union2 p0) {
			return this.isNeutralWdBinary(p0.p1, p0.p2);  
		}
	};

	private static final DesignCheck OWD_INSTANCE = new DesignCheck() {
		@Override
		protected ResultTriple handleOpt(Opt2 p0) {
			return this.isLeftRightWdBinary(p0.p1, p0.p2);
		}

		@Override
		protected ResultTriple handleUnion(Union2 p0) {
			return this.isNeutralWdBinary(p0.p1, p0.p2);
		}
	};

	private static final DesignCheck UWD_INSTANCE = new DesignCheck() {
		@Override
		protected ResultTriple handleOpt(Opt2 p0) {
			return this.isNeutralWdBinary(p0.p1, p0.p2);
		}

		@Override
		protected ResultTriple handleUnion(Union2 p0) {
			return this.isLeftRightWdBinary(p0.p1, p0.p2);
		}
	};



	public static Optional<Function<Predicate<Element>, Boolean>> unionDecomposition(Element element) {
		List<Element> unionArgs = new ArrayList<>();
		SparqlAlgorithms.collectUnionArgs(unionArgs, element);
		boolean onlyTopLevelUnion = unionArgs.stream().allMatch(
			SparqlAlgorithms::isUnionFree);
		if (onlyTopLevelUnion) {
			return Optional.of(t -> unionArgs.stream().allMatch(t));
		} else {
			return Optional.empty();
		}
	}

	
	@Deprecated
	public static boolean isPwwd(Element element) {
		return WWD_INSTANCE.isPwwdInst(element);
	}

	
	public static boolean isUwd(Element element) {
		Pattern p = rebuilt(element);
		if (p != null) {
			List<Pattern> parts = associativeUnionArgs(p);
			return parts.stream().allMatch(p0 ->
				isUnionFree(p0) && OWD_INSTANCE.isWd(p0));
		} else {
			return false;
		}
	}

	
	public static boolean isUwwd(Element element) {
		Pattern p = rebuilt(element);
		if (p != null) {
			List<Pattern> parts = associativeUnionArgs(p);
			return parts.stream().allMatch(p0 ->
				isUnionFree(p0) && WWD_INSTANCE.isPwwd(p0));
		} else {
			return false;
		}
	}

	
	public static boolean isOWD(Element element) {
		Pattern p = rebuilt(element);
		if (p != null) {
			return OWD_INSTANCE.isWd(p);
		} else {
			return false;
		}
	}

	
	public static boolean isUNF(Element element) {
		Pattern p = rebuilt(element);
		if (p != null) {
			List<Pattern> parts = associativeUnionArgs(p);
			return parts.stream().allMatch(DesignCheck::isUnionFree);
		} else {
			return false;
		}
	}

	protected static List<Pattern> associativeUnionArgs(Pattern pattern) {
		List<Pattern> list = new ArrayList<>();
		collectAssociativeUnionArgs(list, pattern);
		return list;
	}

	protected static void collectAssociativeUnionArgs(List<Pattern> list, Pattern pattern) {
		if (pattern instanceof Union2) {
			Union2 p0 = (Union2) pattern;
			collectAssociativeUnionArgs(list, p0.p1);
			collectAssociativeUnionArgs(list, p0.p2);
		} else {
			list.add(pattern);
		}
	}

	protected static boolean isUnionFree(Pattern p) {
		if (p instanceof And2) {
			And2 p0 = (And2) p;
			return isUnionFree(p0.p1) && isUnionFree(p0.p2);
		} else if (p instanceof Opt2) {
			Opt2 p0 = (Opt2) p;
			return isUnionFree(p0.p1) && isUnionFree(p0.p2);
		} else if (p instanceof Union2) {
			return false;
		} else if (p instanceof Filter2) {
			Filter2 p0 = (Filter2) p;
			return isUnionFree(p0.p1);
		}
		return true;  
	}

	
	public static boolean isUWD(Element element) {
		Pattern p = rebuilt(element);
		if (p != null) {
			return UWD_INSTANCE.isWd(p);
		} else {
			return false;
		}
	}



	@Deprecated
	public boolean isPwwdInst(Element element) {
		Pattern p = rebuilt(element);
		if (p != null) {
			p = rmTopLevelFilters(p);  
			return isWd(p);
		} else {
			return false;
		}
	}

	public boolean isPwwd(Pattern p) {
		p = rmTopLevelFilters(p);
		return isWd(p);
	}


	protected static Pattern rmTopLevelFilters(Pattern p) {
		if (p instanceof And2) {
			And2 p0 = (And2) p;
			Pattern p1 = rmTopLevelFilters(p0.p1);
			Pattern p2 = rmTopLevelFilters(p0.p2);
			return  new And2(p1, p2);
		} else if (p instanceof Opt2) {
			Opt2 p0 = (Opt2) p;
			Pattern p1 = rmTopLevelFilters(p0.p1);
			return  new Opt2(p1, p0.p2);
		} else if (p instanceof Filter2) {
			Filter2 p0 = (Filter2) p;
			return rmTopLevelFilters(p0.p1);
		} else {  
			return p;
		}
	}

	protected boolean isWd(Pattern p) {
		return isWdPattern(p).wd;
	}

	protected ResultTriple isWdPattern(Pattern p) {
		if (p instanceof And2) {
			And2 p0 = (And2) p;
			return handleAnd(p0);
		} else if (p instanceof Opt2) {  
			Opt2 p0 = (Opt2) p;
			return handleOpt(p0);
		} else if (p instanceof Union2) {
			Union2 p0 = (Union2) p;
			return handleUnion(p0);
		} else if (p instanceof Filter2) {
			Filter2 p0 = (Filter2) p;
			return isWdFilter(p0.p1, p0.r2);
		} else if (p instanceof Basic1) {  
			Basic1 p0 = (Basic1) p;
			return createResultTriple(true, p0.v1, Collections.emptySet());
		}
		return fail();
	}

	protected ResultTriple handleAnd(And2 p0) {
		return isWdBinary(p0.p1, p0.p2);
	}

	protected ResultTriple handleOpt(Opt2 p0) {
		return isRightWdBinary(p0.p1, p0.p2);
	}

	protected ResultTriple handleUnion(Union2 p0) {
		return isWdBinary(p0.p1, p0.p2);
	}

	protected ResultTriple isWdBinary(Pattern p1, Pattern p2) {
		ResultTriple t1 = isWdPattern(p1);
		ResultTriple t2 = isWdPattern(p2);
		boolean test = Sets.union(
			Sets.intersection(t1.ws, Sets.union(t2.vs, t2.ws)),
			Sets.intersection(t2.ws, Sets.union(t1.vs, t1.ws))
		).isEmpty();
		if (test) {
			return createResultTriple(t1.wd && t2.wd,
				Sets.union(t1.vs, t2.vs),
				Sets.union(t1.ws, t2.ws)
			);
		} else {
			return fail();
		}
	}

	protected ResultTriple isRightWdBinary(Pattern p1, Pattern p2) {
		ResultTriple t1 = isWdPattern(p1);
		ResultTriple t2 = isWdPattern(p2);
		if (Sets.intersection(t2.ws, Sets.union(t1.vs, t1.ws)).isEmpty()) {
			return createResultTriple(t1.wd && t2.wd,
				t1.vs,
				Sets.union(
					Sets.difference(t2.vs, t1.vs),
					Sets.union(t1.ws, t2.ws)
				)
			);
		} else {
			return fail();
		}
	}

	protected ResultTriple isLeftRightWdBinary(Pattern p1, Pattern p2) {
		ResultTriple t1 = isWdPattern(p1);
		ResultTriple t2 = isWdPattern(p2);
		boolean test = Sets.union(
			Sets.intersection(t1.ws, Sets.union(t2.vs, t2.ws)),
			Sets.intersection(t2.ws, Sets.union(t1.vs, t1.ws))
		).isEmpty();
		if (test) {
			return createResultTriple(t1.wd && t2.wd,
				t1.vs,
				Sets.union(
					Sets.difference(t2.vs, t1.vs),
					Sets.union(t1.ws, t2.ws)
				)
			);
		} else {
			return fail();
		}
	}

	protected ResultTriple isNeutralWdBinary(Pattern p1, Pattern p2) {
		ResultTriple t1 = isWdPattern(p1);
		ResultTriple t2 = isWdPattern(p2);
		return createResultTriple(t1.wd && t2.wd,
			Sets.union(t1.vs, t2.vs),
			Sets.union(t1.ws, t2.ws)
		);
	}

	private ResultTriple isWdFilter(Pattern p, Set<Var> rVars) {
		ResultTriple t = isWdPattern(p);
		if (Sets.intersection(t.ws, rVars).isEmpty()) {
			return createResultTriple(t.wd, t.vs, t.ws);
		} else {
			return fail();
		}
	}



	private ResultTriple createResultTriple(boolean wd, Set<Var> vs, Set<Var> ws) {
		if (!wd) {
			onFail();
		}
		return new ResultTriple(wd, vs, ws);
	}

	private ResultTriple fail() {
		onFail();
		return FAIL_TRIPLE;
	}

	protected void onFail() {

	}
}
