package oarlib.improvements.impl;

import gnu.trove.TIntArrayList;
import oarlib.core.Problem;
import oarlib.core.Route;
import oarlib.graph.impl.WindyGraph;
import oarlib.improvements.IntraRouteImprovementProcedure;
import oarlib.link.impl.WindyEdge;
import oarlib.route.impl.Tour;
import oarlib.route.util.RouteExpander;
import oarlib.route.util.RouteFlattener;
import oarlib.vertex.impl.WindyVertex;

import java.util.ArrayList;
import java.util.Collection;

/**
 * Improvement Procedure first given in Benavent et al. (2005) New Heuristic Algorithms
 * for the Windy Rural Postman Problem.  This IP operates over the compressed representation
 * of a feasible solution, and tries to swap the positions of two strings of required
 * edges, and re-assess cost.
 *
 * Created by oliverlum on 11/16/14.
 */
public class OrInterchange extends IntraRouteImprovementProcedure<WindyVertex, WindyEdge, WindyGraph> {


    public OrInterchange(Problem<WindyVertex, WindyEdge, WindyGraph> problem) {
        super(problem);
    }
    public OrInterchange(Problem<WindyVertex, WindyEdge, WindyGraph> problem, Collection<Route<WindyVertex, WindyEdge>> initialSol) {
        super(problem, initialSol);
    }

    private static final int L = 4;
    private static final int M = 11;

    @Override
    public Route<WindyVertex, WindyEdge> improveRoute(Route<WindyVertex, WindyEdge> r) {

        Route record = r;
        int recordCost = r.getCost();
        int candidateCost;

        RouteExpander wre = new RouteExpander(getGraph());
        boolean foundImprovement = true;

        Route newRecord = null;
        while (foundImprovement) {

            // defaults
            foundImprovement = false;
            TIntArrayList flattenedRoute = RouteFlattener.flattenRoute(record, true);
            ArrayList<Boolean> recordTraversalDirection = record.getCompactTraversalDirection();

            int n = flattenedRoute.size();

            Tour candidate;
            //swap them and re expand, and re-assess cost
            for (int i = 0; i < n; i++) { //starting point
                for (int j = 1; j <= L; j++) { //how many to move
                    if(i + j >= n )
                        break;
                    for(int k = -M; k <= M; k++) { //shift

                        //bounds
                        int lowerLim = i + k;
                        int upperLim = lowerLim + j;
                        if(lowerLim < 0)
                            continue;
                        if(k == 0)
                            continue;
                        if(upperLim > n)
                            break;

                        //copy
                        TIntArrayList candidateRoute = new TIntArrayList(flattenedRoute.toNativeArray());
                        ArrayList<Boolean> candidateTraversalDirection = new ArrayList<Boolean>(recordTraversalDirection);
                        int toMoveIndex;
                        boolean toMoveDirection;
                        if(k < 0) {
                            for(int l = 0; l > k; l--) {
                                toMoveIndex = candidateRoute.get(lowerLim);
                                toMoveDirection = candidateTraversalDirection.get(lowerLim);
                                candidateRoute.insert(i+j,toMoveIndex);
                                candidateTraversalDirection.add(i+j,toMoveDirection);
                                candidateRoute.remove(lowerLim);
                                candidateTraversalDirection.remove(lowerLim);
                            }
                        } else if(k > 0) {
                            for(int l = 0; l < k; l++) {
                                toMoveIndex = candidateRoute.get(i+j);
                                if(candidateTraversalDirection.size() == 0)
                                    System.out.println("DEBUG");
                                toMoveDirection = candidateTraversalDirection.get(i+j);
                                candidateRoute.remove(i+j);
                                candidateTraversalDirection.remove(i+j);
                                candidateRoute.insert(lowerLim,toMoveIndex);
                                candidateTraversalDirection.add(lowerLim, toMoveDirection);
                            }
                        }


                        candidate = wre.unflattenRoute(candidateRoute, candidateTraversalDirection);
                        candidateCost = candidate.getCost();
                        if (candidateCost < recordCost) {
                            recordCost = candidateCost;
                            newRecord = candidate;
                            flattenedRoute = RouteFlattener.flattenRoute(record, true);
                            foundImprovement = true;
                        }
                    }
                }
            }
            if(foundImprovement)
                record = newRecord;
        }

        return record;
    }

    @Override
    public Problem.Type getProblemType() {
        return Problem.Type.WINDY_RURAL_POSTMAN;
    }
}
