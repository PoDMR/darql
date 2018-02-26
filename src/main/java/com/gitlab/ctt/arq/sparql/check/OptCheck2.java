package com.gitlab.ctt.arq.sparql.check;

import com.gitlab.ctt.arq.core.BatchProcessor;
import com.gitlab.ctt.arq.sparql.check.PatternTree.*;
import com.gitlab.ctt.arq.util.GraphShape;
import com.gitlab.ctt.arq.util.SparqlUtil;
import com.gitlab.ctt.arq.utilx.LabeledEdge;
import com.gitlab.ctt.arq.utilx.Resources;
import fj.data.Either;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.jena.ext.com.google.common.collect.Sets;
import org.apache.jena.query.Query;
import org.apache.jena.sparql.core.Var;
import org.apache.jena.sparql.syntax.Element;
import org.jgrapht.UndirectedGraph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.Pseudograph;

import java.util.*;

import static com.gitlab.ctt.arq.sparql.check.PatternTree.*;


public class OptCheck2 {
	public static void main(String[] args) {
		String sparqlStr1 = Resources.getResourceAsString("sample/misc/scrap.sparql");
		Either<Exception, Query> mQuery = SparqlUtil.get().toQuery(sparqlStr1);
		if (mQuery.isRight()) {
			Query query = mQuery.right().value();
			Element element = query.getQueryPattern();
			System.out.println(OptCheck2.wdpt(element));
		} else {
			throw new RuntimeException(mQuery.left().value());
		}
	}

	private static class Node {
		public Set<Var> vars;

		public Node(Set<Var> vars) {
			this.vars = vars;
		}

		@Override
		public String toString() {
			return vars.toString();
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) {
				return true;
			}
			if (!(o instanceof Node)) {
				return false;
			}
			Node node = (Node) o;
			return vars.equals(node.vars);
		}

		@Override
		public int hashCode() {
			return vars.hashCode();
		}
	}

	private UndirectedGraph<Node, DefaultEdge> g = new Pseudograph<>(DefaultEdge.class);
	private Map<Var, UndirectedGraph<Pair<Var, Node>, DefaultEdge>> v2g =
		new LinkedHashMap<>();

	public static boolean wdpt(Element element) {
		return new OptCheck2().check(element);
	}

	public boolean check(Element element) {
		Pattern p = rebuilt(element);
		Node root = patternTree(p);

		return check();
	}

	private Node patternTree(Pattern p) {
		if (p instanceof Opt2) {
			Opt2 p0 = (Opt2) p;
			Node r1 = patternTree(p0.p1);
			Node r2 = patternTree(p0.p2);
			g.addVertex(r1);
			g.addVertex(r2);
			g.addEdge(r1, r2, new LabeledEdge<>(Pair.of(r1, r2)));
			return r1;  
		} else if (p instanceof And2) {
			And2 p0 = (And2) p;
			Node r1 = patternTree(p0.p1);
			Node r2 = patternTree(p0.p2);
			LinkedHashSet<Var> vs = new LinkedHashSet<>(r1.vars);
			vs.addAll(r2.vars);
			return new Node(vs);
		} else if (p instanceof Filter2) {
			Filter2 p0 = (Filter2) p;
			Node r1 = patternTree(p0.p1);
			return new Node(r1.vars);
		} else if (p instanceof Basic1) {
			Basic1 p0 = (Basic1) p;
			return new Node(p0.v1);
		} else {
			throw new BatchProcessor.BailException("Unexpected input.");
		}
	}

	public boolean check() {
		for (Node s : g.vertexSet()) {
			for (DefaultEdge e : g.edgesOf(s)) {
				Node n1 = g.getEdgeSource(e);
				Node n2 = g.getEdgeTarget(e);
				Node t = s.equals(n1) ? n2: n1;
				for (Var v : Sets.union(s.vars, t.vars)) {
					UndirectedGraph<Pair<Var, Node>, DefaultEdge> vg = v2g.computeIfAbsent(
						v, k -> new Pseudograph<>(DefaultEdge.class));
					Pair<Var, Node> ns = null;
					Pair<Var, Node> nt = null;
					if (s.vars.contains(v)) {
						ns = Pair.of(v, s);
						vg.addVertex(ns);
					}
					if (t.vars.contains(v)) {
						nt = Pair.of(v, t);
						vg.addVertex(nt);
					}
					if (ns != null && nt != null) {
						vg.addEdge(ns, nt, new LabeledEdge<>(Pair.of(ns, nt)));
					}
				}
			}
		}







		return v2g.values().stream().allMatch(GraphShape::isConnected);
	}
}
