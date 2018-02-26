package com.gitlab.ctt.arq.sparql;

import com.gitlab.ctt.arq.analysis.aspect.util.FlagWalker;
import com.gitlab.ctt.arq.core.BatchProcessor.BailException;
import com.gitlab.ctt.arq.sparql.check.DesignCheck;
import com.gitlab.ctt.arq.util.HyperGraph;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.Node_Variable;
import org.apache.jena.sparql.syntax.*;

import java.util.LinkedHashSet;
import java.util.Set;

public class SparqlProperties {
	public static class DetectionException extends RuntimeException {
		public DetectionException(String message) {
			super(message);
		}
	}

	private static final SparqlProperties INSTANCE = new SparqlProperties();

	public static SparqlProperties get() {
		return INSTANCE;
	}


	public boolean isWellBehaved(Element element) {
		if (!isOWD(element) || !isUWD(element)) {
			return false;
		}
		try {
			ElementDeepWalker.walk(element, new ElementVisitorBase() {
				@Override
				public void visit(ElementOptional p2) {
					if (!isWellBehavedOpt(p2)) {
						throw new DetectionException("non-well-behaved OPT");
					}
				}
			});
		} catch (DetectionException ignored) {
			return false;
		}
		return true;
	}

	public boolean isOWD(Element element) {
		return DesignCheck.isOWD(element);
	}

	public boolean isUWD(Element element) {
		return DesignCheck.isUWD(element);
	}

	private boolean isWellBehavedOpt(Element p2) {
		return isCpf(p2) && isAcyclic(p2) && !hasOnlySimpleFilters(p2);
	}

	private boolean hasOnlySimpleFilters(Element element) {
		try {
			ElementDeepWalker.walk(element, new ElementVisitorBase() {
				@Override
				public void visit(ElementFilter el) {
					if (el.getExpr().getVarsMentioned().size() > 1) {
						throw new DetectionException("non-simple filter");
					}
				}
			});
		} catch (DetectionException ignored) {
			return false;
		}
		return true;
	}


	public boolean isCpf(Element element) {
		FlagWalker flagWalker = new FlagWalker();
		flagWalker.consume(element);
		long flagLong = flagWalker.asLong();

		return ((flagLong & ~9L) == 0);  


























	}




	public boolean isAcyclic(Element element) {
		if (hasBadOpt(element)) {
			return false;  
		}
		HyperGraph<Node> hg = buildHyperGraph(element);
		hg.gyoReduce();
		return hg.isEmpty();
	}

	private static boolean hasBadOpt(Element element) {
		try {
			SparqlGraph.throwOnBadOpt(element);
		} catch (BailException e) {
			return true;
		}
		return false;
	}


	private HyperGraph<Node> buildHyperGraph(Element element) {
		HyperGraph<Node> hg = new HyperGraph<>();

		ElementDeepWalker.walk(element, new ElementVisitorBase() {
			@Override
			public void visit(ElementPathBlock el) {
				el.patternElts().forEachRemaining(tp -> {
					Node subject = tp.getSubject();
					Node object = tp.getObject();
					Node predicate = tp.getPredicate();
					Set<Node> he = new LinkedHashSet<>();
					if (subject instanceof Node_Variable) {
						he.add(subject);
					}
					if (object instanceof Node_Variable) {
						he.add(object);
					}
					if (predicate instanceof Node_Variable) {
						he.add(predicate);
					}
					hg.cover(he);
				});
			}
		});
		return hg;
	}

	public boolean hasPath(Element element) {
		try {
			ElementDeepWalker.walk(element, new ElementVisitorBase() {
				@Override
				public void visit(ElementPathBlock el) {
					el.patternElts().forEachRemaining(tp -> {
						if (tp.getPredicate() == null) {
							if (tp.getPath() != null) {  
								throw new DetectionException("contains path");
							}
						}
					});
				}
			});
		} catch (DetectionException ignored) {
			return true;
		}
		return false;
	}
}
