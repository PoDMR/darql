package com.gitlab.ctt.arq.analysis.aspect.util;

import at.unisalzburg.dbresearch.apted.costmodel.CostModel;
import at.unisalzburg.dbresearch.apted.distance.APTED;
import at.unisalzburg.dbresearch.apted.node.Node;
import at.unisalzburg.dbresearch.apted.node.StringNodeData;
import com.gitlab.ctt.arq.utilx.Resources;
import org.apache.jena.ext.com.google.common.collect.Lists;
import org.apache.jena.graph.Node_Variable;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.SortCondition;
import org.apache.jena.sparql.core.TriplePath;
import org.apache.jena.sparql.core.Var;
import org.apache.jena.sparql.expr.Expr;
import org.apache.jena.sparql.syntax.*;

import java.io.PrintStream;
import java.util.*;
import java.util.stream.Collectors;

import static com.google.common.base.Objects.firstNonNull;

public class JenaTed {
	public static void main(String[] args) {


		testResources("sample/diff/w2_1.sparql", "sample/diff/w2_2.sparql");
	}

	private static void testResources(String name1, String name2) {
		String sparqlStr1 = Resources.getResourceAsString(name1);
		String sparqlStr2 = Resources.getResourceAsString(name2);
		Query q1 = QueryFactory.create(sparqlStr1);
		Query q2 = QueryFactory.create(sparqlStr2);
		JenaTed ted = JenaTed.compute(q1, q2);
		ted.printDebug(System.out);
	}

	private APTED<JenaCostModel, JenaNode> apted;
	private TedVisitor tedVisitor1;
	private TedVisitor tedVisitor2;
	private float editDistance;

	private JenaTed(Query q1, Query q2) {
		tedVisitor1 = new TedVisitor();
		Node<JenaNode> n1 = QueryNode.wrap(q1, tedVisitor1);
		tedVisitor2 = new TedVisitor();
		Node<JenaNode> n2 = QueryNode.wrap(q2, tedVisitor2);
		apted = new APTED<>(new JenaCostModel());
		editDistance = apted.computeEditDistance(n1, n2);
	}

	public static JenaTed compute(Query q1, Query q2) {
		return new JenaTed(q1, q2);
	}

	public float similarity() {
		return 1.0f - (editDistance / primeSize());
	}

	private float primeSize() {
		return Math.max(tedVisitor1.size(), tedVisitor2.size());
	}

	public List<int[]> editMapping() {
		return apted.computeEditMapping();
	}

	public void printDebug(PrintStream out) {
		out.println(similarity());
		out.println(editDistance);
		out.println(primeSize());
		out.println(editMapping()
			.stream().map(Arrays::toString).collect(Collectors.joining(", ")));
	}

	private static class JenaCostModel implements CostModel<JenaNode> {
		@Override
		public float del(Node<JenaNode> node) {
			return JenaNode.SMALL;
		}

		@Override
		public float ins(Node<JenaNode> node) {
			return JenaNode.SMALL;
		}

		@Override
		public float ren(Node<JenaNode> n1, Node<JenaNode> n2) {
			return n1.getNodeData().diff(n2.getNodeData());
		}
	}

	private static abstract class JenaNode extends StringNodeData {
		private static final float SMALL = 1.0f;
		private static final float BIG = 1.0f;

		public JenaNode() {
			super(null);
		}

		@Override
		public String getLabel() {
			return toString();
		}

		public static Node<JenaNode> wrap(Query query, TedVisitor visitor) {
			return QueryNode.wrap(query, visitor);
		}

		public static Node<JenaNode> wrap(Element el) {
			return ElementNode.wrap(el);
		}

		public static Node<JenaNode> wrap(ElementPathBlock bgp) {
			return BlockNode.wrap(bgp);
		}

		public abstract float diff(JenaNode other);
		public abstract float weight();

		@Override
		public String toString() {
			return String.format("%s: %s", getClass().getSimpleName(), weight());
		}
	}

	private static class ElementNode extends JenaNode {
		private Element el;

		public static Node<JenaNode> wrap(Element el) {
			ElementNode self = new ElementNode();
			self.el = el;
			return new Node<>(self);
		}

		@Override
		public float diff(JenaNode other) {
			if (!this.getClass().equals(other.getClass())) {
				return weight();
			}






			return 0.0f;
		}

		@Override
		public float weight() {
			return JenaNode.BIG;
		}
	}

	private static class QueryNode extends JenaNode {


		public static Node<JenaNode> wrap(Query query, TedVisitor visitor) {
			QueryNode self = new QueryNode();
			Node<JenaNode> node = new Node<>(self);

			self.addValue(visitor, node, query.getQueryType(), JenaNode.BIG);
			self.addValue(visitor, node, query.getLimit());
			self.addValue(visitor, node, query.getOffset());


			List<SortCondition> orderBy =
				firstNonNull(query.getOrderBy(), Collections.emptyList());
			self.addList(visitor, node, orderBy.stream()
				.filter(Objects::nonNull)
				.map(e -> e.getExpression().getClass())
				.collect(Collectors.toList()));
			List<Expr> havingExprs =
				firstNonNull(query.getHavingExprs(), Collections.emptyList());
			self.addList(visitor, node, havingExprs.stream()
				.filter(Objects::nonNull)
				.map(Expr::getClass).collect(Collectors.toList()));
			List<Var> groupBy =
				firstNonNull(query.getGroupBy().getVars(), Collections.emptyList());
			self.addList(visitor, node, groupBy.stream()
				.filter(Objects::nonNull)
				.map(e -> e.getVarName().getClass())
				.collect(Collectors.toList()));
			self.addValue(visitor, node, query.isQueryResultStar() ? 1L : 0L);
			self.addList(visitor, node, Lists.newArrayList(
				query.getProject().getExprs().entrySet()
				.stream()
				.filter(Objects::nonNull)
				.map(e -> e.getKey().getClass())
				.collect(Collectors.toList())));



			Element element = query.getQueryPattern();
			if (element != null) {
				element.visit(visitor);
				node.addChild(visitor.top());
			}
			return node;
		}

		private void addValue(TedVisitor visitor, Node<JenaNode> node, long value) {
			float weight = value <= 0L ? 0.0f : JenaNode.SMALL;
			visitor.addToNode(node, ObjectNode.wrap(value), weight);
		}

		private void addValue(TedVisitor visitor, Node<JenaNode> node, long value, float weight) {
			visitor.addToNode(node, ObjectNode.wrap(value), weight);
		}

		private void addList(TedVisitor visitor, Node<JenaNode> node, List<?> list) {
			float weight = list == null || list.isEmpty() ? 0.0f : JenaNode.SMALL;
			visitor.addToNode(node, ListNode.wrap(list), weight);
		}

		@Override
		public float diff(JenaNode other) {
			if (!this.getClass().equals(other.getClass())) {
				return weight();
			}
			return 0.0f;
		}

		@Override
		public float weight() {
			return 0.0f;

		}
	}

	private static class ListNode extends JenaNode {

		private float weight;

		public static Node<JenaNode> wrap(List<?> list) {
			return wrap(list, JenaNode.BIG);
		}

		public static Node<JenaNode> wrap(List<?> list, float weight) {
			list = list != null ? list : Collections.emptyList();
			ListNode self = new ListNode();

			self.weight = weight;
			Node<JenaNode> node = new Node<>(self);
			for (Object o : list) {
				node.addChild(ObjectNode.wrap(o));
			}
			return node;
		}

		@Override
		public float diff(JenaNode other) {
			if (!this.getClass().equals(other.getClass())) {
				return weight();
			}
			return 0;
		}

		@Override
		public float weight() {
			return weight; 
		}
	}

	private static class ObjectNode extends JenaNode {
		private Object object;
		private float weight;

		public static Node<JenaNode> wrap(Object object) {
			return wrap(object, JenaNode.SMALL);
		}

		public static Node<JenaNode> wrap(Object object, float weight) {
			ObjectNode self = new ObjectNode();
			self.object = object;
			self.weight = weight;
			return new Node<>(self);
		}

		@Override
		public float diff(JenaNode other) {
			if (!this.getClass().equals(other.getClass())) {
				return weight();
			}
			ObjectNode objectNode2 = (ObjectNode) other;
			if (!this.object.equals(objectNode2.object)) {
				return weight();
			}
			return 0;
		}

		@Override
		public float weight() {
			return weight;
		}
	}

	private static class BlockNode extends JenaNode {


		public static Node<JenaNode> wrap(ElementPathBlock bgp) {
			BlockNode self = new BlockNode();
			Node<JenaNode> node = new Node<>(self);

			for (TriplePath tps : bgp.getPattern().getList()) {


				node.addChild(AtomNode.wrapObj(tps.getSubject()));
				Object predicate = tps.getPath();
				if (predicate == null) {
					predicate = tps.getPredicate();
				}
				if (predicate != null) {
					node.addChild(AtomNode.wrapObj(predicate));
				}
				node.addChild(AtomNode.wrapObj(tps.getObject()));
			}
			return node;
		}

		@Override
		public float diff(JenaNode other) {
			if (!this.getClass().equals(other.getClass())) {
				return weight();
			}
			return 0.0f;
		}

		@Override
		public float weight() {
			return JenaNode.BIG;
		}
	}

	private static class AtomNode extends JenaNode {
		Object node;

		public static Node<JenaNode> wrapObj(Object node) {
			AtomNode self = new AtomNode();
			self.node = node;
			return new Node<>(self);
		}

		@Override
		public float diff(JenaNode other) {
			if (!this.getClass().equals(other.getClass())) {
				return weight();
			}
			AtomNode node2 = (AtomNode) other;
			boolean equals = this.node.equals(node2.node);
			if (equals) {
				return 0.0f;
			}
			if (Node_Variable.class.isAssignableFrom(this.node.getClass())) {
				if (Node_Variable.class.isAssignableFrom(node2.node.getClass())) {
					return 0.0f;
				}
			}
			return weight();
		}

		@Override
		public float weight() {
			return JenaNode.SMALL;
		}
	}

	private static class TedVisitor extends ElementVisitorBase {
		private ArrayDeque<Node<JenaNode>> stack = new ArrayDeque<>();
		private float sum = 0.0f;

		public Node<JenaNode> top() {
			if (stack.isEmpty()) {
				return null;
			}
			return stack.pop();
		}

		public float size() {
			return sum;
		}

		public void addToNode(Node<JenaNode> node, Node<JenaNode> child, float weight) {
			node.addChild(child);

			sum += weight;
		}

		private void assimilate(Node<JenaNode> node) {
			stack.push(node);
			sum += node.getNodeData().weight();
		}

		private void withChildren(Element el, List<Element> children) {
			Node<JenaNode> node = JenaNode.wrap(el);
			for (Element child : children) {
				child.visit(this);
				Node<JenaNode> nChild = stack.pop();
				node.addChild(nChild);

			}
			assimilate(node);
		}

		private void direct(Element el) {
			assimilate(JenaNode.wrap(el));
		}


		public void visit(ElementTriplesBlock el) {

			direct(el);
		}


		public void visit(ElementPathBlock el) {


			Node<JenaNode> node = JenaNode.wrap(el);
			assimilate(node);
			sum += node.getChildren().size();
		}

		public void visit(ElementFilter el) {
			direct(el);  

		}

		public void visit(ElementAssign el) {
			direct(el);  


		}

		public void visit(ElementBind el) {
			direct(el);  


		}

		public void visit(ElementData el) {
			direct(el);  

		}

		public void visit(ElementUnion el) {
			List<Element> children = el.getElements();
			withChildren(el, children);
		}

		public void visit(ElementOptional el) {
			List<Element> children = Lists.newArrayList(el.getOptionalElement());
			withChildren(el, children);
		}

		public void visit(ElementGroup el) {
			List<Element> children = el.getElements();
			withChildren(el, children);
		}

		public void visit(ElementDataset el) {
			direct(el);  

		}

		public void visit(ElementNamedGraph el) {
			List<Element> children = Lists.newArrayList(el.getElement());
			withChildren(el, children);


		}

		public void visit(ElementExists el) {
			List<Element> children = Lists.newArrayList(el.getElement());
			withChildren(el, children);
		}

		public void visit(ElementNotExists el) {
			List<Element> children = Lists.newArrayList(el.getElement());
			withChildren(el, children);
		}

		public void visit(ElementMinus el) {
			List<Element> children = Lists.newArrayList(el.getMinusElement());
			withChildren(el, children);
		}

		public void visit(ElementService el) {
			List<Element> children = Lists.newArrayList(el.getElement());
			withChildren(el, children);
		}

		public void visit(ElementSubQuery el) {
			assimilate(JenaNode.wrap(el.getQuery(), this));
		}
	}
}
