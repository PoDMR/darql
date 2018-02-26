package com.gitlab.ctt.arq.util;

import org.apache.jena.ext.com.google.common.collect.Lists;
import org.apache.jena.ext.com.google.common.collect.Sets;
import org.jgrapht.Graph;
import org.jgrapht.UndirectedGraph;
import org.jgrapht.graph.Pseudograph;

import java.util.*;

public class Cycles {
	public static void main(String[] args) {
		UndirectedGraph<Integer, String> g = new Pseudograph<>(String.class);


		buildPlaner(g);
		List<List<Integer>> base = GraphShape.findCycleBase(g);
		Set<List<Integer>> cycles = iterate(base);
		System.out.println("any: " + base);
		System.out.println("all: " + cycles);
		System.out.println("opt: " + findBase(base));
	}

	private static void build8(UndirectedGraph<Integer, String> g) {
		addEdge(g, 1, 2);
		addEdge(g, 3, 4);
		addEdge(g, 1, 3);
		addEdge(g, 2, 4);
		addEdge(g, 2, 5);
		addEdge(g, 4, 6);
		addEdge(g, 5, 6);
	}


	private static void buildDual(UndirectedGraph<Integer, String> g) {
		addEdge(g, 0, 1);
		addEdge(g, 1, 2);
		addEdge(g, 2, 3);
		addEdge(g, 3, 4);
		addEdge(g, 4, 1);
		addEdge(g, 3, 5);
		addEdge(g, 0, 6);
		addEdge(g, 0, 7);
		addEdge(g, 5, 6);
		addEdge(g, 5, 7);
	}


	private static void buildPlaner(UndirectedGraph<Integer, String> g) {
		addEdge(g, 1, 2);
		addEdge(g, 2, 3);
		addEdge(g, 3, 4);
		addEdge(g, 4, 1);
		addEdge(g, 2, 5);
		addEdge(g, 5, 6);
		addEdge(g, 6, 4);
		addEdge(g, 3, 8);
		addEdge(g, 8 ,7);
		addEdge(g, 7 ,1);
	}

	private static <V> void addEdge(Graph<V, String> graph, V a, V b) {
		graph.addVertex(a);
		graph.addVertex(b);
		graph.addEdge(a, b, String.format("%s,%s", a, b));
	}

	@SuppressWarnings("rawtypes")
	public static <V> Set<List<V>> iterate(List<List<V>> base) {
		Set<List<V>> set = new HashSet<>(base);
		set.forEach(Cycles::pivot);
		for (Set<List<V>> cycles : Sets.powerSet(new HashSet<>(base))) {
			List<List<V>> cl = new ArrayList<>(cycles);
			if (cl.size() > 1) {
				Set<Set<V>> es1 = new HashSet<>(toEdges(cl.get(0)));
				for (int i = 1; i < cl.size(); i++) {
					HashSet<Set<V>> es2 = new HashSet<>(toEdges(cl.get(i)));
					if (!Sets.intersection(es1, es2).isEmpty()) {
						Set<Set<V>> e3 = Sets.symmetricDifference(es1, es2);
						UndirectedGraph<V, Set> graph = buildGraph(e3);
						List<List<V>> base2 = GraphShape.findCycleBase(graph);
						base2.forEach(Cycles::pivot);
						set.addAll(base2);
					}
				}
			}
		}
		return set;
	}

	
	public static <V> List<List<V>> findBase(List<List<V>> base) {
		List<List<V>> bestBase = new ArrayList<>(base);
		Deque<List<V>> todo = new ArrayDeque<>(base);
		while (!todo.isEmpty()) {
			List<V> cx = todo.pop();
			Set<List<V>> baseNoCx = new HashSet<>(base);
			baseNoCx.remove(cx);
			Set<Set<List<V>>> ps = Sets.powerSet(baseNoCx);
			psLoop:
			for (Set<List<V>> cs : ps) {
				List<List<V>> cl = new ArrayList<>(cs);
				if (cl.size() > 1) {
					Set<Set<V>> es1 = new HashSet<>(toEdges(cl.get(0)));
					for (int i = 1; i < cl.size(); i++) {
						HashSet<Set<V>> es2 = new HashSet<>(toEdges(cl.get(i)));
						if (!Sets.intersection(es1, es2).isEmpty()) {
							es1 = Sets.symmetricDifference(es1, es2);
						} else {
							continue psLoop;
						}
					}
					if (es1.size() < cx.size()){
						@SuppressWarnings("rawtypes")
						UndirectedGraph<V, Set> graph = buildGraph(es1);
						List<List<V>> partBase = GraphShape.findCycleBase(graph);
						bestBase.addAll(partBase);
						bestBase.remove(cx);
						todo = new ArrayDeque<>(bestBase);
						break;
					}
				}
			}
		}
		return bestBase;
	}

	private static <V> void pivot(List<V> list) {
		V min = Collections.min(list, Comparator.comparing(Object::hashCode));
		int i = list.indexOf(min);
		Collections.rotate(list, -i);
		if (list.get(1 % list.size()).hashCode() > list.get(list.size() - 1).hashCode()) {
			Lists.reverse(list);
			Collections.rotate(list, 1);
		}
	}

	@SuppressWarnings("rawtypes")
	private static <V> UndirectedGraph<V, Set> buildGraph(Set<Set<V>> edgeSets) {
		UndirectedGraph<V, Set> graph = new Pseudograph<>(Set.class);
		for (Set<V> vs : edgeSets) {
			List<V> vl = new ArrayList<>(vs);
			for (List<V> p : Lists.partition(vl, 2)) {
				p.forEach(graph::addVertex);
				graph.addEdge(p.get(0), p.get(1), vs);
			}
		}
		return graph;
	}

	@SuppressWarnings("unchecked")
	private static <V> List<Set<V>> toEdges(List<V> vl) {
		List<Set<V>> list = new ArrayList<>();
		for (int i = 0; i < vl.size() - 1; i++) {
			list.add(Sets.newHashSet(vl.get(i), vl.get(i + 1)));
		}
		if (list.size() > 0) {
			list.add(Sets.newHashSet(vl.get(vl.size() - 1), vl.get(0)));
		}
		return list;
	}
}
