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
import oarlib.core.*;
import oarlib.display.GraphDisplay;
import oarlib.graph.impl.DirectedGraph;
import oarlib.graph.impl.WindyGraph;
import oarlib.graph.util.CommonAlgorithms;
import oarlib.graph.util.IndexedRecord;
import oarlib.graph.util.Utils;
import oarlib.improvements.metaheuristics.impl.BenaventIPFramework;
import oarlib.link.impl.Arc;
import oarlib.link.impl.WindyEdge;
import oarlib.metrics.AverageTraversalMetric;
import oarlib.metrics.RouteOverlapMetric;
import oarlib.problem.impl.ProblemAttributes;
import oarlib.problem.impl.rpp.WindyRPP;
import oarlib.route.impl.Tour;
import oarlib.vertex.impl.DirectedVertex;
import oarlib.vertex.impl.WindyVertex;
import org.apache.log4j.Logger;

import java.util.*;

/**
 * Created by oliverlum on 10/17/14.
 */
public class MultiWRPPSolver_Benavent extends MultiVehicleSolver<WindyVertex, WindyEdge, WindyGraph> {

    private static final Logger LOGGER = Logger.getLogger(MultiWRPPSolver_Benavent.class);
    WindyGraph mGraph;
    String mInstanceName;

    /**
     * Default constructor; must set problem instance.
     *
     * @param instance - instance for which this is a solver
     */
    public MultiWRPPSolver_Benavent(Problem<WindyVertex, WindyEdge, WindyGraph> instance) throws IllegalArgumentException {
        this(instance, "");
    }

    public MultiWRPPSolver_Benavent(Problem<WindyVertex, WindyEdge, WindyGraph> instance, String instanceName) throws IllegalArgumentException {
        super(instance);
        mGraph = mInstance.getGraph();
        mInstanceName = instanceName;
    }

    public static Collection<Route<DirectedVertex, Arc>> splitRoute(Route<DirectedVertex, Arc> singleAns, WindyGraph graph, int numVehicles) {

        try {
            //Compile the ordered list of required edges.

            ArrayList<Boolean> service = singleAns.getServicingList();

            ArrayList<Arc> orderedReqEdges = new ArrayList<Arc>();
            List<Arc> singleRoute = singleAns.getPath();
            int singleRouteSize = singleRoute.size();
            for (int i = 0; i < singleRouteSize; i++) {
                if (service.get(i))
                    orderedReqEdges.add(singleRoute.get(i));
            }

            //Compute shortest path distances in the full graph
            int n = graph.getVertices().size();
            int[][] dist = new int[n + 1][n + 1];
            int[][] path = new int[n + 1][n + 1];

            CommonAlgorithms.fwLeastCostPaths(graph, dist, path);

        /*
         * Compute the acyclic digraph H in which an edge (i-1,j) represents the cost of having a
         * tour that traverses edges i through j, and starts and ends at the depot
         */
            int m = orderedReqEdges.size();
            int tempCost;
            int depotId = graph.getDepotId();
            int maxtempCost = Integer.MIN_VALUE;
            DirectedGraph H = new DirectedGraph(m + 1);
            int prevEnd, nextStart;
            for (int i = 1; i <= m; i++) {
                for (int j = i; j <= m; j++) {
                    tempCost = 0;
                    for (int k = i; k <= j; k++) {

                        //add the cost of getting here
                        if (k > 1) {
                            prevEnd = orderedReqEdges.get(k - 2).getHead().getId();
                            nextStart = orderedReqEdges.get(k - 1).getTail().getId();
                            if (prevEnd != nextStart)
                                tempCost += dist[prevEnd][nextStart];
                        }
                        //add the cost of this req. edge
                        tempCost += orderedReqEdges.get(k - 1).getCost();

                    }
                    tempCost += dist[depotId][orderedReqEdges.get(i - 1).getTail().getId()];
                    tempCost += dist[orderedReqEdges.get(j - 1).getHead().getId()][depotId];
                    H.addEdge(i, j + 1, tempCost);
                    if (tempCost > maxtempCost)
                        maxtempCost = tempCost;
                }
            }

            //transform the graph so that when we solve the widest path problem, we get the min-max shortest path
            for (Arc a : H.getEdges())
                a.setCost(maxtempCost - a.getCost());

            //calculate a min-max shortest path problem from 1 to m+1 in H.
            IndexedRecord<Integer>[] width = new IndexedRecord[m + 2];
            IndexedRecord<Integer>[] widestPath = new IndexedRecord[m + 2];
            IndexedRecord<Integer>[] widestEdgePath = new IndexedRecord[m + 2];
            CommonAlgorithms.dijkstrasWidestPathAlgorithmWithMaxPathCardinality(H, 1, width, widestPath, widestEdgePath, numVehicles);

            //now construct the routes
            Stack<Integer> stoppingPoints = new Stack<Integer>();
            int prev = m + 1;
            int numSteps = width[prev].getRecordKey();
            do {
                stoppingPoints.push(prev);
                prev = widestPath[prev].getEntry(numSteps--);
            } while (prev != 1);

            ArrayList<Route<DirectedVertex, Arc>> ans = new ArrayList<Route<DirectedVertex, Arc>>();
            int counter = 0;
            int singleRouteCounter = 0;
            int curr, next, end, cost;
            Arc linkToAdd;
            TIntObjectHashMap<WindyVertex> mVertices = graph.getInternalVertexMap();
            int partition = 1;
            do {
                DirectedGraph toAddGraph = new DirectedGraph();
                for (int i = 1; i <= n; i++) {
                    DirectedVertex toAdd = new DirectedVertex("");
                    toAdd.setCoordinates(mVertices.get(i).getX(), mVertices.get(i).getY());
                    toAddGraph.addVertex(toAdd);
                }

                toAddGraph.setDepotId(graph.getDepotId());

                //add path from depot to start
                curr = graph.getDepotId();
                end = orderedReqEdges.get(counter).getTail().getId();

                do {
                    next = path[curr][end];
                    cost = dist[curr][next];
                    toAddGraph.addEdge(curr, next, cost, false);
                } while ((curr = next) != end);

                //increment singleRouteCounter to catch up
                while (singleRoute.get(singleRouteCounter).getId() != orderedReqEdges.get(counter).getId()) {
                    singleRouteCounter++;
                }

                //add guys from single route
                end = stoppingPoints.pop();
                boolean required;
                //while (counter != end - 2) {
                while (singleRoute.get(singleRouteCounter).getId() != orderedReqEdges.get(end - 2).getId()) {
                    //add singleRoute.get(singleRouteCounter) to the path
                    linkToAdd = singleRoute.get(singleRouteCounter);
                    required = service.get(singleRouteCounter);
                    toAddGraph.addEdge(linkToAdd.getEndpoints().getFirst().getId(), linkToAdd.getEndpoints().getSecond().getId(), linkToAdd.getCost(), required);
                    singleRouteCounter++;
                }

                //add singleRoute.get(singleRouteCounter) to the path
                linkToAdd = singleRoute.get(singleRouteCounter);
                toAddGraph.addEdge(linkToAdd.getEndpoints().getFirst().getId(), linkToAdd.getEndpoints().getSecond().getId(), linkToAdd.getCost(), linkToAdd.isRequired());
                counter = end - 2;

                //add path from end to depot
                curr = orderedReqEdges.get(counter).getHead().getId();
                end = graph.getDepotId();
                do {
                    next = path[curr][end];
                    cost = dist[curr][next];
                    toAddGraph.addEdge(curr, next, cost, false);
                } while ((curr = next) != end);

                //add the route
                ArrayList<Integer> tour = CommonAlgorithms.tryHierholzer(toAddGraph);
                TIntObjectHashMap<Arc> indexedArcs = toAddGraph.getInternalEdgeMap();
                Tour<DirectedVertex, Arc> toAdd = new Tour<DirectedVertex, Arc>();
                for (int i = 0; i < tour.size(); i++) {
                    toAdd.appendEdge(indexedArcs.get(tour.get(i)));
                }
                ans.add(toAdd);

                counter++;
                partition++;

            } while (!stoppingPoints.empty());

            int max = 0;
            for (Route r : ans) {
                if (r.getCost() > max)
                    max = r.getCost();
            }

            return ans;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    protected boolean checkGraphRequirements() {
        //make sure the graph is connected
        if (mInstance.getGraph() == null)
            return false;
        else {
            WindyGraph mGraph = mInstance.getGraph();
            if (!CommonAlgorithms.isConnected(mGraph))
                return false;
        }
        return true;
    }

    @Override
    protected Problem<WindyVertex, WindyEdge, WindyGraph> getInstance() {
        return mInstance;
    }

    @Override
    protected Collection<Route<WindyVertex, WindyEdge>> solve() {

        int nsol = 10; //number of sols to gen
        double bestObj = Integer.MAX_VALUE;
        Collection<Route<WindyVertex, WindyEdge>> ans = null;
        HashMap<Integer, Integer> bestSol = new HashMap<Integer, Integer>();

        for (int MS = 0; MS < nsol; MS++) {
            //Solve the single-vehicle WRPP instance, and then split it into K routes by the process discussed in Lacomme, Prins, and Ramdane-Cherif
            WindyGraph copy = mGraph.getDeepCopy();

        /*
         * Solve the WRPP instance
         */
            WindyRPP singleProblem = new WindyRPP(copy);
            WRPPSolver_Benavent_H1 singleSolver = new WRPPSolver_Benavent_H1(singleProblem);
            Collection<Tour> singleAns = singleSolver.solve();

        /*
         * Display it
         */
            try {
                GraphDisplay gd = new GraphDisplay(GraphDisplay.Layout.YifanHu, copy, mInstanceName + "_1vehRoute");
                Vertex temp = copy.getVertex(copy.getDepotId());
                gd.export(GraphDisplay.ExportType.PDF);
            } catch (Exception e) {
                e.printStackTrace();
                LOGGER.error("Error displaying graph.");
            }

        /*
         * now split it
         */
            Collection<Route<DirectedVertex, Arc>> multiAns = splitRoute(singleAns.iterator().next(), mInstance.getGraph(), mInstance.getmNumVehicles());

            ArrayList<Route<WindyVertex, WindyEdge>> initialSol = new ArrayList<Route<WindyVertex, WindyEdge>>();
            for (Route<DirectedVertex, Arc> r : multiAns) {
                initialSol.add(Utils.reclaimTour(r, mGraph));
            }

            mInstance.setSol(initialSol);

            BenaventIPFramework improver = new BenaventIPFramework(mInstance);

            Collection<Route<WindyVertex, WindyEdge>> improvedFinalAns = improver.improveSolution();


            HashMap<Integer, Integer> sol = new HashMap<Integer, Integer>();
            //init
            //for(WindyEdge we : mGraph.getEdges())
            //sol.put(we.getId(), 1);
            int counter = 1;
            for (Route<WindyVertex, WindyEdge> r : improvedFinalAns) {

                List<WindyEdge> path = r.getPath();
                ArrayList<Boolean> service = r.getServicingList();
                for (int i = 0; i < path.size(); i++) {
                    if (service.get(i))
                        sol.put(path.get(i).getId(), counter);
                }
                counter++;
            }

            double improvedObj = mInstance.getObjectiveFunction().evaluate(improvedFinalAns);
            if (improvedObj < bestObj) {
                ans = improvedFinalAns;
                bestObj = improvedObj;
                bestSol = sol;
            }
        }

        ArrayList<Route<WindyVertex, WindyEdge>> reclaimedAns = new ArrayList<Route<WindyVertex, WindyEdge>>();
        for (Route r : ans)
            reclaimedAns.add(Utils.reclaimTour(r, mGraph));

        mInstance.setSol(reclaimedAns);


        //display the partition and the individual routes
        //For the display
        WindyVertex tempV;
        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxX = Double.MAX_VALUE;
        double maxY = Double.MAX_VALUE;
        int n = mGraph.getVertices().size();
        for (int i = 1; i <= n; i++) {
            tempV = mGraph.getVertex(i);

            if (tempV.getX() > maxX)
                maxX = tempV.getX();
            if (tempV.getX() < minX)
                minX = tempV.getX();


            if (tempV.getY() > maxY)
                maxY = tempV.getY();
            if (tempV.getY() < minY)
                minY = tempV.getY();
        }

        float xRange = (float) (maxX - minX);
        float yRange = (float) (maxY - minY);
        float xScaleFactor = 100f / xRange * 100;
        float yScaleFactor = 100f / yRange * 100;

        try {
            WindyGraph toDisplay = mGraph.getDeepCopy();
            int limi = mGraph.getEdges().size();
            for (int i = 1; i <= limi; i++) {
                WindyEdge we = toDisplay.getEdge(i);
                if (!bestSol.containsKey(we.getId()))
                    toDisplay.removeEdge(we.getId());
            }

            GraphDisplay gd = new GraphDisplay(GraphDisplay.Layout.YifanHu, toDisplay, mInstanceName);
            gd.exportWithPartition(GraphDisplay.ExportType.PDF, bestSol);

            //individual routes
            for (Route<WindyVertex, WindyEdge> r : reclaimedAns) {

                gd = new GraphDisplay(GraphDisplay.Layout.YifanHu, toDisplay, mInstanceName);
                gd.setScaling(xScaleFactor, yScaleFactor, minX, minY);

                try {
                    gd.setInstanceName(mInstance.getName() + "_" + r.getCost() + "_" + System.currentTimeMillis());
                    gd.exportRoute(GraphDisplay.ExportType.PDF, r);

                } catch (Exception e) {
                    e.printStackTrace();
                    return null;
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
        return reclaimedAns;
    }

    @Override
    public ProblemAttributes getProblemAttributes() {
        return new ProblemAttributes(Graph.Type.WINDY, ProblemAttributes.Type.RURAL_POSTMAN, ProblemAttributes.NumVehicles.MULTI_VEHICLE, ProblemAttributes.NumDepots.SINGLE_DEPOT, null);
    }

    @Override
    public String getSolverName() {
        return "Benavent's Min-Max K Windy Rural Postman Solver";
    }

    @Override
    public Solver<WindyVertex, WindyEdge, WindyGraph> instantiate(Problem<WindyVertex, WindyEdge, WindyGraph> p) {
        return new MultiWRPPSolver_Benavent(p, p.getName());
    }

    @Override
    public HashMap<String, Double> getProblemParameters() {
        return new HashMap<String, Double>();
    }

    @Override
    public String printCurrentSol() throws IllegalStateException {

        Collection<Route<WindyVertex, WindyEdge>> currSol = mInstance.getSol();

        if (currSol == null)
            LOGGER.error("It does not appear as though this solver has been run yet!", new IllegalStateException());

        int tempCost;
        int numZeroRoutes = 0;
        int totalCost = 0;
        int minLength = Integer.MAX_VALUE;
        int maxLength = Integer.MIN_VALUE;
        double percentVariance, averageCost, averageCostNoEmpty;
        double deviationFromAverage, deviationFromAverageNoEmpty;
        int addedCost = 0;
        int origTotalCost;

        for (WindyEdge l : mInstance.getGraph().getEdges())
            addedCost -= (l.getCost() + l.getReverseCost()) / 2;

        origTotalCost = -1 * addedCost;


        String ans = "=======================================================";
        ans += "\n";
        ans += "\n";
        ans += this.getSolverName() + ": Printing current solution for instance " + mInstanceName + "...";
        ans += "\n";
        ans += "\n";
        ans += "=======================================================";
        for (Route<WindyVertex, WindyEdge> r : currSol) {
            //gather metrics
            tempCost = r.getCost();

            if (tempCost == 0)
                numZeroRoutes++;

            if (tempCost < minLength)
                minLength = tempCost;

            if (tempCost > maxLength)
                maxLength = tempCost;

            totalCost += tempCost;

            ans += "\n";
            ans += "Route: " + r.toString() + "\n";
            ans += "Route Cost: " + tempCost + "\n";
            ans += "Route Required Cost: " + r.getReqCost() + "\n";
            ans += "Route Unrequired Cost: " + (tempCost - r.getReqCost()) + "\n";
            ans += "\n";

            //exportSol
            r.exportRouteToPDF(mInstanceName + tempCost, mGraph.getDepotId());
        }

        percentVariance = ((double) maxLength - minLength) / maxLength;
        averageCost = (double) totalCost / currSol.size();
        averageCostNoEmpty = (double) totalCost / (currSol.size() - numZeroRoutes);
        deviationFromAverage = ((double) maxLength - averageCost) / maxLength;
        deviationFromAverageNoEmpty = ((double) maxLength - averageCostNoEmpty) / maxLength;
        addedCost += totalCost;

        WindyRPP tempInstance = new WindyRPP(mInstance.getGraph());
        WRPPSolver_Benavent_H1 tempSolver = new WRPPSolver_Benavent_H1(tempInstance);
        int oneVObjective = tempSolver.solve().iterator().next().getCost();
        int totalCostShare = origTotalCost / mInstance.getmNumVehicles();
        int solutionCostShare = oneVObjective / mInstance.getmNumVehicles();

        ans += "=======================================================";
        ans += "\n";
        ans += "\n";
        ans += "Vertices: " + mInstance.getGraph().getVertices().size() + "\n";
        ans += "Edges: " + mInstance.getGraph().getEdges().size() + "\n";
        ans += "Total Edge Cost: " + origTotalCost + "\n";
        ans += "Total Edge Cost / num vehicles (Total Cost Share): " + totalCostShare + "\n";
        ans += "Cost of 1-Vehicle Solution: " + oneVObjective + "\n";
        ans += "Cost of 1-Vehicle Solution / num vehicles (Solution Cost Share): " + solutionCostShare + "\n";
        ans += "Max Route Length: " + maxLength + "\n";
        ans += "Max Route Length / Total Cost Share: " + (double) maxLength / totalCostShare + "\n";
        ans += "Max Route Length / Solution Cost Share: " + (double) maxLength / solutionCostShare + "\n";
        ans += "Min Route Length: " + minLength + "\n";
        ans += "Average Route Length: " + averageCost + "\n";
        ans += "Average RouteLength (excluding empty): " + averageCostNoEmpty + "\n";
        ans += "% variance: " + 100.0 * percentVariance + "\n";
        ans += "% deviation from average length: " + 100.0 * deviationFromAverage + "\n";
        ans += "% deviation from average length (excluding empty): " + 100.0 * deviationFromAverageNoEmpty + "\n";
        ans += "Added cost: " + addedCost + "\n";
        ans += "ROI: " + new RouteOverlapMetric(mGraph).evaluate(currSol) + "\n";
        ans += "ATD: " + new AverageTraversalMetric(mGraph).evaluate(currSol) + "\n";
        ans += "\n";
        ans += "\n";
        ans += "=======================================================";

        return ans;
    }
}
