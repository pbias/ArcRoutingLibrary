package oarlib.solver.impl;

import oarlib.core.*;
import oarlib.graph.factory.impl.UndirectedGraphFactory;
import oarlib.graph.impl.UndirectedGraph;
import oarlib.graph.io.GraphFormat;
import oarlib.graph.io.GraphWriter;
import oarlib.graph.io.PartitionFormat;
import oarlib.graph.io.PartitionReader;
import oarlib.graph.transform.impl.EdgeInducedSubgraphTransform;
import oarlib.graph.transform.impl.UndirectedKWayPartitionTransform;
import oarlib.graph.util.CommonAlgorithms;
import oarlib.problem.impl.CapacitatedUCPP;
import oarlib.problem.impl.UndirectedCPP;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;

/**
 * Created by Oliver Lum on 7/25/2014.
 */
public class CapacitatedUCPPSolver extends CapacitatedVehicleSolver {

    CapacitatedUCPP mInstance;
    /**
     * Default constructor; must set problem instance.
     *
     * @param instance - instance for which this is a solver
     */
    public CapacitatedUCPPSolver(CapacitatedUCPP instance) throws IllegalArgumentException {
        super(instance);
        mInstance = instance;
    }

    @Override
    protected boolean checkGraphRequirements() {

        // make sure the graph is connected
        if(mInstance.getGraph() == null)
            return false;
        else
        {
            UndirectedGraph mGraph = mInstance.getGraph();
            if(!CommonAlgorithms.isConnected(mGraph))
                return false;
        }
        return true;
    }

    @Override
    protected CapacitatedUCPP getInstance() {
        return mInstance;
    }

    @Override
    protected Collection<Route> solve() {

        try {

            //initialize transformer for turning edge-weighted graph into vertex-weighted graph
            UndirectedGraph mGraph = mInstance.getGraph();
            UndirectedKWayPartitionTransform transformer = new UndirectedKWayPartitionTransform(mGraph);

            //transform the graph
            UndirectedGraph vWeightedTest = transformer.transformGraph();

            String filename = "/Users/oliverlum/Desktop/RandomGraph.graph";

            //write it to a file
            GraphWriter gw = new GraphWriter(GraphFormat.Name.METIS);
            gw.writeGraph(vWeightedTest, filename);

            //num parts to partition into
            int numParts = mInstance.getmNumVehicles();

            //partition the graph
            runMetis(numParts, filename);

            //now read the partition and reconstruct the induced subgraphs on which we solve the UCPP on to get our final solution.
            PartitionReader pr = new PartitionReader(PartitionFormat.Name.METIS);
            HashMap<Integer, Integer> sol = pr.readPartition(filename + ".part." + numParts);

            //initialize vars
            int firstId, secondId;
            int m = vWeightedTest.getEdges().size();
            double prob;
            Edge temp;
            HashMap<Integer, Edge> mGraphEdges = mGraph.getInternalEdgeMap();
            HashMap<Integer, Integer> edgeSol = new HashMap<Integer, Integer>();
            HashMap<Integer, HashSet<Integer>> partitions = new HashMap<Integer, HashSet<Integer>>();
            HashSet<Integer> valueSet = new HashSet<Integer>(sol.values());

            for(Integer part: valueSet)
            {
                partitions.put(part, new HashSet<Integer>());
            }

            //for each edge, figure out if it's internal, or part of the cut induced by the partition
            for (int i = 1; i <= m; i++) {
                temp = mGraphEdges.get(i);
                firstId = temp.getEndpoints().getFirst().getId();
                secondId = temp.getEndpoints().getSecond().getId();

                //if it's internal or to the depot, just log the edge in the appropriate partition
                if(sol.get(firstId) == sol.get(secondId)  || firstId == mGraph.getDepotId() || secondId == mGraph.getDepotId()) {
                    edgeSol.put(i, sol.get(firstId));
                    partitions.get(sol.get(firstId)).add(i);
                }
                //oth. with 50% probability, stick it in either one
                else {
                    prob = Math.random();
                    if(prob > .5) {
                        edgeSol.put(i, sol.get(firstId));
                        partitions.get(sol.get(firstId)).add(i);
                    }
                    else {
                        edgeSol.put(i, sol.get(secondId));
                        partitions.get(sol.get(secondId)).add(i);
                    }
                }
            }

            UndirectedGraphFactory ugf = new UndirectedGraphFactory();
            EdgeInducedSubgraphTransform<UndirectedGraph> subgraphTransform = new EdgeInducedSubgraphTransform<UndirectedGraph>(mGraph,ugf,null, true);
            HashSet<Route> ans = new HashSet<Route>();

            //now create the subgraphs
            for(Integer part: partitions.keySet())
            {
                subgraphTransform.setEdges(partitions.get(part));
                UndirectedGraph subgraph = subgraphTransform.transformGraph();

                //Now add the depot and some artificial edges to the graph to connect each partition to the depot

                //TODO: repair any connectivity benefits we might have lost.

                //now solve the UCPP on it
                UndirectedCPP subInstance = new UndirectedCPP(subgraph);
                UCPPSolver_Edmonds solver = new UCPPSolver_Edmonds(subInstance);
                ans.add(solver.solve());
            }

            return ans;
        } catch (Exception e)
        {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public Problem.Type getProblemType() {
        return Problem.Type.UNDIRECTED_CHINESE_POSTMAN;
    }
}