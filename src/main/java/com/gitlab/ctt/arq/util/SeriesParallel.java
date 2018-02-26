package com.gitlab.ctt.arq.util;

import com.gitlab.ctt.arq.sparql.SparqlGraph;
import com.gitlab.ctt.arq.utilx.LabeledEdge;
import com.gitlab.ctt.arq.utilx.Resources;
import fj.data.Either;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.apache.jena.ext.com.google.common.collect.Sets;
import org.apache.jena.query.Query;
import org.apache.jena.sparql.syntax.Element;
import org.jgrapht.DirectedGraph;
import org.jgrapht.UndirectedGraph;
import org.jgrapht.alg.BiconnectivityInspector;
import org.jgrapht.graph.AsUndirectedGraph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.Pseudograph;
import org.jgrapht.graph.SimpleGraph;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public  class SeriesParallel {
	public static void main(String[] args) {
		String sparqlStr = Resources.getResourceAsString("sample/misc/scrap.sparql");
		Either<Exception, Query> mQuery = SparqlUtil.get().toQuery(sparqlStr);
		Element element = mQuery.right().value().getQueryPattern();
		DirectedGraph<Object, DefaultEdge> graph = SparqlGraph.graphFromQuery(element);

		System.out.println(isFlower(graph, true));
	}

	public static boolean isGarden(DirectedGraph<Object, DefaultEdge> graph, boolean simplify) {
		return graph != null && isGarden(new AsUndirectedGraph<>(graph), simplify);
	}

	public static boolean isGarden(UndirectedGraph<Object, DefaultEdge> ug, boolean simplify) {
		List<Set<Object>> cs = GraphShape.connectedSets(ug);
		return cs.stream().allMatch(c -> isFlower(GraphShape.subGraph(ug, c), simplify));
	}

	public static boolean isFlower(DirectedGraph<Object, DefaultEdge> graph, boolean simplify) {
		return graph != null && isFlower(new AsUndirectedGraph<>(graph), simplify);
	}

	public static boolean isFlower(UndirectedGraph<Object, DefaultEdge> ug, boolean simplify) {
		AtomicLong l = new AtomicLong();
		return GraphShape.isConnected(ug) && isFlower(ug,
			o -> String.format("%s_%s", String.valueOf(o), l.getAndIncrement()), simplify);
	}

	private static <V> boolean isFlower(UndirectedGraph<V, DefaultEdge> ug,
			Function<Object, V> f, boolean simplify) {
		if (ug.edgeSet().size() == 0) {
			return true;
		}
		BiconnectivityInspector<V, DefaultEdge> bci = new BiconnectivityInspector<>(ug);
		List<V> cps = bci.getCutpoints().stream().sorted(
			Comparator.<V, Integer>comparing(
				x -> bci.getBiconnectedVertexComponents(x).size()).reversed())
			.collect(Collectors.toList());
		if (cps.isEmpty()) {
			return petalType(ug, simplify) > 0;
		}

		return cps.stream().anyMatch(v -> isFlowerCut(ug, f, v, bci, simplify));
	}

	
	private static <V> boolean isFlowerCut(UndirectedGraph<V, DefaultEdge> ug,
			Function<Object, V> f, V v, BiconnectivityInspector<V, DefaultEdge> bci,
			boolean simplify) {
		UndirectedGraph<V, DefaultEdge> decomp = copyGraph(ug, false);
		Set<Set<V>> bcs = bci.getBiconnectedVertexComponents(v);
		Set<V> n = neighbors(decomp, v);
		for (Set<V> bc : bcs) {
			Set<V> vs = Sets.intersection(new LinkedHashSet<>(bc), n);
			V v1 = f.apply(v);
			for (V w : vs) {
				decomp.addVertex(v1);
				decomp.addEdge(v1, w);
			}

		}
		decomp.removeVertex(v);
		List<Set<V>> cs = GraphShape.connectedSets(decomp);
		List<Integer> types = cs.stream()
			.map(c -> GraphShape.subGraph(decomp, c))  
			.map(ug1 -> petalType(ug1, simplify)).collect(Collectors.toList());

		return types.stream().allMatch(i -> i > 0);
	}

	private static <V> Integer petalType(UndirectedGraph<V, DefaultEdge> ug, boolean simplify) {
		if (GraphShape.isTreeU(ug)) {
			return 1;
		} else if (isCollapsible(ug, simplify)) {
			return 2;
		}
		return 0;
	}

	
	private static <V> boolean isCollapsible(
			UndirectedGraph<V, DefaultEdge> graph, boolean simplify) {
		UndirectedGraph<V, DefaultEdge> ug = copyGraph(graph, simplify);
		AtomicLong l = new AtomicLong();
		while (true) {
			if (!collapse(ug, simplify ? null : () -> new LabeledEdge<>(l.get()))) {
				break;
			}
		}

		return ug.vertexSet().size() == 2;
	}

	private static <V> boolean collapse(UndirectedGraph<V, DefaultEdge> graph,
			Supplier<DefaultEdge> f) {
		MutableBoolean collapsed = new MutableBoolean();
		List<V> isolated = new ArrayList<>();
		for (List<V> pair : pairs(graph.vertexSet())) {
			V a = pair.get(0);
			V b = pair.get(1);
			if (!isolated.contains(a) && !isolated.contains(b)) {
				Set<V> na = neighbors(graph, a);
				Set<V> nb = neighbors(graph, b);
				Set<V> intersection = Sets.intersection(na, nb);
				MutableBoolean needEdge = new MutableBoolean();
				intersection.stream().filter(v -> graph.degreeOf(v) == 2).forEach(v -> {
					isolated.add(v);
					needEdge.setTrue();
					collapsed.setTrue();
				});
				if (needEdge.isTrue()) {
					if (f == null) {
						graph.addEdge(a, b);
					} else {
						graph.addEdge(a, b, f.get());
					}
				}
			}
		}
		isolated.forEach(graph::removeVertex);
		return collapsed.isTrue();
	}


	private static <V> Set<List<V>> pairs(Collection<V> c) {
		Set<Set<V>> pairs = new LinkedHashSet<>();
		for (V a : c) {
			for (V b : c) {
				if (a != b) {
					pairs.add(new LinkedHashSet<>(Arrays.asList(a, b)));
				}
			}
		}
		return pairs.stream().map(ArrayList::new).collect(Collectors.toSet());
	}

	private static <V, E> Set<V> neighbors(UndirectedGraph<V, E> graph, V v) {
		return graph.edgesOf(v).stream().map(e -> {
			V s = graph.getEdgeSource(e);
			V t = graph.getEdgeTarget(e);
			return s.equals(v) ? t : s;
		}).collect(Collectors.toSet());
	}


	private static <V, E> UndirectedGraph<V, E> copyGraph(
			UndirectedGraph<V, E> graph, boolean simplify) {
		E e0 = graph.edgeSet().stream().findFirst().orElse(null);
		if (e0 == null) {
			return graph;  
		}
		@SuppressWarnings("unchecked")
		UndirectedGraph<V, E> newGraph = simplify
			? new SimpleGraph<V, E>((Class<? extends E>) e0.getClass())
			: new Pseudograph<>((Class<? extends E>) e0.getClass());
		for (E e : graph.edgeSet()) {
			V a = graph.getEdgeSource(e);
			V b = graph.getEdgeTarget(e);
			newGraph.addVertex(a);
			newGraph.addVertex(b);
			if (simplify) {
				if (!Objects.equals(a, b)) {
					newGraph.addEdge(a, b);
				}
			} else {
				newGraph.addEdge(a, b, e);
			}
		}
		return newGraph;
	}
}
