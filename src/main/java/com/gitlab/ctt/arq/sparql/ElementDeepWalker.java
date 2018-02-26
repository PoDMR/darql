package com.gitlab.ctt.arq.sparql;

import org.apache.jena.sparql.syntax.Element;
import org.apache.jena.sparql.syntax.ElementSubQuery;
import org.apache.jena.sparql.syntax.ElementVisitor;
import org.apache.jena.sparql.syntax.ElementWalker;

public class ElementDeepWalker {
	public static void walk(Element el, ElementVisitor visitor) {
		walk(el, visitor, null, null) ;
	}

	public static void walk(Element el, ElementVisitor visitor,
			ElementVisitor beforeVisitor,   ElementVisitor afterVisitor) {
		Walker w = new Walker(visitor, beforeVisitor, afterVisitor) ;
		el.visit(w) ;
	}

	public static class Walker extends ElementWalker.Walker {
		public Walker(ElementVisitor visitor,
				ElementVisitor beforeVisitor, ElementVisitor afterVisitor) {
			super(visitor, beforeVisitor, afterVisitor);
		}

		@Override
		public void visit(ElementSubQuery el) {
			if (beforeVisitor != null) {
				el.visit(beforeVisitor) ;
			}
			if (el.getQuery() != null && el.getQuery().getQueryPattern() != null) {
				el.getQuery().getQueryPattern().visit(this);
			}
			proc.visit(el) ;
			if (afterVisitor != null) {
				el.visit(afterVisitor) ;
			}
		}
	}
}
