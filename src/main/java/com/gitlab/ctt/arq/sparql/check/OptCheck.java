package com.gitlab.ctt.arq.sparql.check;

import com.gitlab.ctt.arq.sparql.check.PatternTree.*;
import com.gitlab.ctt.arq.util.SparqlUtil;
import com.gitlab.ctt.arq.utilx.Resources;
import fj.data.Either;
import org.apache.jena.query.Query;
import org.apache.jena.sparql.core.Var;
import org.apache.jena.sparql.syntax.Element;

import java.util.LinkedHashSet;
import java.util.Set;

import static com.gitlab.ctt.arq.sparql.check.PatternTree.*;


public class OptCheck {
	public static void main(String[] args) {
		String sparqlStr1 = Resources.getResourceAsString("sample/misc/scrap.sparql");
		Either<Exception, Query> mQuery = SparqlUtil.get().toQuery(sparqlStr1);
		if (mQuery.isRight()) {
			Query query = mQuery.right().value();
			Element element = query.getQueryPattern();
			Pattern p = rebuilt(element);
			Object triple = new DesignCheck().isWdPattern(p);
			System.out.println(triple);
			OptCheck check = OptCheck.check(element);
			System.out.println("badNesting: " + check.badNesting);
			System.out.println("badInterface: " + check.badInterface);
			System.out.println("wd: " + DesignCheck.isUwd(element));
		} else {
			throw new RuntimeException(mQuery.left().value());
		}
	}

	private Set<Var> vs;
	private boolean badInterface = false;
	private boolean badNesting = false;
	private boolean badAssert = false;

	private OptCheck(Set<Var> vs) {
		this.vs = vs;
	}

	public boolean isOk() {
		return !badAssert && !badNesting && !badInterface;
	}

	public boolean isBadInterface() {
		return badInterface;
	}

	public boolean isBadNesting() {
		return badNesting;
	}

	public static OptCheck check(Element element) {
		Pattern p = rebuilt(element);

		return check(p, true);
	}


	public static OptCheck check(Pattern p, boolean optOk) {
		OptCheck result = new OptCheck(new LinkedHashSet<>());
		if (p instanceof Opt2) {
			if (!optOk) {
				result.badNesting = true;
			}
			Opt2 p0 = (Opt2) p;
			OptCheck r1 = check(p0.p1, true);
			OptCheck r2 = check(p0.p2, false);
			result.badNesting = result.badNesting || r1.badNesting || r2.badNesting;
			result.badInterface = r1.badInterface || r2.badInterface;
			Set<Var> v1 = new LinkedHashSet<>(r1.vs);
			v1.retainAll(r2.vs);
			if (v1.size() > 1) {
				result.badInterface = true;
			}
			result.vs = r1.vs;
		} else if (p instanceof And2) {
			And2 p0 = (And2) p;
			OptCheck r1 = check(p0.p1, false);
			OptCheck r2 = check(p0.p2, false);
			result.badNesting = r1.badNesting || r2.badNesting;
			result.badInterface = r1.badInterface || r2.badInterface;
			Set<Var> v1 = new LinkedHashSet<>(r1.vs);
			v1.addAll(r2.vs);
			result.vs = v1;
		} else if (p instanceof Filter2) {
			Filter2 p0 = (Filter2) p;
			OptCheck r1 = check(p0.p1, false);



			Set<Var> vs = new LinkedHashSet<>(r1.vs);
			vs.addAll(p0.r2);
			result.vs = vs;
			result.badNesting = r1.badNesting;
			result.badInterface = r1.badInterface;
		} else if (p instanceof Basic1) {
			Basic1 p0 = (Basic1) p;
			result.vs = p0.v1;
		} else {
			result.badAssert = true;
		}
		return result;
	}
}
