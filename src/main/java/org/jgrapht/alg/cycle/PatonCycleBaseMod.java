package org.jgrapht.alg.cycle;

import org.jgrapht.UndirectedGraph;

import java.util.*;


public class PatonCycleBaseMod<V, E>
	implements UndirectedCycleBase<V, E>
{
	private UndirectedGraph<V, E> graph;

	
	public PatonCycleBaseMod()
	{
	}

	
	public PatonCycleBaseMod(UndirectedGraph<V, E> graph)
	{
		if (graph == null) {
			throw new IllegalArgumentException("Null graph argument.");
		}
		this.graph = graph;
	}

	
	@Override
	public UndirectedGraph<V, E> getGraph()
	{
		return graph;
	}

	
	@Override
	public void setGraph(UndirectedGraph<V, E> graph)
	{
		if (graph == null) {
			throw new IllegalArgumentException("Null graph argument.");
		}
		this.graph = graph;
	}

	
	@Override
	public List<List<V>> findCycleBase()
	{
		if (graph == null) {
			throw new IllegalArgumentException("Null graph.");
		}
		Map<V, Set<V>> used = new HashMap<>();
		Map<V, V> parent = new HashMap<>();
		ArrayDeque<V> stack = new ArrayDeque<>();
		List<List<V>> cycles = new ArrayList<>();

		for (V root : graph.vertexSet()) {


			if (parent.containsKey(root)) {
				continue;
			}



			used.clear();


			parent.put(root, root);
			used.put(root, new HashSet<>());
			stack.push(root);





			while (!stack.isEmpty()) {
				V current = stack.pop();
				Set<V> currentUsed = used.get(current);
				for (E e : graph.edgesOf(current)) {
					V neighbor = graph.getEdgeTarget(e);
					if (neighbor.equals(current)) {
						neighbor = graph.getEdgeSource(e);
					}
					if (!used.containsKey(neighbor)) {

						parent.put(neighbor, current);
						Set<V> neighbourUsed = new HashSet<>();
						neighbourUsed.add(current);
						used.put(neighbor, neighbourUsed);
						stack.push(neighbor);
					} else if (neighbor.equals(current)) {

						List<V> cycle = new ArrayList<>();
						cycle.add(current);
						cycles.add(cycle);
					} else if (!currentUsed.contains(neighbor)) {

						Set<V> neighbourUsed = used.get(neighbor);
						List<V> cycle = new ArrayList<>();
						cycle.add(neighbor);
						cycle.add(current);
						V p = parent.get(current);
						while (!neighbourUsed.contains(p)) {
							cycle.add(p);
							V q = parent.get(p);
							if (q == p) {  
								break;  
							} else {  
								p = q;  
							}  
						}
						cycle.add(p);
						cycles.add(cycle);
						neighbourUsed.add(current);
					}
				}
			}
		}
		return cycles;
	}
}


