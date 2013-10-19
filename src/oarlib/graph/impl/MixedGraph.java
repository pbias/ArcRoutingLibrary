package oarlib.graph.impl;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;

import oarlib.core.Arc;
import oarlib.core.Edge;
import oarlib.core.Link;
import oarlib.graph.util.Pair;
import oarlib.vertex.impl.MixedVertex;

/**
 * Representation of  Mixed Graph; that is, it can use both edges and arcs, in tandem with mixed vertices
 * @author Oliver
 *
 */
public class MixedGraph extends MutableGraph<MixedVertex, Link<MixedVertex>>{

	@Override
	public void addVertex(MixedVertex v) {
		getVertices().add(v);
	}

	@Override
	protected void addToNeighbors(Link<MixedVertex> e) throws IllegalArgumentException {
		if(e.getClass() == Edge.class)
		{
			
		}
		else if(e.getClass() == Arc.class)
		{
			
		}
	}

	@Override
	public void addEdge(Link<MixedVertex> e) {
		if(e.getClass() == Edge.class)
		{
			
		}
		else if(e.getClass() == Arc.class)
		{
			
		}
	}

	@Override
	public Collection<Link<MixedVertex>> findEdges(Pair<MixedVertex> endpoints) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public LinkedHashMap<MixedVertex, LinkedHashSet<MixedVertex>> getNeighbors() {
		// TODO Auto-generated method stub
		return null;
	}
}
