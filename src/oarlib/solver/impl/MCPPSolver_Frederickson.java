/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2013-2015 Oliver Lum
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package oarlib.solver.impl;

import gnu.trove.TIntObjectHashMap;
import oarlib.core.Graph;
import oarlib.core.Problem;
import oarlib.core.SingleVehicleSolver;
import oarlib.core.Solver;
import oarlib.graph.impl.DirectedGraph;
import oarlib.graph.impl.MixedGraph;
import oarlib.graph.impl.UndirectedGraph;
import oarlib.graph.util.CommonAlgorithms;
import oarlib.graph.util.Pair;
import oarlib.link.impl.Arc;
import oarlib.link.impl.Edge;
import oarlib.link.impl.MixedEdge;
import oarlib.problem.impl.ProblemAttributes;
import oarlib.route.impl.Tour;
import oarlib.vertex.impl.DirectedVertex;
import oarlib.vertex.impl.MixedVertex;
import oarlib.vertex.impl.UndirectedVertex;
import org.apache.log4j.Logger;

import java.util.*;

/**
 * @author oliverlum
 *         <p/>
 *         <p/>
 *         Implements Frederickson's heuristic for the mixed CPP.  This heuristic is in fact a combination of two heuristics that
 *         whereby we choose the better-performing of the two.  Both heuristics are decompositional in nature; they find an optimal way
 *         of achieving evenness and symmetry, and then combine the two procedures to ensure that the resulting augmentation
 *         has both properties.
 */
public class MCPPSolver_Frederickson extends SingleVehicleSolver<MixedVertex, MixedEdge, MixedGraph> {

    private static final Logger LOGGER = Logger.getLogger(MCPPSolver_Frederickson.class);

    public MCPPSolver_Frederickson(Problem<MixedVertex, MixedEdge, MixedGraph> instance) throws IllegalArgumentException {
        super(instance);
    }

    /**
     * As described in Frederickson, we solve a min cost perfect matching on the odd vertices, and then
     * adds the paths to create an even graph.
     *
     * @param input
     * @param U
     */
    private static void largeCycles(MixedGraph input, ArrayList<MixedEdge> U) {
        try {
            UndirectedGraph G1 = new UndirectedGraph(); //G', in which we identify the odd degree nodes
            UndirectedGraph G2 = new UndirectedGraph(); //G'', in which we calculate least cost paths

            int maxCost = 0;
            int inputN = input.getVertices().size();

            for (int i = 1; i <= inputN; i++) {
                G1.addVertex(new UndirectedVertex("symmetric setup graph"), i);
                G2.addVertex(new UndirectedVertex("symmetric setup graph"), i);
            }
            TIntObjectHashMap<UndirectedVertex> g1Vertices = G1.getInternalVertexMap();
            TIntObjectHashMap<UndirectedVertex> g2Vertices = G2.getInternalVertexMap();

            //add edges in U to G1
            MixedEdge e;
            int uSize = U.size();
            for (int i = 0; i < uSize; i++) {
                e = U.get(i);
                G1.addEdge(new Edge("final", new Pair<UndirectedVertex>(g1Vertices.get(e.getEndpoints().getFirst().getId()), g1Vertices.get(e.getEndpoints().getSecond().getId())), e.getCost()));
            }
            //add edges in E to G2
            TIntObjectHashMap<MixedEdge> inputEdges = input.getInternalEdgeMap();
            int edgesSize = input.getEdges().size();
            for (int i = 1; i <= edgesSize; i++) {
                e = inputEdges.get(i);
                if (e.isDirected())
                    continue;
                G2.addEdge(new Edge("final", new Pair<UndirectedVertex>(g2Vertices.get(e.getEndpoints().getFirst().getId()), g2Vertices.get(e.getEndpoints().getSecond().getId())), e.getCost()));
                maxCost += e.getCost();
            }

            ArrayList<Integer> oddVertexIndices = new ArrayList<Integer>();
            //construct our odd degree set from G1
            for (UndirectedVertex v : G1.getVertices()) {
                if (v.getDegree() % 2 == 1) {
                    oddVertexIndices.add(v.getId());
                }
            }

            //find shortest paths in G2
            int n = G2.getVertices().size();
            int[][] dist = new int[n + 1][n + 1];
            int[][] path = new int[n + 1][n + 1];
            CommonAlgorithms.fwLeastCostPaths(G2, dist, path);

            UndirectedGraph matchingGraph = new UndirectedGraph();
            //setup a matching graph
            for (int i = 0; i < oddVertexIndices.size(); i++) {
                matchingGraph.addVertex(new UndirectedVertex("matching graph"), oddVertexIndices.get(i));
            }
            TIntObjectHashMap<UndirectedVertex> matchingVertices = matchingGraph.getInternalVertexMap();
            int n2 = matchingGraph.getVertices().size();
            UndirectedVertex u1, u2;
            for (int i = 1; i < n2 + 1; i++) {
                u1 = matchingVertices.get(i);
                for (int j = 1; j < n2 + 1; j++) {
                    if (i <= j)
                        continue;
                    u2 = matchingVertices.get(j);
                    if (dist[u1.getMatchId()][u2.getMatchId()] == Integer.MAX_VALUE)
                        matchingGraph.addEdge(new Edge("matching edge", new Pair<UndirectedVertex>(u1, u2), maxCost));
                    else
                        matchingGraph.addEdge(new Edge("matching edge", new Pair<UndirectedVertex>(u1, u2), dist[u1.getMatchId()][u2.getMatchId()]));
                }
            }
            Set<Pair<UndirectedVertex>> matchSolution = CommonAlgorithms.minCostMatching(matchingGraph);
            for (Pair<UndirectedVertex> p : matchSolution) {
                //add shortest paths
                int curr = p.getFirst().getMatchId();
                int end = p.getSecond().getMatchId();
                int next = 0;
                int cost = 0;
                MixedVertex u, v;
                do {
                    next = path[curr][end];
                    cost = dist[curr][next];
                    u = input.getInternalVertexMap().get(curr);
                    v = input.getInternalVertexMap().get(next);
                    U.add(new MixedEdge("from largeCycles", new Pair<MixedVertex>(u, v), cost, false));
                } while ((curr = next) != end);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Modifies the output from inoutdegree to make M and U still satisfy our balance constraints, but
     * make them also obey evenness constraints that may have been violated in the process of
     * the inoutdegree construction.
     *
     * @param input       - the original mixed graph
     * @param M           - the corresponding collection from running inoutdegree on input
     * @param U           - the corresponding collection from running inoutdegree on input
     * @param inMdubPrime - the corresponding list from running inoutdegree on input
     */
    private static void evenParity(MixedGraph input, ArrayList<MixedEdge> U, ArrayList<MixedEdge> M, ArrayList<Boolean> inMdubPrime) {
        //the sets that we'll use to construct the answer
        ArrayList<MixedEdge> Mprime = new ArrayList<MixedEdge>();
        ArrayList<MixedEdge> Uprime = new ArrayList<MixedEdge>();

        //first figure out the odd degree vertices relative to the output of inoutdegree
        try {
            MixedGraph temp = new MixedGraph();
            for (int i = 1; i <= input.getVertices().size(); i++) {
                temp.addVertex(new MixedVertex("parity graph"), i);
            }
            TIntObjectHashMap<MixedVertex> tempVertices = temp.getInternalVertexMap();
            MixedEdge e;
            //add in the edges from U
            for (int i = 0; i < U.size(); i++) {
                e = U.get(i);
                temp.addEdge(new MixedEdge("from U", new Pair<MixedVertex>(tempVertices.get(e.getEndpoints().getFirst().getId()), tempVertices.get(e.getEndpoints().getSecond().getId())), e.getCost(), false), i);
            }
            //now figure out the odd vertices
            ArrayList<MixedVertex> Vprime = new ArrayList<MixedVertex>();
            for (MixedVertex v : temp.getVertices()) {
                if (v.getDegree() % 2 == 1) {
                    Vprime.add(v);
                }
            }

            MixedGraph temp2 = new MixedGraph();
            TIntObjectHashMap<MixedVertex> temp2Vertices = temp2.getInternalVertexMap();
            for (int i = 1; i <= input.getVertices().size(); i++) {
                temp2.addVertex(new MixedVertex("parity graph"), i);
            }
            //add in the arcs from M''
            for (int i = 0; i < M.size(); i++) {
                if (inMdubPrime.get(i)) {
                    e = M.get(i);
                    //we add it as undirected because we ignore direction in our adjustcycles search
                    temp2.addEdge(new MixedEdge("from M", new Pair<MixedVertex>(temp2Vertices.get(e.getTail().getId()), temp2Vertices.get(e.getHead().getId())), e.getCost(), false), i);
                }
            }
            //at this point, temp 1 has edges in U, temp 2 has arcs in M, and their ids match the input's, therefore, to find
            //the alternating paths that evenparity demands, we look for paths from odd nodes to other odd nodes first in temp 2,
            //then in temp.

            //now do the adjustcycles routine, which looks for cycles consisting of alternating paths from M'' and U
            MixedVertex curr;
            HashMap<MixedVertex, ArrayList<MixedEdge>> currNeighbors;
            MixedEdge currEdge;
            TIntObjectHashMap<MixedVertex> inputVertices = input.getInternalVertexMap();
            int startId;
            if (!Vprime.isEmpty()) {
                curr = temp2Vertices.get(Vprime.remove(0).getId()); //in the M'' graph
                startId = curr.getId();

                while (!Vprime.isEmpty()) {
                    curr = temp2Vertices.get(curr.getId()); //in the M'' graph
                    //go until we get to another guy in Vprime
                    while (!Vprime.remove(tempVertices.get(curr.getId()))) {
                        currNeighbors = curr.getNeighbors(); //neighbors in M''
                        currEdge = currNeighbors.values().iterator().next().get(0); //grab anybody
                        if (currEdge.getEndpoints().getFirst().equals(curr)) //if it's directed 'forward' then add a copy
                        {
                            Mprime.add(new MixedEdge("duplicate from M''", new Pair<MixedVertex>(inputVertices.get(currEdge.getEndpoints().getFirst().getId()), inputVertices.get(currEdge.getEndpoints().getSecond().getId())), currEdge.getCost(), true));
                            curr = currEdge.getEndpoints().getSecond();
                            temp2.removeEdge(currEdge);
                        } else //if it's directed backward, remove the original
                        {
                            M.set(currEdge.getMatchId(), null);
                            inMdubPrime.set(currEdge.getMatchId(), null);
                            //M.remove(currEdge.getMatchId());
                            //inMdubPrime.remove(currEdge.getMatchId());
                            curr = currEdge.getEndpoints().getFirst();
                            temp2.removeEdge(currEdge);
                        }
                    }
                    //now look in temp, not temp2
                    curr = tempVertices.get(curr.getId());
                    while (!Vprime.remove(curr) && curr.getId() != startId) {
                        currNeighbors = curr.getNeighbors(); //neighbors in M''
                        currEdge = currNeighbors.values().iterator().next().get(0); //grab anybody
                        U.set(currEdge.getMatchId(), null); //remove it from U, and throw it into Mprime directed now
                        if (curr.equals(currEdge.getEndpoints().getFirst())) {
                            Mprime.add(new MixedEdge("directed from U", new Pair<MixedVertex>(inputVertices.get(currEdge.getEndpoints().getFirst().getId()), inputVertices.get(currEdge.getEndpoints().getSecond().getId())), currEdge.getCost(), true));
                            curr = currEdge.getEndpoints().getSecond();
                            temp.removeEdge(currEdge);
                        } else {
                            Mprime.add(new MixedEdge("directed from U", new Pair<MixedVertex>(inputVertices.get(currEdge.getEndpoints().getSecond().getId()), inputVertices.get(currEdge.getEndpoints().getFirst().getId())), currEdge.getCost(), true));
                            curr = currEdge.getEndpoints().getFirst();
                            temp.removeEdge(currEdge);
                        }
                    }
                }
            }
            //add M to M'
            for (int i = 0; i < M.size(); i++) {
                if (M.get(i) != null)
                    Mprime.add(M.get(i));
            }
            //add U to U'
            for (int i = 0; i < U.size(); i++) {
                if (U.get(i) != null)
                    Uprime.add(U.get(i));
            }

            input.clearEdges();
            for (int i = 0; i < Mprime.size(); i++) {
                input.addEdge(Mprime.get(i));
            }
            for (int i = 0; i < Uprime.size(); i++) {
                input.addEdge(Uprime.get(i));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Essentially solves the UCPP on the Mixed Graph, ignoring arc direction,
     * as in Mixed 1 of Frederickson.
     *
     * @param input - a mixed graph, which is augmented with the solution to the matching.
     */
    private static void evenDegree(MixedGraph input) {
        try {
            //set up the undirected graph, and then solve the min cost matching
            UndirectedGraph setup = new UndirectedGraph();
            for (int i = 1; i < input.getVertices().size() + 1; i++) {
                setup.addVertex(new UndirectedVertex("even setup graph"), i);
            }
            TIntObjectHashMap<UndirectedVertex> indexedVertices = setup.getInternalVertexMap();
            for (MixedEdge e : input.getEdges()) {
                setup.addEdge(new Edge("even setup graph", new Pair<UndirectedVertex>(indexedVertices.get(e.getEndpoints().getFirst().getId()), indexedVertices.get(e.getEndpoints().getSecond().getId())), e.getCost()), e.getId());
            }

            //solve shortest paths
            int n = setup.getVertices().size();
            int[][] dist = new int[n + 1][n + 1];
            int[][] path = new int[n + 1][n + 1];
            int[][] edgePath = new int[n + 1][n + 1];
            CommonAlgorithms.fwLeastCostPaths(setup, dist, path, edgePath);

            //setup the complete graph composed entirely of the unbalanced vertices
            UndirectedGraph matchingGraph = new UndirectedGraph();

            //setup our graph of unbalanced vertices
            for (UndirectedVertex v : setup.getVertices()) {
                if (v.getDegree() % 2 == 1) {
                    matchingGraph.addVertex(new UndirectedVertex("oddVertex"), v.getId());
                }
            }

            //connect with least cost edges
            Collection<UndirectedVertex> oddVertices = matchingGraph.getVertices();
            for (UndirectedVertex v : oddVertices) {
                for (UndirectedVertex v2 : oddVertices) {
                    //only add one edge per pair of vertices
                    if (v.getId() <= v2.getId())
                        continue;
                    matchingGraph.addEdge(new Edge("matchingEdge", new Pair<UndirectedVertex>(v, v2), dist[v.getMatchId()][v2.getMatchId()]));
                }
            }

            Set<Pair<UndirectedVertex>> matchingSolution = CommonAlgorithms.minCostMatching(matchingGraph);


            //now add copies in the mixed graph
            MixedEdge e;
            TIntObjectHashMap<Edge> setupEdges = setup.getInternalEdgeMap();
            for (Pair<UndirectedVertex> p : matchingSolution) {
                //add the 'undirected' shortest path
                int curr = p.getFirst().getMatchId();
                int end = p.getSecond().getMatchId();
                int next = 0;
                int nextEdge = 0;
                do {
                    next = path[curr][end];
                    nextEdge = edgePath[curr][end];
                    e = input.getInternalEdgeMap().get(setupEdges.get(nextEdge).getMatchId());
                    input.addEdge(new MixedEdge("added in phase I", new Pair<MixedVertex>(e.getEndpoints().getFirst(), e.getEndpoints().getSecond()), e.getCost(), e.isDirected()));
                } while ((curr = next) != end);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Essentially solves the DCPP on the Mixed Graph, as in Mixed 1 of Frederickson.
     *
     * @param input       - a mixed graph
     * @param U           - should be an empty ArrayList.  At the end, it will contain edges for whom we are still unsure of their orientation
     * @param M           - should be an empty ArrayList.  At the end, it will contain arcs and edges for whom we know orientations
     * @param inMdubPrime - should be an empty ArrayList.  At the end, it will be of the same size as M, and will hold true if the arc is a duplicate, and false if it's an original
     */
    private static void inOutDegree(MixedGraph input, ArrayList<MixedEdge> U, ArrayList<MixedEdge> M, ArrayList<Boolean> inMdubPrime) {
        try {
            DirectedGraph setup = new DirectedGraph();
            int n = input.getVertices().size();
            for (int i = 1; i < n + 1; i++) {
                setup.addVertex(new DirectedVertex("symmetric setup graph"), i);
            }
            Arc a;
            MixedEdge e;
            TIntObjectHashMap<MixedEdge> inputEdges = input.getInternalEdgeMap();
            TIntObjectHashMap<MixedVertex> inputVertices = input.getInternalVertexMap();
            TIntObjectHashMap<DirectedVertex> setupVertices = setup.getInternalVertexMap();
            TIntObjectHashMap<Arc> setupEdges = setup.getInternalEdgeMap();
            int m = input.getEdges().size();
            for (int i = 1; i < m + 1; i++) {
                e = inputEdges.get(i);
                if (e.isDirected()) {
                    setup.addEdge(new Arc("symmetric setup graph", new Pair<DirectedVertex>(setupVertices.get(e.getTail().getId()), setupVertices.get(e.getHead().getId())), e.getCost()), e.getId());
                } else {
                    //add two arcs; one in either direction
                    setup.addEdge(new Arc("symmetric setup graph", new Pair<DirectedVertex>(setupVertices.get(e.getEndpoints().getFirst().getId()), setupVertices.get(e.getEndpoints().getSecond().getId())), e.getCost()), e.getId());
                    setup.addEdge(new Arc("symmetric setup graph", new Pair<DirectedVertex>(setupVertices.get(e.getEndpoints().getSecond().getId()), setupVertices.get(e.getEndpoints().getFirst().getId())), e.getCost()), e.getId());
                    //add two arcs that we get for free, but only have capacity 1 for when we solve the min cost flow
                    a = new Arc("symmetric setup graph", new Pair<DirectedVertex>(setupVertices.get(e.getEndpoints().getFirst().getId()), setupVertices.get(e.getEndpoints().getSecond().getId())), 0);
                    a.setCapacity(1);
                    setup.addEdge(a, e.getId());
                    a = new Arc("symmetric setup graph", new Pair<DirectedVertex>(setupVertices.get(e.getEndpoints().getSecond().getId()), setupVertices.get(e.getEndpoints().getFirst().getId())), 0);
                    a.setCapacity(1);
                    setup.addEdge(a, e.getId());

                }
            }

            //prepare our unbalanced vertex sets
            HashSet<DirectedVertex> setupVertexSet = setup.getVertices();
            for (DirectedVertex v : setupVertexSet) {
                if (v.getDelta() != 0) {
                    v.setDemand(v.getDelta());
                }
            }

            //solve the min-cost flow
            int[] flowanswer = CommonAlgorithms.shortestSuccessivePathsMinCostNetworkFlow(setup);

            //build M and U
            /*
             * the ith entry will be 1 if the flow solution included an arc along the ith edge (of input), (only
			 * meaningful for undirected edges) from tail to head; -1 if it included one from head to tail.
			 * This enables us to determine which are still left unoriented by this phase.
			 */
            int[] undirTraversals = new int[m + 1];
            int setupM = setup.getEdges().size();
            MixedVertex temp;
            //iterate through flow solution, and add appropriate number of guys
            for (int i = 1; i < setupM + 1; i++) {
                e = inputEdges.get(setupEdges.get(i).getMatchId());
                a = setupEdges.get(i);
                if (e.isDirected()) {
                    //add back the original
                    M.add(new MixedEdge("copy from symmetry", new Pair<MixedVertex>(e.getTail(), e.getHead()), e.getCost(), true));
                    inMdubPrime.add(false);
                    undirTraversals[e.getId()] = 2;
                    //e
                    for (int j = 0; j < flowanswer[i]; j++) {
                        //add copy to M
                        M.add(new MixedEdge("copy from symmetry", new Pair<MixedVertex>(e.getTail(), e.getHead()), e.getCost(), true));
                        inMdubPrime.add(true);
                        //f
                    }
                } else if (!a.isCapacitySet()) //arc corresponding to an edge, but not artificial
                {
                    for (int j = 0; j < flowanswer[i]; j++) {
                        //add copy to M
                        M.add(new MixedEdge("copy from symmetry", new Pair<MixedVertex>(inputVertices.get(a.getTail().getId()), inputVertices.get(a.getHead().getId())), e.getCost(), true));
                        inMdubPrime.add(true);
                        //c
                    }
                } else //artificial arc corresponding to an edge
                {
                    if (flowanswer[i] == 0)
                        continue;
                    temp = inputVertices.get(a.getTail().getId());
                    if (temp.equals(e.getEndpoints().getFirst())) // arc is 'forward'
                    {
                        //update undirTraversals
                        if (undirTraversals[e.getId()] == 0)
                            undirTraversals[e.getId()] = 1;
                        else // was already -1, so we have traversal in both directions, so add to U, we don't know
                        {
                            U.add(new MixedEdge("copy from symmetry", new Pair<MixedVertex>(e.getEndpoints().getFirst(), e.getEndpoints().getSecond()), e.getCost(), false));
                            undirTraversals[e.getId()] = 2; //so we don't add it to M again later
                            //a
                        }
                    } else // arc is backward
                    {
                        //update undirTraversals
                        if (undirTraversals[e.getId()] == 0)
                            undirTraversals[e.getId()] = -1;
                        else // was already 1, so we have traversal in both directions, so add to U, we don't know
                        {
                            U.add(new MixedEdge("copy from symmetry", new Pair<MixedVertex>(e.getEndpoints().getFirst(), e.getEndpoints().getSecond()), e.getCost(), false));
                            undirTraversals[e.getId()] = 2;
                            //a
                        }
                    }
                }
            }

            //now just go through, and any undirTraversal entries of 1 should be added forward, -1 should be added backward
            int undirLength = undirTraversals.length;
            for (int i = 1; i < undirLength; i++) {
                e = inputEdges.get(i);
                if (undirTraversals[i] == 0) {
                    U.add(new MixedEdge("copy from symmetry", new Pair<MixedVertex>(e.getEndpoints().getFirst(), e.getEndpoints().getSecond()), e.getCost(), false));
                    //a
                } else if (undirTraversals[i] == 1) //add a forward copy
                {
                    M.add(new MixedEdge("copy from symmetry", new Pair<MixedVertex>(e.getEndpoints().getFirst(), e.getEndpoints().getSecond()), e.getCost(), true));
                    inMdubPrime.add(false);
                    //b unless already c
                } else if (undirTraversals[i] == -1) //add a backwards copy
                {
                    M.add(new MixedEdge("copy from symmetry", new Pair<MixedVertex>(e.getEndpoints().getSecond(), e.getEndpoints().getFirst()), e.getCost(), true));
                    inMdubPrime.add(false);
                    //b unless already c
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected Collection<Tour> solve() {
        try {

            MixedGraph ans1 = mInstance.getGraph().getDeepCopy(); //starting point for Mixed1
            MixedGraph ans2 = mInstance.getGraph().getDeepCopy(); //starting point for Mixed2

            //Vars for bookkeeping
            ArrayList<MixedEdge> U = new ArrayList<MixedEdge>(); //will hold the list of edges that are still undirected after inOutDegree
            ArrayList<MixedEdge> M = new ArrayList<MixedEdge>(); //will hold the list of directed edges after inOutDegree
            ArrayList<Boolean> inMdubPrime = new ArrayList<Boolean>(); // will hold the list of copies of directed edges after inOutDegree

            //Start Mixed 1
            /*
             * This procedure aims to make the mixed graph even; that is, for each vertex v, v.getDegree() % 2 == 0.
			 */
            evenDegree(ans1);

			/*
             * This procedure aims to make the mixed graph symmetric; that is, for each vertex v, v.getInDegree() == v.getOutDegree().
			 * Note that this may disrupt the property of every vertex being even.
			 */
            inOutDegree(ans1, U, M, inMdubPrime);

			/*
			 * This procedure restores evenness to the graph, without disturbing the symmetric property of the graph.
			 */
            evenParity(ans1, U, M, inMdubPrime);
            //End Mixed 1

            //Start Mixed 2

            //Vars for bookkeeping
            U = new ArrayList<MixedEdge>();
            M = new ArrayList<MixedEdge>();
            inMdubPrime = new ArrayList<Boolean>();

			/*
			 * This procedure aims to make the mixed graph symmetric; that is, for each vertex v, v.getInDegree() == v.getOutDegree().
			 * Note that this may disrupt the property of every vertex being even.
			 */
            inOutDegree(ans2, U, M, inMdubPrime);

			/*
			 * This procedure aims to restore evenness to the graph by performing a matching on the graph induced
			 * by the edges left undirected after inOutDegree
			 */
            largeCycles(ans2, U);
            ans2.clearEdges();


            int mSize, uSize;
            mSize = M.size();
            uSize = U.size();
            for (int i = 0; i < mSize; i++) {
                ans2.addEdge(M.get(i));
            }
            for (int i = 0; i < uSize; i++) {
                ans2.addEdge(U.get(i));
            }
            //End Mixed 2

            //select the lower cost of the two
            int cost1 = 0;
            int cost2 = 0;
            for (MixedEdge temp : ans1.getEdges()) {
                cost1 += temp.getCost();
            }
            for (MixedEdge temp : ans2.getEdges()) {
                cost2 += temp.getCost();
            }

            ArrayList<Integer> tour;
            Tour<MixedVertex, MixedEdge> eulerTour = new Tour<MixedVertex, MixedEdge>();
            if (cost1 <= cost2) {
                LOGGER.debug("ans1 chosen: " + CommonAlgorithms.isStronglyConnected(ans1));
                tour = CommonAlgorithms.tryHierholzer(ans1);
                TIntObjectHashMap<MixedEdge> indexedEdges = ans1.getInternalEdgeMap();
                int tourSize = tour.size();
                for (int i = 0; i < tourSize; i++) {
                    eulerTour.appendEdge(indexedEdges.get(tour.get(i)));
                }
            } else {
                LOGGER.debug("ans2 chosen: " + CommonAlgorithms.isStronglyConnected(ans2));
                tour = CommonAlgorithms.tryHierholzer(ans2);
                TIntObjectHashMap<MixedEdge> indexedEdges = ans2.getInternalEdgeMap();
                int tourSize = tour.size();
                for (int i = 0; i < tourSize; i++) {
                    eulerTour.appendEdge(indexedEdges.get(tour.get(i)));
                }
            }
            mInstance.setSol(eulerTour);

            HashSet<Tour> ret = new HashSet<Tour>();
            ret.add(eulerTour);
            return ret;

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public ProblemAttributes getProblemAttributes() {
        return new ProblemAttributes(Graph.Type.MIXED, ProblemAttributes.Type.CHINESE_POSTMAN, ProblemAttributes.NumVehicles.SINGLE_VEHICLE, ProblemAttributes.NumDepots.SINGLE_DEPOT, null);
    }

    @Override
    public String getSolverName() {
        return "Frederickson's Mixed Chinese Postman Heuristic";
    }

    @Override
    public HashMap<String, Double> getProblemParameters() {
        return new HashMap<String, Double>();
    }

    @Override
    public Solver<MixedVertex, MixedEdge, MixedGraph> instantiate(Problem p) {
        return new MCPPSolver_Frederickson(p);
    }

    @Override
    protected Problem<MixedVertex, MixedEdge, MixedGraph> getInstance() {
        return mInstance;
    }

    @Override
    protected boolean checkGraphRequirements() {
        // make sure the graph is connected
        if (mInstance.getGraph() == null)
            return false;
        else {
            MixedGraph mGraph = mInstance.getGraph();
            if (!CommonAlgorithms.isStronglyConnected(mGraph))
                return false;
        }
        return true;
    }

}
