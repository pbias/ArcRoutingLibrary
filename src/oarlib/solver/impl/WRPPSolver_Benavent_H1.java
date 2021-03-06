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
import oarlib.display.GraphDisplay;
import oarlib.graph.impl.DirectedGraph;
import oarlib.graph.impl.UndirectedGraph;
import oarlib.graph.impl.WindyGraph;
import oarlib.graph.util.CommonAlgorithms;
import oarlib.graph.util.Pair;
import oarlib.graph.util.Utils;
import oarlib.link.impl.Arc;
import oarlib.link.impl.Edge;
import oarlib.link.impl.WindyEdge;
import oarlib.problem.impl.ProblemAttributes;
import oarlib.route.impl.Tour;
import oarlib.vertex.impl.DirectedVertex;
import oarlib.vertex.impl.UndirectedVertex;
import oarlib.vertex.impl.WindyVertex;

import javax.swing.*;
import java.util.*;

public class WRPPSolver_Benavent_H1 extends SingleVehicleSolver<WindyVertex, WindyEdge, WindyGraph> {

    private static final double K = .2; //parameter fixed by computational experiments done by Benavent
    private boolean exportSolToPDF;

    public WRPPSolver_Benavent_H1(Problem<WindyVertex, WindyEdge, WindyGraph> instance) throws IllegalArgumentException {
        this(instance, false);
    }

    public WRPPSolver_Benavent_H1(Problem<WindyVertex, WindyEdge, WindyGraph> instance, boolean exportToPDF) throws IllegalArgumentException {
        super(instance);
        exportSolToPDF = exportToPDF;
    }


    public static void eulerAugment(WindyGraph fullGraph, WindyGraph g, HashSet<Integer> L) {

        try {
            int n = fullGraph.getVertices().size();
            int m = fullGraph.getEdges().size();

            //alter the distances in fullGraph; setting the L guys to 0
            WindyGraph fullGraphCopy = new WindyGraph();
            for (int i = 1; i <= n; i++) {
                fullGraphCopy.addVertex(new WindyVertex("orig"));
            }
            TIntObjectHashMap<WindyEdge> fullGraphEdges = fullGraph.getInternalEdgeMap();
            WindyEdge temp;
            for (int i = 1; i <= m; i++) {
                temp = fullGraphEdges.get(i);
                if (L.contains(i)) //add with zero cost
                    fullGraphCopy.addEdge(temp.getEndpoints().getFirst().getId(), temp.getEndpoints().getSecond().getId(), "orig", 0, 0);
                else
                    fullGraphCopy.addEdge(temp.getEndpoints().getFirst().getId(), temp.getEndpoints().getSecond().getId(), "orig", temp.getCost(), temp.getReverseCost());
            }

            int[] dist = new int[n + 1];
            int[] path = new int[n + 1];
            int[] edgePath = new int[n + 1];

            //setup the complete graph composed entirely of the unbalanced vertices
            UndirectedGraph matchingGraph = new UndirectedGraph();

            //setup our graph of unbalanced vertices
            for (WindyVertex v : g.getVertices()) {
                if (v.getDegree() % 2 == 1) {
                    matchingGraph.addVertex(new UndirectedVertex("oddVertex"), v.getId());
                }
            }

            //connect with least cost edges
            Collection<UndirectedVertex> oddVertices = matchingGraph.getVertices();
            HashMap<Pair<Integer>, Edge> traverseIj = new HashMap<Pair<Integer>, Edge>(); //key is (i,j) where i < j, and value is true if the shortest average path cost is i to j, false if it's j to i
            double costCandidate;
            Pair<Integer> candidateKey;
            for (UndirectedVertex v : oddVertices) {
                CommonAlgorithms.dijkstrasAlgorithm(fullGraphCopy, v.getMatchId(), dist, path, edgePath);
                for (UndirectedVertex v2 : oddVertices) {
                    //only add one edge per pair of vertices
                    if (v.getId() == v2.getId())
                        continue;

                    costCandidate = calculateAveragePathCost(fullGraphCopy, v.getMatchId(), v2.getMatchId(), path, edgePath);
                    candidateKey = new Pair<Integer>(v2.getId(), v.getId());

                    if (!traverseIj.containsKey(candidateKey) || costCandidate < traverseIj.get(candidateKey).getCost()) {
                        traverseIj.remove(candidateKey);
                        traverseIj.put(new Pair<Integer>(v.getId(), v2.getId()), new Edge("matchingEdge", new Pair<UndirectedVertex>(v, v2), (int) (2 * costCandidate)));
                    }
                }
            }

            for (Edge e : traverseIj.values())
                matchingGraph.addEdge(e);


            Set<Pair<UndirectedVertex>> matchingSolution = CommonAlgorithms.minCostMatching(matchingGraph);

            //now add the corresponding edges back in the windy graph
            int curr, end, next, nextEdge;
            TIntObjectHashMap<WindyEdge> indexedEdges = fullGraph.getInternalEdgeMap();
            for (Pair<UndirectedVertex> p : matchingSolution) {
                //minCostMatching doesn't discriminate between 1 - 2 and 2 - 1 so we need to
                if (traverseIj.containsKey(new Pair<Integer>(p.getFirst().getId(), p.getSecond().getId()))) {
                    curr = p.getFirst().getMatchId();
                    end = p.getSecond().getMatchId();
                } else {
                    curr = p.getSecond().getMatchId();
                    end = p.getFirst().getMatchId();
                }

                CommonAlgorithms.dijkstrasAlgorithm(fullGraphCopy, curr, dist, path, edgePath);

                do {
                    next = path[end];
                    nextEdge = edgePath[end];
                    temp = indexedEdges.get(nextEdge);
                    //g.addEdge(temp.getEndpoints().getFirst().getId(), temp.getEndpoints().getSecond().getId(), "to make even", temp.getCost(), temp.getReverseCost(), nextEdge, temp.isRequired());
                    g.addEdge(temp.getEndpoints().getFirst().getId(), temp.getEndpoints().getSecond().getId(), "to make even", temp.getCost(), temp.getReverseCost(), nextEdge, false);
                } while ((end = next) != curr);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    public static HashSet<Integer> buildL(DirectedGraph gaux, HashSet<Integer> e1, HashSet<Integer> e2, int[] flowanswer) {
        HashSet<Integer> ans = new HashSet<Integer>();
        Arc temp;
        TIntObjectHashMap<Arc> indexedArcs = gaux.getInternalEdgeMap();
        int tempMatchId;
        for (int i = 1; i < flowanswer.length; i++) {
            temp = indexedArcs.get(i);
            tempMatchId = temp.getMatchId();
            if (temp.isCapacitySet())
                continue;
            if (flowanswer[i] >= 1 && e1.contains(tempMatchId)) //in e1, and flow >= 1
                ans.add(tempMatchId);
            else if (flowanswer[i] >= 2 && e2.contains(tempMatchId))
                ans.add(tempMatchId);
        }
        return ans;
    }

    public static DirectedGraph buildGdr(WindyGraph g, HashSet<Integer> unbalancedEdges) {
        try {
            DirectedGraph ans = new DirectedGraph();
            //the vertex set is the same as g
            int n = g.getVertices().size();
            for (int i = 1; i <= n; i++) {
                ans.addVertex(new DirectedVertex("Gdr"));
            }

            TIntObjectHashMap<WindyEdge> indexedEdges = g.getInternalEdgeMap();
            WindyEdge temp;
            //add an arc in the cheaper direction of the unbalanced edges
            for (Integer id : unbalancedEdges) {
                temp = indexedEdges.get(id);
                if (temp.getCost() < temp.getReverseCost()) {
                    ans.addEdge(temp.getEndpoints().getFirst().getId(), temp.getEndpoints().getSecond().getId(), "Gdr", temp.getCost());
                } else {
                    ans.addEdge(temp.getEndpoints().getSecond().getId(), temp.getEndpoints().getFirst().getId(), "Gdr", temp.getReverseCost());
                }
            }
            return ans;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static DirectedGraph buildGaux(WindyGraph fullGraph, HashSet<Integer> unbalancedEdges) {
        try {
            DirectedGraph ans = new DirectedGraph();
            int n = fullGraph.getVertices().size();
            for (int i = 1; i <= n; i++) {
                ans.addVertex(new DirectedVertex("Gaux"));
            }

            //put in an arc for each of the edges in g
            int i, j, tempCost;
            for (WindyEdge e : fullGraph.getEdges()) {
                i = e.getEndpoints().getFirst().getId();
                j = e.getEndpoints().getSecond().getId();
                //double cost so we can keep everything integer
                ans.addEdge(i, j, "Gaux", 2 * e.getCost(), e.getId());
                ans.addEdge(j, i, "Gaux", 2 * e.getReverseCost(), e.getId());
            }

            WindyEdge temp;
            TIntObjectHashMap<WindyEdge> indexedEdges = fullGraph.getInternalEdgeMap();
            TIntObjectHashMap<DirectedVertex> indexedVertices = ans.getInternalVertexMap();
            Arc toAdd;
            //add an arc in the high cost direction for each of the unbalanced edges
            for (Integer id : unbalancedEdges) {
                temp = indexedEdges.get(id);
                i = temp.getEndpoints().getFirst().getId();
                j = temp.getEndpoints().getSecond().getId();

                if (temp.getCost() < temp.getReverseCost()) {
                    tempCost = temp.getReverseCost() - temp.getCost();
                    toAdd = new Arc("Gaux", new Pair<DirectedVertex>(indexedVertices.get(j), indexedVertices.get(i)), tempCost);
                    toAdd.setCapacity(2);
                    ans.addEdge(toAdd);
                } else {
                    tempCost = temp.getCost() - temp.getReverseCost();
                    toAdd = new Arc("Gaux", new Pair<DirectedVertex>(indexedVertices.get(i), indexedVertices.get(j)), tempCost);
                    toAdd.setCapacity(2);
                    ans.addEdge(toAdd);
                }

            }
            return ans;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static void buildEdgeSets(HashSet<Integer> e1, HashSet<Integer> e2, WindyGraph windyReq, WindyGraph fullGraph, double averageCost) {
        double costDiff;
        double threshold = K * averageCost;
        for (WindyEdge e : windyReq.getEdges()) {
            costDiff = Math.abs(e.getCost() - e.getReverseCost());
            if (costDiff > threshold)
                e1.add(e.getMatchId());
        }
        for (WindyEdge e : fullGraph.getEdges()) {
            if (!e1.contains(e.getId()))
                e2.add(e.getId());
        }
    }

    public static double calculateAverageCost(WindyGraph g) {
        double ans = 0;
        double m = 2.0 * g.getEdges().size();
        for (WindyEdge e : g.getEdges()) {
            ans += (e.getCost() + e.getReverseCost());
        }
        return ans / m;
    }

    private static double calculateAveragePathCost(WindyGraph g, int i, int j, int[][] path, int[][] edgePath) {
        int curr, end, next, ans;
        curr = i;
        end = j;
        ans = 0;
        WindyEdge temp;
        TIntObjectHashMap<WindyEdge> indexedWindyEdges = g.getInternalEdgeMap();
        do {
            next = path[curr][end];
            temp = indexedWindyEdges.get(edgePath[curr][end]);
            ans += temp.getCost() + temp.getReverseCost();

        } while ((curr = next) != end);
        return ans / 2.0;
    }

    private static double calculateAveragePathCost(WindyGraph g, int i, int j, int[] path, int[] edgePath) {
        int start, end, next, ans;
        start = i;
        end = j;
        ans = 0;
        WindyEdge temp;
        TIntObjectHashMap<WindyEdge> indexedWindyEdges = g.getInternalEdgeMap();
        do {
            next = path[end];
            temp = indexedWindyEdges.get(edgePath[end]);
            ans += temp.getCost() + temp.getReverseCost();
        } while ((end = next) != start);
        return ans / 2.0;
    }

    @Override
    protected Problem getInstance() {
        return mInstance;
    }

    @Override
    protected Collection<Tour> solve() {
        try {
            WindyGraph copy = mInstance.getGraph().getDeepCopy();

            /*
             * Connect up the required components of the graph, just as in WRPP1.
			 * Match ids in windyReq correspond to edge ids in copy after this.
			 */
            WindyGraph windyReq = WRPPSolver_Win.connectRequiredComponents(copy);

            //calculate average cost of edges in Er', so add up (cij + cji)/2, and then divide by num edges of windyReq
            double averageCost = calculateAverageCost(windyReq);

            //construct E1 and E2
            HashSet<Integer> E1 = new HashSet<Integer>();
            HashSet<Integer> E2 = new HashSet<Integer>();

			/*
             * Build out the edge sets E1 and E2, which hold the particularly asymmetric edges from windyReq, and
			 * the everyone else respectively. We do so by searching windyReq for asymmetric edges, and adding their
			 * match ids (ids in copy) to E1.  Then we go through copy's edges, and add the rest to E2.
			 */
            buildEdgeSets(E1, E2, windyReq, copy, averageCost);

			/*
             * Build Gdr, which is a graph that has only the edges in E1 in it as arcs directed in the cheap direction.
			 * The purpose of doing so is to set demands for the flow problem we're about to solve.
			 */
            DirectedGraph Gdr = buildGdr(copy, E1);
            HashSet<Integer> L = new HashSet<Integer>();
            if (!CommonAlgorithms.isEulerian(Gdr)) {

				/*
                 * Build Gaux, which is a graph that is the directed graph induced by copy, PLUS
				 * an extra arc for each edge in E1 in the expensive direction, with cost = (cji - cij)/2.
				 * The match ids here will be ids in copy for the inf. capacity arcs, and -1 for the
				 * artificial ones.
				 */
                DirectedGraph Gaux = buildGaux(copy, E1);

                //set up the flow problem on Gaux using demands from Gdr
                TIntObjectHashMap<DirectedVertex> indexedVertices = Gdr.getInternalVertexMap();
                for (DirectedVertex v : Gaux.getVertices()) {
                    v.setDemand(indexedVertices.get(v.getId()).getDelta());
                }


                //solve the flow problem on Gaux with demands from Gdr
                int flowanswer[] = CommonAlgorithms.shortestSuccessivePathsMinCostNetworkFlow(Gaux);

				/*
				 * Create a list of ids L (in copy) which represent guys that are likely to appear in the min cost flow
				 * solution we solve to construct the optimal windy tour.
				 */
                L = buildL(Gaux, E1, E2, flowanswer);
            }
			/*
			 * Perform the same euler augmentation process the same as in WRPP1, except that this time,
			 * when we solve the matching, we want the edges we marked in L to be of zero cost
			 * to coerce the matching to use these.
			 */
            eulerAugment(copy, windyReq, L);
            DirectedGraph ans = WRPPSolver_Win.constructOptimalWindyTour(windyReq);

            ans.setDepotId(copy.getDepotId());
            WRPPSolver_Win.eliminateRedundantCycles(ans, windyReq, copy);
            WRPPSolver_Win.repairSolution(ans, copy);

            ArrayList<Integer> tour;
            tour = CommonAlgorithms.tryHierholzer(ans);

            Tour<DirectedVertex, Arc> eulerTour = new Tour<DirectedVertex, Arc>();
            TIntObjectHashMap<Arc> indexedEdges = ans.getInternalEdgeMap();
            for (int i = 0; i < tour.size(); i++) {
                eulerTour.appendEdge(indexedEdges.get(tour.get(i)));
            }

            mInstance.setSol(Utils.reclaimTour(eulerTour, mInstance.getGraph()));

            /*visualize it
            if (exportSolToPDF) {
                try {
                    GraphDisplay gd = new GraphDisplay(GraphDisplay.Layout.YifanHu, ans, mInstance.getName() + "_" + eulerTour.getCost());
                    gd.export(GraphDisplay.ExportType.PDF);

                } catch (Exception e) {
                    e.printStackTrace();
                    return null;
                }
            }*/

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
        return new ProblemAttributes(Graph.Type.WINDY, null, ProblemAttributes.NumVehicles.SINGLE_VEHICLE, ProblemAttributes.NumDepots.SINGLE_DEPOT, null);
    }

    @Override
    public String getSolverName() {
        return "Benavent's H1 Heuristic for the Windy Rural Postman Problem";
    }

    @Override
    public Solver<WindyVertex, WindyEdge, WindyGraph> instantiate(Problem<WindyVertex, WindyEdge, WindyGraph> p) {
        return new WRPPSolver_Benavent_H1(p);
    }

    @Override
    public HashMap<String, Double> getProblemParameters() {
        return new HashMap<String, Double>();
    }

    @Override
    protected boolean checkGraphRequirements() {
        // make sure the graph is connected
        if (mInstance.getGraph() == null)
            return false;
        else {
            WindyGraph mGraph = mInstance.getGraph();
            if (!CommonAlgorithms.isConnected(mGraph))
                return false;
        }
        return true;
    }


}
