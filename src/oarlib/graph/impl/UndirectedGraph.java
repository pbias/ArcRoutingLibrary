package oarlib.graph.impl;

import java.util.Collection;
import java.util.List;

import oarlib.core.Edge;
import oarlib.graph.util.Pair;
import oarlib.vertex.impl.UndirectedVertex;
/**
 * First attempts at an Undirected Graph.
 * @author Oliver
 *
 * @param <V> - Vertex Class
 * @param <E> - Edge Class
 */
public class UndirectedGraph extends MutableGraph<UndirectedVertex,Edge>{
	//constructors
	public UndirectedGraph(){
		super();
	}
	
	
	@Override
	public void addVertex(UndirectedVertex v) {
		super.addVertex(v);
	}

	@Override
	public void addEdge(Edge e) {
		Pair<UndirectedVertex> endpoints = e.getEndpoints();
		endpoints.getFirst().addToNeighbors(endpoints.getSecond(), e);
		endpoints.getSecond().addToNeighbors(endpoints.getFirst(), e);
		UndirectedVertex toUpdate = endpoints.getFirst();
		toUpdate.setDegree(toUpdate.getDegree()+1);
		toUpdate = e.getEndpoints().getSecond();
		toUpdate.setDegree(toUpdate.getDegree()+1);
		super.addEdge(e);
	}
	
	@Override
	public List<Edge> findEdges(Pair<UndirectedVertex> endpoints) {
		// TODO Auto-generated method stub
		return null;
	}

}
