package oarlib.graph.transform.partition.impl;

import oarlib.core.Edge;
import oarlib.graph.impl.UndirectedGraph;
import oarlib.graph.transform.partition.PartitionTransformer;
import oarlib.vertex.impl.UndirectedVertex;

import java.util.HashMap;
import java.util.HashSet;

/**
 * Graph transformer that takes an edge-weighted graph, and produces a vertex-weighted graph such that a
 * a k-way weighted vertex partition of the transformed graph corresponds to an equal k-way edge partition
 * in the original graph.  We anticipate using this to turn our single-vehicle solvers into capacitated solvers.
 *
 * @author oliverlum
 */
public class UndirectedKWayPartitionTransform implements PartitionTransformer<UndirectedGraph> {

    private UndirectedGraph mGraph;

    public UndirectedKWayPartitionTransform(UndirectedGraph input) {
        mGraph = input;
    }

    @Override
    public UndirectedGraph transformGraph() {

        try {
            int n = mGraph.getVertices().size();
            //ans
            UndirectedGraph ans = new UndirectedGraph(n);

            //setup
            UndirectedVertex temp;
            HashMap<Integer, UndirectedVertex> ansVertices = ans.getInternalVertexMap();
            HashSet<Edge> edges = mGraph.getEdges();
            int firstId, secondId;

            for (Edge e : edges) {
                firstId = e.getEndpoints().getFirst().getId();
                secondId = e.getEndpoints().getSecond().getId();

                temp = ansVertices.get(firstId);
                temp.setCost(temp.getCost() + e.getCost());

                temp = ansVertices.get(secondId);
                temp.setCost(temp.getCost() + e.getCost());

                ans.addEdge(firstId, secondId, 1);
            }

            //now set the depot cost to be 0 so that it doesn't affect the partition
            ansVertices.get(mGraph.getDepotId()).setCost(0);

            return ans;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public void setGraph(UndirectedGraph input) {
        mGraph = input;
    }

}