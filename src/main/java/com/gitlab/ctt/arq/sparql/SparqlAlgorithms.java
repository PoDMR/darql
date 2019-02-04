package com.gitlab.ctt.arq.sparql;

import com.gitlab.ctt.arq.core.BatchProcessor.BailException;
import com.gitlab.ctt.arq.util.SparqlUtil;
import com.gitlab.ctt.arq.utilx.Resources;
import fj.data.Either;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.apache.jena.graph.Node;
import org.apache.jena.query.Query;
import org.apache.jena.sparql.core.TriplePath;
import org.apache.jena.sparql.core.Var;
import org.apache.jena.sparql.expr.*;
import org.apache.jena.sparql.syntax.*;

import java.util.*;

import static com.gitlab.ctt.arq.sparql.SparqlGraph.exprWalkerWalk;

public class SparqlAlgorithms {
	public static void main(String[] args) {
		String sparqlStr = Resources.getResourceAsString("sample/misc/scrap.sparql");
		Either<Exception, Query> mQuery = SparqlUtil.get().toQuery(sparqlStr);
		Set<TriplePath> tps = collectTriples(mQuery.right().value().getQueryPattern());
		System.out.println(tps.size());
	}

	
	public static Set<Var> tripleVars(Element element) {
		Set<Var> set = new LinkedHashSet<>();
		ElementDeepWalker.walk(element, new ElementVisitorBase() {
			@Override
			public void visit(ElementPathBlock el) {
				el.patternElts().forEachRemaining(tp -> {
					exVar(tp.getSubject()).ifPresent(set::add);
					exVar(tp.getSubject()).ifPresent(set::add);
					exVar(tp.getObject()).ifPresent(set::add);
				});
			}
		});
		return set;
	}

	public static Set<Var> filterVars(Element element) {
		Set<Var> filterVars = new HashSet<>();
		ElementDeepWalker.walk(element, new ElementVisitorBase() {
			@Override
			public void visit(ElementFilter el) {
				filterVars.addAll(el.getExpr().getVarsMentioned());
			}
		});
		return filterVars;
	}

	private static Optional<Var> exVar(Node node) {
		if (node instanceof Var) {
			Var var = (Var) node;
			return Optional.of(var);
		} else {
			return Optional.empty();
		}
	}

	public static Set<TriplePath> collectTriples(Element element) {
		Set<TriplePath> set = new HashSet<>();
		ElementDeepWalker.walk(element, new ElementVisitorBase() {
			@Override
			public void visit(ElementPathBlock el) {
				el.patternElts().forEachRemaining(set::add);
			}
		});
		return set;
	}

	public static Set<TriplePath> collectTriplesWithService(Element element) {
		Set<TriplePath> set = new HashSet<>();
		ElementDeepWalker.walkWithService(element, new ElementVisitorBase() {
			@Override
			public void visit(ElementPathBlock el) {
				el.patternElts().forEachRemaining(set::add);
			}
		});
		return set;
	}

	public static boolean isConjunctive(ElementGroup elg) {
		HashSet<Class<? extends Element>> classes = new HashSet<>(Arrays.asList(
			ElementPathBlock.class,
			ElementTriplesBlock.class,
			ElementGroup.class,
			ElementUnion.class
		));
		int count = 0;
		for (Element e : elg.getElements()) {
			if (classes.contains(e.getClass())) {
				count++;
			}
		}
		return count > 1;
	}

	public static boolean containsFilter(Element element) {
		MutableBoolean mutableBoolean = new MutableBoolean();
		ElementDeepWalker.walk(element, new ElementVisitorBase() {
			@Override
			public void visit(ElementFilter el) {
				mutableBoolean.setTrue();
			}
		});
		return mutableBoolean.isTrue();
	}

	public static Set<String> collectRegexStrings(Element element) {
		Set<String> regexs = new LinkedHashSet<>();
		ElementDeepWalker.walk(element, new ElementVisitorBase() {
			@Override
			public void visit(ElementFilter filter) {
				Expr expr = filter.getExpr();
				exprWalkerWalk(new ExprVisitorBase() {
					@Override
					public void visit(ExprFunctionN func) {
						if (func instanceof E_Regex) {
							E_Regex e_regex = (E_Regex) func;
							String regexStr = e_regex.getFunction().getArgs().get(1).toString();
							regexs.add(regexStr);
						}
					}
				}, expr);
			}
		});
		return regexs;
	}

	public static void collectUnionArgs(List<Element> list, Element element) {
		if (element instanceof ElementUnion) {
			ElementUnion union = (ElementUnion) element;
			for (Element el : union.getElements()) {
				collectUnionArgs(list, el);
			}
		} else if (element instanceof ElementGroup) {
			ElementGroup group = (ElementGroup) element;
			if (group.size() == 1) {
				Element el = group.get(0);
				collectUnionArgs(list, el);
			} else {
				list.add(element);
			}
		} else {
			list.add(element);
		}
	}

	public static boolean isUnionFree(Element element) {
		try {
			ElementDeepWalker.walk(element, new ElementVisitorBase() {
				@Override
				public void visit(ElementUnion el) {
					throw new BailException("not union-free");
				}
			});
		} catch (BailException ignore) {
			return false;
		}
		return true;
	}
}
