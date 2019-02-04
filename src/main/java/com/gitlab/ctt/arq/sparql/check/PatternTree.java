package com.gitlab.ctt.arq.sparql.check;

import com.gitlab.ctt.arq.sparql.SparqlAlgorithms;
import org.apache.jena.sparql.core.Var;
import org.apache.jena.sparql.syntax.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class PatternTree {
	public static abstract class Pattern {
	}

	public static class And2 extends Pattern {
		public Pattern p1;
		public Pattern p2;

		public And2(Pattern p1, Pattern p2) {
			this.p1 = p1;
			this.p2 = p2;
		}

		@Override
		public String toString() {
			return String.format("{%s . %s}", Objects.toString(p1), Objects.toString(p2));
		}
	}

	public static class Opt2 extends Pattern {
		public Pattern p1;
		public Pattern p2;

		public Opt2(Pattern p1, Pattern p2) {
			this.p1 = p1;
			this.p2 = p2;
		}

		@Override
		public String toString() {
			return String.format("{%s OPTIONAL %s}", Objects.toString(p1), Objects.toString(p2));
		}
	}

	public static class Union2 extends Pattern {
		public Pattern p1;
		public Pattern p2;

		public Union2(Pattern p1, Pattern p2) {
			this.p1 = p1;
			this.p2 = p2;
		}

		@Override
		public String toString() {
			return String.format("{%s UNION %s}", Objects.toString(p1), Objects.toString(p2));
		}
	}

	public static class Filter2 extends Pattern {
		public Pattern p1;
		public Set<Var> r2;

		public Filter2(Pattern p1, Set<Var> r2) {
			this.p1 = p1;
			this.r2 = r2;
		}

		@Override
		public String toString() {
			return String.format("{%s FILTER %s}", Objects.toString(p1), Objects.toString(r2));
		}
	}

	public static class Basic1 extends Pattern {
		public Set<Var> v1;

		public Basic1(Set<Var> v1) {
			this.v1 = v1;
		}

		private Basic1() {
			this(Collections.emptySet());
		}

		@Override
		public String toString() {
			return Objects.toString(v1);
		}
	}

	private static final Basic1 EMPTY_PATTERN = new Basic1();

	public static Pattern rebuilt(Element element) {
		if (element instanceof ElementGroup) {
			ElementGroup group = (ElementGroup) element;
			return handleGroup(group);
		} else if (element instanceof ElementOptional) {
			ElementOptional optional = (ElementOptional) element;
			Pattern p2 = rebuilt(optional.getOptionalElement());
			if (p2 != null) {
				return new Opt2(null, p2);
			} else {
				return EMPTY_PATTERN;
			}
		} else if (element instanceof ElementFilter) {
			ElementFilter filter = (ElementFilter) element;
			Set<Var> v2 = filter.getExpr().getVarsMentioned();
			return new Filter2(null, v2);
		} else if (element instanceof ElementUnion) {
			ElementUnion union = (ElementUnion) element;
			ArrayList<Pattern> children = union.getElements().stream()
				.map(PatternTree::rebuilt)
				.collect(Collectors.toCollection(ArrayList::new));
			int n = children.size();
			if (n > 0) {
				Pattern p = children.get(0);
				for (int i = 0; i < n - 1; i++) {
					Pattern p2 = children.get(i + 1);
					p = new Union2(p, p2);
				}
				return p;
			} else {
				return EMPTY_PATTERN;
			}



		} else if (element instanceof ElementPathBlock) {
			ElementPathBlock pathBlock = (ElementPathBlock) element;
			return new Basic1(SparqlAlgorithms.tripleVars(pathBlock));
		}
		return EMPTY_PATTERN;  
	}

	private static Pattern handleGroup(ElementGroup group) {
		ArrayList<Pattern> children = group.getElements().stream()
			.map(PatternTree::rebuilt)
			.collect(Collectors.toCollection(ArrayList::new));
		int n = children.size();
		if (n > 0) {
			children = order(children);
			Pattern p = children.get(0);
			if (p instanceof Opt2) {
				Opt2 opt2 = (Opt2) p;
				if (opt2.p1 == null) {
					opt2.p1 = EMPTY_PATTERN;
				}
			}
			ArrayList<Filter2> filters = new ArrayList<>();
			if (p instanceof Filter2
				&& ((Filter2) p).p1 == null) {
				Filter2 filter2 = (Filter2) p;
				filters.add(filter2);
			}
			for (int i = 0; i < n - 1; i++) {
				Pattern p2 = children.get(i + 1);
				if (p2 instanceof Opt2
					&& ((Opt2) p2).p1 == null) {
					Opt2 opt2 = (Opt2) p2;
					p = new Opt2(p, opt2.p2);
				} else if (p2 instanceof Filter2
					&& ((Filter2) p2).p1 == null) {
					Filter2 filter2 = (Filter2) p2;
					filters.add(filter2);
				} else {
					p = new And2(p, p2);
				}
			}
			for (Filter2 filter2 : filters) {
				p = new Filter2(p, filter2.r2);
			}
			return p;
		} else {
			return EMPTY_PATTERN;
		}
	}


	private static ArrayList<Pattern> order(ArrayList<Pattern> children) {
		ArrayList<Pattern> cList = new ArrayList<>();
		ArrayList<Pattern> oList = new ArrayList<>();
		for (Pattern p : children) {
			if (!(p instanceof Opt2)) {
				cList.add(p);
			} else {
				oList.add(p);
			}
		}
		cList.addAll(oList);
		return cList;
	}
}
