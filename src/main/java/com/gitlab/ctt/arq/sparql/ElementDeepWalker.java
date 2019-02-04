package com.gitlab.ctt.arq.sparql;

import com.gitlab.ctt.arq.core.BatchProcessor.BailException;
import org.apache.jena.sparql.algebra.walker.ElementWalker_New.EltWalker;
import org.apache.jena.sparql.expr.ExprVisitor;
import org.apache.jena.sparql.expr.ExprVisitorBase;
import org.apache.jena.sparql.syntax.Element;
import org.apache.jena.sparql.syntax.ElementService;
import org.apache.jena.sparql.syntax.ElementSubQuery;
import org.apache.jena.sparql.syntax.ElementVisitor;

public class ElementDeepWalker {
	public static void walk(Element el, ElementVisitor visitor) {
		walk(el, visitor, null, null) ;
	}

	public static void walkWithService(Element el, ElementVisitor visitor) {
		EltWalker w = new DeepWalker(visitor, null, null);
		walk(el, visitor, null, null, w);
	}

	protected static void walk(Element el, ElementVisitor visitor, EltWalker w) {
		walk(el, visitor, null, null, w);
	}

	protected static void walk(Element el, ElementVisitor visitor,
			ElementVisitor beforeVisitor, ElementVisitor afterVisitor) {
		EltWalker w = new SubQueryWalker(visitor, beforeVisitor, afterVisitor);
		walk(el, visitor, beforeVisitor, afterVisitor, w);
	}

	protected static void walk(Element el, ElementVisitor visitor,
			ElementVisitor beforeVisitor, ElementVisitor afterVisitor,
			EltWalker w) {
		el.visit(w);
	}






















	protected static class DeepWalker extends EltWalker {
		protected DeepWalker(ElementVisitor visitor, ExprVisitor exprVisitor) {
			super(visitor, exprVisitor);
		}

		protected DeepWalker(ElementVisitor visitor,
				ElementVisitor dummyBeforeVisitor,
				ElementVisitor dummyAfterVisitor) {
			super(visitor, new ExprVisitorBase());
		}

		@Override
		public void visit(ElementSubQuery el) {
			try {


				elementVisitor.visit(el);
				ElementDeepWalker.walk(el.getQuery().getQueryPattern(), elementVisitor);
			} catch (BailException ignored) {
			}
		}

		@Override
		public void visit(ElementService el) {
			try {
				elementVisitor.visit(el);
				ElementDeepWalker.walk(el.getElement(), elementVisitor);
			} catch (BailException ignored) {
			}
		}
	}

	protected static class SubQueryWalker extends DeepWalker {
		protected SubQueryWalker(ElementVisitor visitor,
			ElementVisitor dummyBeforeVisitor,
			ElementVisitor dummyAfterVisitor) {
			super(visitor, new ExprVisitorBase());
		}

		@Override
		public void visit(ElementService el) {

			elementVisitor.visit(el);
		}
	}
}
