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
package oarlib.graph.util;

import gnu.trove.TIntArrayList;
import gnu.trove.TIntIntHashMap;
import gnu.trove.TIntObjectHashMap;
import oarlib.core.Graph;
import oarlib.core.Link;
import oarlib.core.Vertex;
import oarlib.exceptions.InvalidEndpointsException;
import oarlib.exceptions.NegativeCycleException;
import oarlib.exceptions.NoDemandSetException;
import oarlib.exceptions.UnsupportedFormatException;
import oarlib.graph.impl.DirectedGraph;
import oarlib.graph.impl.MixedGraph;
import oarlib.graph.impl.UndirectedGraph;
import oarlib.graph.impl.WindyGraph;
import oarlib.graph.util.Utils.DijkstrasComparator;
import oarlib.link.impl.*;
import oarlib.vertex.impl.DirectedVertex;
import oarlib.vertex.impl.MixedVertex;
import oarlib.vertex.impl.UndirectedVertex;
import oarlib.vertex.impl.WindyVertex;
import org.apache.log4j.Logger;

import java.util.*;

public class CommonAlgorithms {

    private static final Logger LOGGER = Logger.getLogger(CommonAlgorithms.class);

    /**
     * Hierholzer's algorithm for determining an Euler tour through an directed Eulerian graph.
     *
     * @param eulerianGraph - an eulerian graph on which to construct the tour
     * @return a ArrayList object containing the tour (values are edge ids).
     * @throws IllegalArgumentException if the graph passed in is not Eulerian.
     */
    public static ArrayList<Integer> tryHierholzer(DirectedGraph eulerianGraph) throws IllegalArgumentException {
        if (!isEulerian(eulerianGraph)) {
            LOGGER.error("You are attempting to run hierholzer's algorithm on a non eulerian graph.");
            throw new IllegalArgumentException();
        }
        if (eulerianGraph.getEdges().size() == 0) {
            LOGGER.debug("Running hierholzer's algorithm on an empty graph.");
            return new ArrayList<Integer>();
        }
        return hierholzer(eulerianGraph, false);
    }

    /**
     * Hierholzer's algorithm for determining an Euler tour through an undirected Eulerian graph.
     *
     * @param eulerianGraph - an eulerian graph on which to construct the tour
     * @return a ArrayList object containing the tour (values are edge ids)
     * @throws IllegalArgumentException if the graph passed in is not Eulerian.
     */
    public static ArrayList<Integer> tryHierholzer(UndirectedGraph eulerianGraph) throws IllegalArgumentException {
        if (!isEulerian(eulerianGraph)) {
            LOGGER.error("You are attempting to run hierholzer's algorithm on a non eulerian graph.");
            throw new IllegalArgumentException();
        }
        if (eulerianGraph.getEdges().size() == 0) {
            LOGGER.debug("Running hierholzer's algorithm on an empty graph.");
            return new ArrayList<Integer>();
        }
        return hierholzer(eulerianGraph, false);
    }

    /**
     * Hierholzer's algorithm for determining an Euler tour through an undirected Eulerian graph.
     *
     * @param eulerianGraph - an eulerian graph on which to construct the tour
     * @return a ArrayList object containing the tour (values are edge ids)
     * @throws IllegalArgumentException if the graph passed in is not Eulerian.
     */
    public static ArrayList<Integer> tryHierholzer(MixedGraph eulerianGraph) throws IllegalArgumentException {
        if (!isStronglyEulerian(eulerianGraph)) {
            LOGGER.error("You are attempting to run hierholzer's algorithm on a non eulerian graph.");
            throw new IllegalArgumentException();
        }
        if (eulerianGraph.getEdges().size() == 0) {
            LOGGER.debug("Running hierholzer's algorithm on an empty graph.");
            return new ArrayList<Integer>();
        }
        DirectedGraph ans = CommonAlgorithms.directUndirectedCycles(eulerianGraph);
        ans.setDepotId(eulerianGraph.getDepotId());
        if (!isStronglyConnected(ans)) {
            LOGGER.debug("You are attempting to run hierholzer's algorithm on a non-strongly connected graph.");
            throw new IllegalArgumentException();
        }
        return hierholzer(ans, true);
    }

    /**
     * business logic for Hierholzer's algorithm
     *
     * @return the Eulerian cycle
     */
    private static ArrayList<Integer> hierholzer(Graph<? extends Vertex, ? extends Link<? extends Vertex>> orig, boolean useMatchIds) {

        Graph<? extends Vertex, ? extends Link<? extends Vertex>> graph = orig.getDeepCopy();
        ArrayList<Integer> edgeTrail = new ArrayList<Integer>();
        ArrayList<Integer> edgeCycle = new ArrayList<Integer>();
        ArrayList<Vertex> visitedVertices = new ArrayList<Vertex>();
        ArrayList<Vertex> simpleCycle = new ArrayList<Vertex>();

        //pick an arbitrary start vertex
        Iterator<? extends Vertex> iter = graph.getVertices().iterator();
        Vertex start = graph.getInternalVertexMap().get(graph.getDepotId());
        visitedVertices.add(start);
        simpleCycle.add(start);
        //initialize current position variables
        Map<? extends Vertex, ? extends List<? extends Link<? extends Vertex>>> currNeighbors = start.getNeighbors();
        Vertex currVertex = start;
        Vertex prevVertex;
        Link<? extends Vertex> currEdge;
        Iterator<Vertex> vertexIter;
        boolean nextStart = true;

        TIntObjectHashMap<? extends Link<? extends Vertex>> indexedOrigEdges = orig.getInternalEdgeMap();

        while (nextStart) {
            //greedily go until we've come back to start
            do {
                currEdge = currNeighbors.values().iterator().next().get(0); //grab anybody
                if (useMatchIds)
                    edgeCycle.add(indexedOrigEdges.get(currEdge.getMatchId()).getMatchId());
                else
                    edgeCycle.add(currEdge.getMatchId()); //add it to the trail
                //update the currVertex
                prevVertex = currVertex;
                currVertex = (currEdge.getEndpoints().getFirst().getId() == currVertex.getId()) ? currEdge.getEndpoints().getSecond() : currEdge.getEndpoints().getFirst();
                simpleCycle.add(currVertex);

                //update the neighbors
                currNeighbors.get(currVertex).remove(currEdge);
                if (currNeighbors.get(currVertex).size() == 0)
                    currNeighbors.remove(currVertex);

                currNeighbors = currVertex.getNeighbors();
                if (!currEdge.isDirected()) {
                    currNeighbors.get(prevVertex).remove(currEdge);
                    if (currNeighbors.get(prevVertex).size() == 0)
                        currNeighbors.remove(prevVertex);
                }

            } while (currVertex.getId() != start.getId());

            //join the trails
            int n = visitedVertices.size();
            for (int j = 0; j < n; j++) {
                //insert here
                if (visitedVertices.get(j) == start) {
                    visitedVertices.remove(j);
                    visitedVertices.addAll(j, simpleCycle);
                    edgeTrail.addAll(j, edgeCycle);
                    break;
                }
            }

            //reinitialize the simple cycle trackers for the next go
            simpleCycle = new ArrayList<Vertex>();
            edgeCycle = new ArrayList<Integer>();

            //look for a new start point
            nextStart = false;
            vertexIter = visitedVertices.iterator();
            while (vertexIter.hasNext()) {
                start = vertexIter.next();
                if (start.getNeighbors().size() != 0) {
                    simpleCycle.add(start);
                    currVertex = start;
                    currNeighbors = currVertex.getNeighbors();
                    nextStart = true;
                    break;
                }
            }
        }
        return edgeTrail;
    }

    /**
     * Checks to make sure that an augmentation of a graph is indeed an augmentation.  That is,
     * for each edge in the augmented graph, there is a corresponding edge in the original that it is a copy of,
     * with regard to cost direction and directedness.
     *
     * @param orig      - the base graph of which augmented is a putative augmentation.
     * @param augmented - the putative augmented graph
     * @return - true if augmented is a valid augmentation of orig, false oth.
     */
    public static boolean isValidAugmentation(Graph<? extends Vertex, ? extends Link<? extends Vertex>> orig, Graph<? extends Vertex, ? extends Link<? extends Vertex>> augmented) {
        if (orig.getClass() != augmented.getClass())
            return false;
        else if (orig.getVertices().size() != augmented.getVertices().size())
            return false;

        int v1, v2;
        TIntObjectHashMap<? extends Vertex> origVertices = orig.getInternalVertexMap();
        boolean foundCopy;
        for (Link<? extends Vertex> l : augmented.getEdges()) {
            v1 = l.getEndpoints().getFirst().getId();
            v2 = l.getEndpoints().getSecond().getId();
            foundCopy = false;
            List<? extends Link<? extends Vertex>> candidates = origVertices.get(v1).getNeighbors().get(origVertices.get(v2));
            for (Link<? extends Vertex> l2 : candidates) {
                if (l2.getCost() == l.getCost()) {
                    if (l2.isDirected() && !l.isDirected())
                        continue;
                    foundCopy = true;
                    break;
                }
            }
            if (!foundCopy)
                return false;
        }
        return true;
    }

    /**
     * Checks to see if the directed graph is strongly connected
     *
     * @return true if the graph is strongly  connected, false oth.
     */
    public static boolean isStronglyConnected(DirectedGraph graph) {
        int n = graph.getVertices().size();
        int m = graph.getEdges().size();
        int[] component = new int[n + 1];
        int[] nodei = new int[m + 1];
        int[] nodej = new int[m + 1];
        int index = 1;
        Iterator<? extends Arc> iter = graph.getEdges().iterator();
        while (iter.hasNext()) {
            Arc a = iter.next();
            nodei[index] = a.getTail().getId();
            nodej[index] = a.getHead().getId();
            index++;
        }
        stronglyConnectedComponents(n, m, nodei, nodej, component);
        return component[0] == 1;
    }

    /**
     * Checks to see if the directed graph is strongly connected
     *
     * @return true if the graph is strongly  connected, false oth.
     */
    public static boolean isStronglyConnected(MixedGraph graph) {
        int n = graph.getVertices().size();
        int m = graph.getEdges().size();
        for (MixedEdge me : graph.getEdges())
            if (!me.isDirected())
                m++;
        int[] component = new int[n + 1];
        int[] nodei = new int[m + 1];
        int[] nodej = new int[m + 1];
        int index = 1;
        Iterator<? extends MixedEdge> iter = graph.getEdges().iterator();
        while (iter.hasNext()) {
            MixedEdge e = iter.next();
            nodei[index] = e.getEndpoints().getFirst().getId();
            nodej[index] = e.getEndpoints().getSecond().getId();
            if (!e.isDirected()) {
                index++;
                nodei[index] = e.getEndpoints().getSecond().getId();
                nodej[index] = e.getEndpoints().getFirst().getId();
            }
            index++;
        }
        stronglyConnectedComponents(n, m, nodei, nodej, component);
        return component[0] == 1;
    }

    public static int[] stronglyConnectedComponents(DirectedGraph graph) {
        int n = graph.getVertices().size();
        int m = graph.getEdges().size();
        int[] component = new int[n + 1];
        int[] nodei = new int[m + 1];
        int[] nodej = new int[m + 1];
        int index = 1;
        Iterator<? extends Arc> iter = graph.getEdges().iterator();
        while (iter.hasNext()) {
            Arc a = iter.next();
            nodei[index] = a.getTail().getId();
            nodej[index] = a.getHead().getId();
            index++;
        }
        stronglyConnectedComponents(n, m, nodei, nodej, component);
        return component;
    }

    /* 
     * Taken from Lau.  Returns the connected components of an undirected graph.  For the directed analog, see stronglyConnectedComponents
	 * @param n - the number of nodes in the graph
	 * @param m - the number of edges in the graph
	 * @param nodei - the pth entry holds one end of the pth edge
	 * @param nodej - the pth entry holds the other end of the pth edge
	 * @param component - 0th entry holds the number of connected components, while the pth entry holds the component that node p belongs to.
	 */
    public static void connectedComponents(int n, int m, int nodei[], int nodej[],
                                           int component[]) {

        //check for no edges
        if (m == 0) {
            component[0] = n;
            for (int i = 1; i < n + 1; i++)
                component[i] = i;
            return;
        }
        int edges, i, j, numcomp, p, q, r, typea, typeb, typec, tracka, trackb;
        int compkey, key1, key2, key3, nodeu, nodev;
        int numnodes[] = new int[n + 1];
        int aux[] = new int[n + 1];
        int index[] = new int[3];

        typec = 0;
        index[1] = 1;
        index[2] = 2;
        q = 2;
        for (i = 1; i <= n; i++) {
            component[i] = -i;
            numnodes[i] = 1;
            aux[i] = 0;
        }
        j = 1;
        edges = m;
        do {
            nodeu = nodei[j];
            nodev = nodej[j];
            key1 = component[nodeu];
            if (key1 < 0) key1 = nodeu;
            key2 = component[nodev];
            if (key2 < 0) key2 = nodev;
            if (key1 == key2) {
                if (j >= edges) {
                    edges--;
                    break;
                }
                nodei[j] = nodei[edges];
                nodej[j] = nodej[edges];
                nodei[edges] = nodeu;
                nodej[edges] = nodev;
                edges--;
            } else {
                if (numnodes[key1] >= numnodes[key2]) {
                    key3 = key1;
                    key1 = key2;
                    key2 = key3;
                    typec = -component[key2];
                } else
                    typec = Math.abs(component[key2]);
                aux[typec] = key1;
                component[key2] = component[key1];
                i = key1;
                do {
                    component[i] = key2;
                    i = aux[i];
                } while (i != 0);
                numnodes[key2] += numnodes[key1];
                numnodes[key1] = 0;
                j++;
                if (j > edges || j > n) break;
            }
        } while (true);
        numcomp = 0;
        for (i = 1; i <= n; i++)
            if (numnodes[i] != 0) {
                numcomp++;
                numnodes[numcomp] = numnodes[i];
                aux[i] = numcomp;
            }
        for (i = 1; i <= n; i++) {
            key3 = component[i];
            if (key3 < 0) key3 = i;
            component[i] = aux[key3];
        }
        if (numcomp == 1) {
            component[0] = numcomp;
            return;
        }
        typeb = numnodes[1];
        numnodes[1] = 1;
        for (i = 2; i <= numcomp; i++) {
            typea = numnodes[i];
            numnodes[i] = numnodes[i - 1] + typeb - 1;
            typeb = typea;
        }
        for (i = 1; i <= edges; i++) {
            typec = nodei[i];
            compkey = component[typec];
            aux[i] = numnodes[compkey];
            numnodes[compkey]++;
        }
        for (i = 1; i <= q; i++) {
            typea = index[i];
            do {
                if (typea <= i) break;
                typeb = index[typea];
                index[typea] = -typeb;
                typea = typeb;
            } while (true);
            index[i] = -index[i];
        }
        if (aux[1] >= 0)
            for (j = 1; j <= edges; j++) {
                tracka = aux[j];
                do {
                    if (tracka <= j) break;
                    trackb = aux[tracka];
                    aux[tracka] = -trackb;
                    tracka = trackb;
                } while (true);
                aux[j] = -aux[j];
            }
        for (i = 1; i <= q; i++) {
            typea = -index[i];
            if (typea >= 0) {
                r = 0;
                do {
                    typea = index[typea];
                    r++;
                } while (typea > 0);
                typea = i;
                for (j = 1; j <= edges; j++)
                    if (aux[j] <= 0) {
                        trackb = j;
                        p = r;
                        do {
                            tracka = trackb;
                            key1 = (typea == 1) ? nodei[tracka] : nodej[tracka];
                            do {
                                typea = Math.abs(index[typea]);
                                key1 = (typea == 1) ? nodei[tracka] : nodej[tracka];
                                tracka = Math.abs(aux[tracka]);
                                key2 = (typea == 1) ? nodei[tracka] : nodej[tracka];
                                if (typea == 1)
                                    nodei[tracka] = key1;
                                else
                                    nodej[tracka] = key1;
                                key1 = key2;
                                if (tracka == trackb) {
                                    p--;
                                    if (typea == i) break;
                                }
                            } while (true);
                            trackb = Math.abs(aux[trackb]);
                        } while (p != 0);
                    }
            }
        }
        for (i = 1; i <= q; i++)
            index[i] = Math.abs(index[i]);
        if (aux[1] > 0) {
            component[0] = numcomp;
            return;
        }
        for (j = 1; j <= edges; j++)
            aux[j] = Math.abs(aux[j]);
        typea = 1;
        for (i = 1; i <= numcomp; i++) {
            typeb = numnodes[i];
            numnodes[i] = typeb - typea + 1;
            typea = typeb;
        }
        component[0] = numcomp;
    }

    /**
     * Taken from Lau.  Gets the SCCs of a directed graph.  For the undirected analog, check connectedComponents.
     *
     * @param n         - number of nodes in the graph
     * @param m         - number of edges in the graph
     * @param nodei     - the pth entry holds the tail of the pth edge
     * @param nodej     - the pth entry holds the head of the pth edge
     * @param component - 0th entry is the number of SCCs, and the pth entry is the component that node p belongs to
     */
    public static void stronglyConnectedComponents(int n, int m, int nodei[],
                                                   int nodej[], int component[]) {
        int i, j, k, series, stackpointer, numcompoents, p, q, r;
        int backedge[] = new int[n + 1];
        int parent[] = new int[n + 1];
        int sequence[] = new int[n + 1];
        int stack[] = new int[n + 1];
        int firstedges[] = new int[n + 2];
        int endnode[] = new int[m + 1];
        boolean next[] = new boolean[n + 1];
        boolean trace[] = new boolean[n + 1];
        boolean fresh[] = new boolean[m + 1];
        boolean skip, found;

        // set up the forward star representation of the graph
        firstedges[1] = 0;
        k = 0;
        for (i = 1; i <= n; i++) {
            for (j = 1; j <= m; j++)
                if (nodei[j] == i) {
                    k++;
                    endnode[k] = nodej[j];
                }
            firstedges[i + 1] = k;
        }
        for (j = 1; j <= m; j++)
            fresh[j] = true;
        // initialize
        for (i = 1; i <= n; i++) {
            component[i] = 0;
            parent[i] = 0;
            sequence[i] = 0;
            backedge[i] = 0;
            next[i] = false;
            trace[i] = false;
        }
        series = 0;
        stackpointer = 0;
        numcompoents = 0;
        // choose an unprocessed node not in the stack
        while (true) {
            p = 0;
            while (true) {
                p++;
                if (n < p) {
                    component[0] = numcompoents;
                    return;
                }
                if (!trace[p]) break;
            }
            series++;
            sequence[p] = series;
            backedge[p] = series;
            trace[p] = true;
            stackpointer++;
            stack[stackpointer] = p;
            next[p] = true;
            while (true) {
                skip = false;
                for (q = 1; q <= n; q++) {
                    // find an unprocessed edge (p,q)
                    found = false;
                    for (i = firstedges[p] + 1; i <= firstedges[p + 1]; i++)
                        if ((endnode[i] == q) && fresh[i]) {
                            // mark the edge as processed
                            fresh[i] = false;
                            found = true;
                            break;
                        }
                    if (found) {
                        if (!trace[q]) {
                            series++;
                            sequence[q] = series;
                            backedge[q] = series;
                            parent[q] = p;
                            trace[q] = true;
                            stackpointer++;
                            stack[stackpointer] = q;
                            next[q] = true;
                            p = q;
                        } else {
                            if (trace[q]) {
                                if (sequence[q] < sequence[p] && next[q]) {
                                    backedge[p] = (backedge[p] < sequence[q]) ?
                                            backedge[p] : sequence[q];
                                }
                            }
                        }
                        skip = true;
                        break;
                    }
                }
                if (skip) continue;
                if (backedge[p] == sequence[p]) {
                    numcompoents++;
                    while (true) {
                        r = stack[stackpointer];
                        stackpointer--;
                        next[r] = false;
                        component[r] = numcompoents;
                        if (r == p) break;
                    }
                }
                if (parent[p] != 0) {
                    backedge[parent[p]] = (backedge[parent[p]] < backedge[p]) ?
                            backedge[parent[p]] : backedge[p];
                    p = parent[p];
                } else
                    break;
            }
        }
    }

    /**
     * Checks to see if the undirected graph is connected
     *
     * @return true if the graph is connected (or empty), false oth.
     */
    public static boolean isConnected(UndirectedGraph graph) {
        //start at an arbitrary vertex
        HashSet<UndirectedVertex> vertices = new HashSet<UndirectedVertex>();
        vertices.addAll(graph.getVertices());

        HashSet<UndirectedVertex> nextUp = new HashSet<UndirectedVertex>();
        HashSet<UndirectedVertex> toAdd = new HashSet<UndirectedVertex>();
        HashSet<Integer> visited = new HashSet<Integer>();
        if (vertices.size() <= 1)
            return true; //trivially connected
        nextUp.add(vertices.iterator().next());

        //DFS
        Iterator<UndirectedVertex> iter;
        while (vertices.size() > 0 && nextUp.size() > 0) {
            iter = nextUp.iterator();
            while (iter.hasNext()) {
                UndirectedVertex v = iter.next();
                vertices.remove(v);
                for (UndirectedVertex uv : v.getNeighbors().keySet()) {
                    if (!visited.contains(uv.getId())) {
                        toAdd.add(uv);
                        visited.add(uv.getId());
                    }
                }
            }
            nextUp.clear();
            nextUp.addAll(toAdd);
            toAdd.clear();
        }
        return vertices.size() == 0;
    }

    /**
     * Checks to see if the undirected graph is connected
     *
     * @return true if the graph is connected (or empty), false oth.
     */
    public static boolean isConnected(WindyGraph graph) {
        //start at an arbitrary vertex
        HashSet<WindyVertex> vertices = new HashSet<WindyVertex>();
        vertices.addAll(graph.getVertices());

        HashSet<WindyVertex> nextUp = new HashSet<WindyVertex>();
        HashSet<WindyVertex> toAdd = new HashSet<WindyVertex>();
        HashSet<Integer> visited = new HashSet<Integer>();
        if (vertices.size() <= 1)
            return true; //trivially connected
        nextUp.add(vertices.iterator().next());

        //DFS
        Iterator<WindyVertex> iter;
        while (vertices.size() > 0 && nextUp.size() > 0) {
            iter = nextUp.iterator();
            while (iter.hasNext()) {
                WindyVertex v = iter.next();
                vertices.remove(v);
                for (WindyVertex wv : v.getNeighbors().keySet()) {
                    if (!visited.contains(wv.getId())) {
                        toAdd.add(wv);
                        visited.add(wv.getId());
                    }
                }
            }
            nextUp.clear();
            nextUp.addAll(toAdd);
            toAdd.clear();
        }
        return vertices.size() == 0;
    }

    /**
     * Checks to see if the directed graph is eulerian.
     *
     * @param graph
     * @return true if the graph is eulerian, false oth.
     */
    public static boolean isEulerian(DirectedGraph graph) {
        for (DirectedVertex v : graph.getVertices()) {
            if (v.getInDegree() != v.getOutDegree())
                return false;
        }
        return true;
    }

    /**
     * Checks to see if the undirected graph is eulerian.
     *
     * @param graph
     * @return true if the graph is eulerian, false oth.
     */
    public static boolean isEulerian(UndirectedGraph graph) {
        for (UndirectedVertex v : graph.getVertices()) {
            if (v.getDegree() % 2 == 1)
                return false;
        }
        return true;
    }

    /**
     * Checks to see if the windy graph is eulerian.
     *
     * @param graph
     * @return true if the graph is eulerian, false oth.
     */
    public static boolean isEulerian(WindyGraph graph) {
        for (WindyVertex v : graph.getVertices()) {
            if (v.getDegree() % 2 == 1)
                return false;
        }
        return true;
    }

    /**
     * Checks to see if the mixed graph is strongly eulerian.  By 'strongly eulerian' we mean that all nodes are both
     * balanced (in-degree = out-degree) and even (the parity of the number of incident undirected edges of each
     * vertex is even).  This guarantees that the graph is eulerian, but it is strictly speaking not necessary.  However,
     * the necessary and sufficient conditions are computationally very expensive to check, and all heuristics implemented
     * use this notion of Eulerian anyways.
     *
     * @param graph
     * @return true if the graph is eulerian, false oth.
     */
    public static boolean isStronglyEulerian(MixedGraph graph) {
        for (MixedVertex v : graph.getVertices()) {
            if (v.getDegree() % 2 == 1)
                return false;
            if (v.getDelta() != 0)
                return false;
            if (v.getDegree() == 0 && v.getInDegree() == 0) {
                LOGGER.warn("There's something wrong; a vertex is completely detached from the rest of the graph.");
                return false;
            }
        }
        return true;
    }

    /**
     * Implements the Bellman-Ford single-source shortest paths algorithm, (useful if facing negative edge weights, but only need a single-source algorithm).
     * Complexity is |V||E|.
     *
     * @param g        - the graph on which to solve our shortest path problem.
     * @param sourceId - the vertex from which paths and distances will be calculated
     * @param dist     - the ith entry contains the shortest distance from source to vetex i.
     * @param path     - the ith entry contains the previous vertex on the shortest path from source to vertex i.
     * @throws IllegalArgumentException
     */
    public static void bellmanFordShortestPaths(Graph<? extends Vertex, ? extends Link<? extends Vertex>> g, int sourceId, int[] dist, int[] path) throws IllegalArgumentException, NegativeCycleException {
        bellmanFordShortestPaths(g, sourceId, dist, path, null);
    }

    /**
     * Implements the Bellman-Ford single-source shortest paths algorithm, (useful if facing negative edge weights, but only need a single-source algorithm).
     * Complexity is |V||E|.
     *
     * @param g        - the graph on which to solve our shortest path problem.
     * @param sourceId - the vertex from which paths and distances will be calculated
     * @param dist     - the ith entry contains the shortest distance from source to vetex i.
     * @param path     - the ith entry contains the previous vertex on the shortest path from source to vertex i.
     * @param edgePath - the ith entry contains the previous link on the shortest path from source to vertex i.
     * @throws IllegalArgumentException                 - if the argument arrays are of the incorrect size.
     * @throws oarlib.exceptions.NegativeCycleException - if there is a negative cycle in the graph.  It will record this cycle in the exception.
     */
    public static void bellmanFordShortestPaths(Graph<? extends Vertex, ? extends Link<? extends Vertex>> g, int sourceId, int[] dist, int[] path, int[] edgePath) throws IllegalArgumentException, NegativeCycleException {

        //throw to a special case if the edges have asymmetric costs
        if (g.getClass() == WindyGraph.class) {
            windyBellmanFord((WindyGraph) g, sourceId, dist, path, edgePath);
            return;
        }

        int n = g.getVertices().size();
        int BIG = 0; //the sum of all edge costs
        for (Link<? extends Vertex> l : g.getEdges()) {
            BIG += Math.abs(l.getCost());
        }

        if (dist.length != n + 1 || path.length != n + 1 || BIG < 0) {
            LOGGER.error("The input arrays to the Bellman-Ford procedure is not of the expected size.");
            throw new IllegalArgumentException();
        }

        //initialization
        boolean recordEdgePath = (edgePath != null);
        if (recordEdgePath && edgePath.length != n + 1) {
            LOGGER.error("The input arrays to the Bellman-Ford procedure is not of the expected size.");
            throw new IllegalArgumentException("The input arrays to the Bellman-Ford procedure is not of the expected size.");
        }

        for (int i = 0; i <= n; i++) {
            dist[i] = BIG; //init to a big value
            path[i] = 0; //init to an arbitrary value
            if (recordEdgePath)
                edgePath[i] = 0; //init to an arbitrary value
        }
        dist[sourceId] = 0; //dist to self = 0

        //relax edges
        TIntObjectHashMap<? extends Vertex> indexedVertices = g.getInternalVertexMap();
        LinkedList<Integer> activeVertices = new LinkedList<Integer>();
        activeVertices.add(sourceId);
        boolean[] active = new boolean[n + 1];
        active[sourceId] = true;
        Vertex u;
        int min, uid, vid, alt;
        int minid = 0;
        int counter = 0;
        int lim = n * g.getEdges().size();
        boolean searchForNegativeCycle = false;

        //business logic
        while (!activeVertices.isEmpty()) {
            u = indexedVertices.get(activeVertices.remove()); // grab an active vertex
            uid = u.getId(); //its id
            active[uid] = false; //set it inactive

            //cycle through u's neighbors
            for (Vertex v : u.getNeighbors().keySet()) {
                List<? extends Link<? extends Vertex>> l = u.getNeighbors().get(v); //the links connecting u to v
                min = Integer.MAX_VALUE; //init
                vid = v.getId(); // v's id

                //grab the cheapest link from u to v
                for (Link<? extends Vertex> link : l) {
                    if (link.getCost() < min) {
                        min = link.getCost();
                        minid = link.getId();
                    }
                }

                //this is the cost of using this new path
                alt = dist[uid] + min;

                //if it's better,
                if (alt < dist[vid]) {
                    //found a better path
                    dist[vid] = alt;
                    path[vid] = uid;
                    if (recordEdgePath)
                        edgePath[vid] = minid;
                    if (!active[vid]) {
                        active[vid] = true;
                        activeVertices.add(vid);
                    }
                }
            }
            counter++;

            //if we've relaxed more than ~ n times, that means we've got a negative cycle somewhere
            if (counter > lim) {
                searchForNegativeCycle = true;
                break;
            }
        }

        if (searchForNegativeCycle) {
            //let's construct it
            int p, q, cost;
            boolean continueSearching;
            //check for negative cycles
            for (Link<? extends Vertex> l : g.getEdges()) {

                //there has to be at least one link with negative cost in a negative cycle
                if (l.getCost() > 0)
                    continue;

                continueSearching = false;

                p = l.getEndpoints().getFirst().getId();
                q = l.getEndpoints().getSecond().getId();


                TIntArrayList problemPath = new TIntArrayList();
                TIntArrayList problemEdgePath = new TIntArrayList();

                int start = q;
                int end = q;
                int next;
                counter = 0;
                do {
                    next = path[end];
                    problemPath.add(next);
                    if (recordEdgePath)
                        problemEdgePath.add(edgePath[end]);
                    counter++;
                    if (counter > n) {
                        continueSearching = true;
                        break;
                    }
                } while ((end = next) != start);

                if (!continueSearching) {
                    LOGGER.error("This graph has a negative cycle.  It is being logged.");
                    throw new NegativeCycleException(q, problemPath.toNativeArray(), problemEdgePath.toNativeArray(), "This graph contains a negative cycle.");
                }
            }
        }
    }

    private static void windyBellmanFord(WindyGraph g, int sourceId, int[] dist, int[] path, int[] edgePath) throws IllegalArgumentException, NegativeCycleException {

        int n = g.getVertices().size();

        //create a digraph that will have link match ids to the original graph
        DirectedGraph virtual = new DirectedGraph(n);

        int BIG = 0; //the sum of all edge costs
        try {
            for (WindyEdge l : g.getEdges()) {
                BIG += l.getCost();
                BIG += l.getReverseCost();
                virtual.addEdge(l.getEndpoints().getFirst().getId(), l.getEndpoints().getSecond().getId(), l.getCost(), l.getId());
                virtual.addEdge(l.getEndpoints().getSecond().getId(), l.getEndpoints().getFirst().getId(), l.getReverseCost(), l.getId());
            }
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }

        if (dist.length != n + 1 || path.length != n + 1 || BIG < 0) {
            LOGGER.error("The input arrays to the Bellman-Ford procedure is not of the expected size.");
            throw new IllegalArgumentException();
        }

        //initialization
        boolean recordEdgePath = (edgePath != null);
        if (recordEdgePath && edgePath.length != n + 1) {
            LOGGER.error("The input arrays to the Bellman-Ford procedure is not of the expected size.");
            throw new IllegalArgumentException();
        }

        for (int i = 0; i <= n; i++) {
            dist[i] = BIG;
            path[i] = 0;
            if (recordEdgePath)
                edgePath[i] = 0;
        }
        dist[sourceId] = 0;

        //relax edges
        TIntObjectHashMap<? extends Vertex> indexedVertices = virtual.getInternalVertexMap();
        LinkedList<Integer> activeVertices = new LinkedList<Integer>();
        activeVertices.add(sourceId);
        boolean[] active = new boolean[n + 1];
        active[sourceId] = true;
        Vertex u;
        int min, uid, vid, alt;
        int minid = 0;
        int counter = 0;
        int lim = n * virtual.getEdges().size();
        boolean searchForNegativeCycle = false;
        while (!activeVertices.isEmpty()) {
            u = indexedVertices.get(activeVertices.remove());
            uid = u.getId();
            active[uid] = false;
            for (Vertex v : u.getNeighbors().keySet()) {
                List<? extends Link<? extends Vertex>> l = u.getNeighbors().get(v);
                min = Integer.MAX_VALUE;
                vid = v.getId();
                for (Link<? extends Vertex> link : l) {
                    if (link.getCost() < min) {
                        min = link.getCost();
                        minid = link.getMatchId();
                    }
                }
                alt = dist[uid] + min;
                if (alt < dist[vid]) {
                    //found a better path
                    dist[vid] = alt;
                    path[vid] = uid;
                    if (recordEdgePath)
                        edgePath[vid] = minid;
                    if (!active[vid]) {
                        active[vid] = true;
                        activeVertices.add(vid);
                    }
                }
            }
            counter++;
            if (counter > lim) {
                searchForNegativeCycle = true;
                break;
            }
        }

        if (searchForNegativeCycle) {
            int p, q, cost;
            boolean continueSearching;
            //check for negative cycles
            for (Link<? extends Vertex> l : virtual.getEdges()) {
                if (l.getCost() > 0)
                    continue;

                continueSearching = false;

                p = l.getEndpoints().getFirst().getId();
                q = l.getEndpoints().getSecond().getId();


                TIntArrayList problemPath = new TIntArrayList();
                TIntArrayList problemEdgePath = new TIntArrayList();

                int start = q;
                int end = q;
                int next;
                counter = 0;
                do {
                    next = path[end];
                    problemPath.add(next);
                    if (recordEdgePath)
                        problemEdgePath.add(edgePath[end]);
                    counter++;
                    if (counter > n) {
                        continueSearching = true;
                        break;
                    }
                } while ((end = next) != start);

                if (!continueSearching) {
                    LOGGER.error("This graph contains a negative cycle.  It is being logged.");
                    throw new NegativeCycleException(q, problemPath.toNativeArray(), problemEdgePath.toNativeArray(), "This graph contains a negative cycle.");
                }

            }
        }

    }

    /**
     * Implements the Pape's single-source shortest paths algorithm, (useful if facing negative edge weights, but only need a single-source algorithm).
     * Complexity is |V||E|.
     *
     * @param g        - the graph on which to solve our shortest path problem.
     * @param sourceId - the vertex from which paths and distances will be calculated
     * @param dist     - the ith entry contains the shortest distance from source to vetex i.
     * @param path     - the ith entry contains the previous vertex on the shortest path from source to vertex i.
     * @throws IllegalArgumentException
     */
    public static void papeShortestPaths(Graph<? extends Vertex, ? extends Link<? extends Vertex>> g, int sourceId, int[] dist, int[] path) throws IllegalArgumentException, NegativeCycleException {
        papeShortestPaths(g, sourceId, dist, path, null);
    }

    /**
     * Implements the Pape's single-source shortest paths algorithm
     *
     * @param g        - the graph on which to solve our shortest path problem.
     * @param sourceId - the vertex from which paths and distances will be calculated
     * @param dist     - the ith entry contains the shortest distance from source to vetex i.
     * @param path     - the ith entry contains the previous vertex on the shortest path from source to vertex i.
     * @param edgePath - the ith entry contains the previous link on the shortest path from source to vertex i.
     * @throws IllegalArgumentException
     */
    public static void papeShortestPaths(Graph<? extends Vertex, ? extends Link<? extends Vertex>> g, int sourceId, int[] dist, int[] path, int[] edgePath) throws IllegalArgumentException, NegativeCycleException {

        if (g.getClass() == WindyGraph.class) {
            windyPape((WindyGraph) g, sourceId, dist, path, edgePath);
            return;
        }

        int n = g.getVertices().size();
        int BIG = 0; //the sum of all edge costs
        for (Link<? extends Vertex> l : g.getEdges())
            BIG += Math.abs(l.getCost());

        if (dist.length != n + 1 || path.length != n + 1 || BIG < 0) {
            LOGGER.error("The input arrays to the Bellman-Ford procedure is not of the expected size.");
            throw new IllegalArgumentException();
        }

        //initialization
        boolean recordEdgePath = (edgePath != null);
        if (recordEdgePath && edgePath.length != n + 1) {
            LOGGER.error("The input arrays to the Bellman-Ford procedure is not of the expected size.");
            throw new IllegalArgumentException();
        }

        for (int i = 0; i <= n; i++) {
            dist[i] = BIG;
            path[i] = 0;
            if (recordEdgePath)
                edgePath[i] = 0;
        }
        dist[sourceId] = 0;

        //relax edges
        TIntObjectHashMap<? extends Vertex> indexedVertices = g.getInternalVertexMap();
        LinkedList<Integer> activeVertices = new LinkedList<Integer>();
        activeVertices.add(sourceId);
        boolean[] active = new boolean[n + 1];
        boolean[] labeled = new boolean[n + 1];
        active[sourceId] = true;
        Vertex u;
        int min, uid, vid, alt;
        int minid = 0;
        int counter = 0;
        int lim = n * g.getEdges().size();
        boolean searchForNegativeCycle = false;
        while (!activeVertices.isEmpty()) {
            u = indexedVertices.get(activeVertices.remove());
            uid = u.getId();
            active[uid] = false;
            for (Vertex v : u.getNeighbors().keySet()) {
                List<? extends Link<? extends Vertex>> l = u.getNeighbors().get(v);
                min = Integer.MAX_VALUE;
                vid = v.getId();
                for (Link<? extends Vertex> link : l) {
                    if (link.getCost() < min) {
                        min = link.getCost();
                        minid = link.getId();
                    }
                }
                alt = dist[uid] + min;
                if (alt < dist[vid]) {
                    //found a better path
                    dist[vid] = alt;
                    path[vid] = uid;
                    if (recordEdgePath)
                        edgePath[vid] = minid;
                    if (!active[vid]) {
                        active[vid] = true;
                        if (!labeled[vid]) {
                            activeVertices.add(vid);
                            labeled[vid] = true;
                        } else
                            activeVertices.addFirst(vid);
                    }
                }
            }
            counter++;
            if (counter > lim) {
                searchForNegativeCycle = true;
                break;
            }
        }

        if (searchForNegativeCycle) {
            int p, q, cost;
            boolean continueSearching;
            //check for negative cycles
            for (Link<? extends Vertex> l : g.getEdges()) {
                if (l.getCost() > 0)
                    continue;

                continueSearching = false;

                p = l.getEndpoints().getFirst().getId();
                q = l.getEndpoints().getSecond().getId();


                TIntArrayList problemPath = new TIntArrayList();
                TIntArrayList problemEdgePath = new TIntArrayList();

                int start = q;
                int end = q;
                int next;
                counter = 0;
                do {
                    next = path[end];
                    problemPath.add(next);
                    if (recordEdgePath)
                        problemEdgePath.add(edgePath[end]);
                    counter++;
                    if (counter > n) {
                        continueSearching = true;
                        break;
                    }
                } while ((end = next) != start);

                if (!continueSearching) {
                    LOGGER.error("This graph contains a negative cycle.  It is being logged.");
                    throw new NegativeCycleException(q, problemPath.toNativeArray(), problemEdgePath.toNativeArray(), "This graph contains a negative cycle.");
                }
            }
        }
    }

    private static void windyPape(WindyGraph g, int sourceId, int[] dist, int[] path, int[] edgePath) throws IllegalArgumentException, NegativeCycleException {

        int n = g.getVertices().size();

        DirectedGraph virtual = new DirectedGraph(n);

        int BIG = 0; //the sum of all edge costs
        try {
            for (WindyEdge l : g.getEdges()) {
                BIG += Math.abs(l.getCost());
                BIG += Math.abs(l.getReverseCost());
                virtual.addEdge(l.getEndpoints().getFirst().getId(), l.getEndpoints().getSecond().getId(), l.getCost());
                virtual.addEdge(l.getEndpoints().getSecond().getId(), l.getEndpoints().getFirst().getId(), l.getReverseCost());
            }
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }

        if (dist.length != n + 1 || path.length != n + 1 || BIG < 0) {
            LOGGER.error("The input arrays to the Bellman-Ford procedure is not of the expected size.");
            throw new IllegalArgumentException();
        }

        //initialization
        boolean recordEdgePath = (edgePath != null);
        if (recordEdgePath && edgePath.length != n + 1) {
            LOGGER.error("The input arrays to the Bellman-Ford procedure is not of the expected size.");
            throw new IllegalArgumentException();
        }

        for (int i = 0; i <= n; i++) {
            dist[i] = BIG;
            path[i] = 0;
            if (recordEdgePath)
                edgePath[i] = 0;
        }
        dist[sourceId] = 0;

        //relax edges
        TIntObjectHashMap<? extends Vertex> indexedVertices = virtual.getInternalVertexMap();
        LinkedList<Integer> activeVertices = new LinkedList<Integer>();
        activeVertices.add(sourceId);
        boolean[] active = new boolean[n + 1];
        boolean[] labeled = new boolean[n + 1];
        active[sourceId] = true;
        Vertex u;
        int min, uid, vid, alt;
        int minid = 0;
        int counter = 0;
        int lim = n * virtual.getEdges().size();
        boolean searchForNegativeCycle = false;
        while (!activeVertices.isEmpty()) {
            u = indexedVertices.get(activeVertices.remove());
            uid = u.getId();
            active[uid] = false;
            for (Vertex v : u.getNeighbors().keySet()) {
                List<? extends Link<? extends Vertex>> l = u.getNeighbors().get(v);
                min = Integer.MAX_VALUE;
                vid = v.getId();
                for (Link<? extends Vertex> link : l) {
                    if (link.getCost() < min) {
                        min = link.getCost();
                        minid = link.getMatchId();
                    }
                }
                alt = dist[uid] + min;
                if (alt < dist[vid]) {
                    //found a better path
                    dist[vid] = alt;
                    path[vid] = uid;
                    if (recordEdgePath)
                        edgePath[vid] = minid;
                    if (!active[vid]) {
                        active[vid] = true;
                        if (!labeled[vid]) {
                            activeVertices.add(vid);
                            labeled[vid] = true;
                        } else
                            activeVertices.addFirst(vid);
                    }
                }
            }
            counter++;
            if (counter > lim) {
                searchForNegativeCycle = true;
                break;
            }
        }

        if (searchForNegativeCycle) {
            int p, q, cost;
            boolean continueSearching;
            //check for negative cycles
            for (Link<? extends Vertex> l : virtual.getEdges()) {
                if (l.getCost() > 0)
                    continue;

                continueSearching = false;

                p = l.getEndpoints().getFirst().getId();
                q = l.getEndpoints().getSecond().getId();


                TIntArrayList problemPath = new TIntArrayList();
                TIntArrayList problemEdgePath = new TIntArrayList();

                int start = q;
                int end = q;
                int next;
                counter = 0;
                do {
                    next = path[end];
                    problemPath.add(next);
                    if (recordEdgePath)
                        problemEdgePath.add(edgePath[end]);
                    counter++;
                    if (counter > n) {
                        continueSearching = true;
                        break;
                    }
                } while ((end = next) != start);

                if (!continueSearching) {
                    LOGGER.error("This graph contains a negative cycle. It is being logged.");
                    throw new NegativeCycleException(q, problemPath.toNativeArray(), problemEdgePath.toNativeArray(), "This graph contains a negative cycle.");
                }
            }
        }

    }

    /**
     * Implements the SLF single-source shortest paths algorithm, given in Bertsekas '93 paper
     *
     * @param g        - the graph on which to solve our shortest path problem.
     * @param sourceId - the vertex from which paths and distances will be calculated
     * @param dist     - the ith entry contains the shortest distance from source to vetex i.
     * @param path     - the ith entry contains the previous vertex on the shortest path from source to vertex i.
     * @throws IllegalArgumentException
     */
    public static void slfShortestPaths(Graph<? extends Vertex, ? extends Link<? extends Vertex>> g, int sourceId, int[] dist, int[] path) throws IllegalArgumentException, NegativeCycleException {
        slfShortestPaths(g, sourceId, dist, path, null);
    }

    /**
     * Implements the SLF single-source shortest paths algorithm, given in Bertsekas '93 paper.
     *
     * @param g        - the graph on which to solve our shortest path problem.
     * @param sourceId - the vertex from which paths and distances will be calculated
     * @param dist     - the ith entry contains the shortest distance from source to vetex i.
     * @param path     - the ith entry contains the previous vertex on the shortest path from source to vertex i.
     * @param edgePath - the ith entry contains the previous link on the shortest path from source to vertex i.
     * @throws IllegalArgumentException
     */
    public static void slfShortestPaths(Graph<? extends Vertex, ? extends Link<? extends Vertex>> g, int sourceId, int[] dist, int[] path, int[] edgePath) throws IllegalArgumentException, NegativeCycleException {

        if (g.getClass() == WindyGraph.class) {
            windySLF((WindyGraph) g, sourceId, dist, path, edgePath);
            return;
        }
        int n = g.getVertices().size();
        int BIG = 0; //the sum of all edge costs
        for (Link<? extends Vertex> l : g.getEdges())
            BIG += Math.abs(l.getCost());

        if (dist.length != n + 1 || path.length != n + 1 || BIG < 0) {
            LOGGER.error("The input arrays to the Bellman-Ford procedure is not of the expected size.");
            throw new IllegalArgumentException();
        }

        //initialization
        boolean recordEdgePath = (edgePath != null);
        if (recordEdgePath && edgePath.length != n + 1) {
            LOGGER.error("The input arrays to the Bellman-Ford procedure is not of the expected size.");
            throw new IllegalArgumentException();
        }

        for (int i = 0; i <= n; i++) {
            dist[i] = BIG;
            path[i] = 0;
            if (recordEdgePath)
                edgePath[i] = 0;
        }
        dist[sourceId] = 0;

        //relax edges
        TIntObjectHashMap<? extends Vertex> indexedVertices = g.getInternalVertexMap();
        LinkedList<Integer> activeVertices = new LinkedList<Integer>();
        activeVertices.add(sourceId);
        boolean[] active = new boolean[n + 1];
        active[sourceId] = true;
        Vertex u;
        int min, uid, vid, alt;
        int minid = 0;
        int counter = 0;
        int lim = n * g.getEdges().size();
        boolean searchForNegativeCycles = false;
        while (!activeVertices.isEmpty()) {
            u = indexedVertices.get(activeVertices.remove());
            uid = u.getId();
            active[uid] = false;
            for (Vertex v : u.getNeighbors().keySet()) {
                List<? extends Link<? extends Vertex>> l = u.getNeighbors().get(v);
                min = Integer.MAX_VALUE;
                vid = v.getId();
                for (Link<? extends Vertex> link : l) {
                    if (link.getCost() < min) {
                        min = link.getCost();
                        minid = link.getId();
                    }
                }
                alt = dist[uid] + min;
                if (alt < dist[vid]) {
                    //found a better path
                    dist[vid] = alt;
                    path[vid] = uid;
                    if (recordEdgePath)
                        edgePath[vid] = minid;
                    if (!active[vid]) {
                        active[vid] = true;

                        if (activeVertices.isEmpty())
                            activeVertices.add(vid);
                        else if (dist[vid] <= dist[activeVertices.peek()])
                            activeVertices.addFirst(vid);
                        else
                            activeVertices.addLast(vid);
                    }
                }
            }
            counter++;
            if (counter > lim) {
                searchForNegativeCycles = true;
                break;
            }
        }

        if (searchForNegativeCycles) {
            int p, q;
            boolean continueSearching;
            //check for negative cycles
            for (Link<? extends Vertex> l : g.getEdges()) {
                if (l.getCost() > 0)
                    continue;

                continueSearching = false;

                p = l.getEndpoints().getFirst().getId();
                q = l.getEndpoints().getSecond().getId();


                TIntArrayList problemPath = new TIntArrayList();
                TIntArrayList problemEdgePath = new TIntArrayList();

                int start = q;
                int end = q;
                int next;
                counter = 0;
                do {
                    next = path[end];
                    problemPath.add(next);
                    if (recordEdgePath)
                        problemEdgePath.add(edgePath[end]);
                    counter++;
                    if (counter > n) {
                        continueSearching = true;
                        break;
                    }
                } while ((end = next) != start);

                if (!continueSearching) {
                    LOGGER.error("This graph contains a negative cycle.  It is being logged.");
                    throw new NegativeCycleException(q, problemPath.toNativeArray(), problemEdgePath.toNativeArray(), "This graph contains a negative cycle.");
                }

            }
        }
    }

    private static void windySLF(WindyGraph g, int sourceId, int[] dist, int[] path, int[] edgePath) throws IllegalArgumentException, NegativeCycleException {

        int n = g.getVertices().size();

        DirectedGraph virtual = new DirectedGraph(n);

        int BIG = 0; //the sum of all edge costs

        try {
            for (WindyEdge l : g.getEdges()) {
                BIG += Math.abs(l.getCost());
                BIG += Math.abs(l.getReverseCost());
                virtual.addEdge(l.getEndpoints().getFirst().getId(), l.getEndpoints().getSecond().getId(), l.getCost());
                virtual.addEdge(l.getEndpoints().getSecond().getId(), l.getEndpoints().getFirst().getId(), l.getReverseCost());
            }
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }

        if (dist.length != n + 1 || path.length != n + 1 || BIG < 0) {
            LOGGER.error("The input arrays to the Bellman-Ford procedure is not of the expected size.");
            throw new IllegalArgumentException();
        }

        //initialization
        boolean recordEdgePath = (edgePath != null);
        if (recordEdgePath && edgePath.length != n + 1) {
            LOGGER.error("The input arrays to the Bellman-Ford procedure is not of the expected size.");
            throw new IllegalArgumentException();
        }

        for (int i = 0; i <= n; i++) {
            dist[i] = BIG;
            path[i] = 0;
            if (recordEdgePath)
                edgePath[i] = 0;
        }
        dist[sourceId] = 0;

        //relax edges
        TIntObjectHashMap<? extends Vertex> indexedVertices = virtual.getInternalVertexMap();
        LinkedList<Integer> activeVertices = new LinkedList<Integer>();
        activeVertices.add(sourceId);
        boolean[] active = new boolean[n + 1];
        active[sourceId] = true;
        Vertex u;
        int min, uid, vid, alt;
        int minid = 0;
        int counter = 0;
        int lim = n * g.getEdges().size();
        boolean searchForNegativeCycles = false;
        while (!activeVertices.isEmpty()) {
            u = indexedVertices.get(activeVertices.remove());
            uid = u.getId();
            active[uid] = false;
            for (Vertex v : u.getNeighbors().keySet()) {
                List<? extends Link<? extends Vertex>> l = u.getNeighbors().get(v);
                min = Integer.MAX_VALUE;
                vid = v.getId();
                for (Link<? extends Vertex> link : l) {
                    if (link.getCost() < min) {
                        min = link.getCost();
                        minid = link.getMatchId();
                    }
                }
                alt = dist[uid] + min;
                if (alt < dist[vid]) {
                    //found a better path
                    dist[vid] = alt;
                    path[vid] = uid;
                    if (recordEdgePath)
                        edgePath[vid] = minid;
                    if (!active[vid]) {
                        active[vid] = true;

                        if (activeVertices.isEmpty())
                            activeVertices.add(vid);
                        else if (dist[vid] <= dist[activeVertices.peek()])
                            activeVertices.addFirst(vid);
                        else
                            activeVertices.addLast(vid);
                    }
                }
            }
            counter++;
            if (counter > lim) {
                searchForNegativeCycles = true;
                break;
            }
        }

        if (searchForNegativeCycles) {
            int p, q, cost;
            boolean continueSearching;
            //check for negative cycles
            for (Link<? extends Vertex> l : g.getEdges()) {
                if (l.getCost() > 0)
                    continue;

                continueSearching = false;

                p = l.getEndpoints().getFirst().getId();
                q = l.getEndpoints().getSecond().getId();


                TIntArrayList problemPath = new TIntArrayList();
                TIntArrayList problemEdgePath = new TIntArrayList();

                int start = q;
                int end = q;
                int next;
                counter = 0;
                do {
                    next = path[end];
                    problemPath.add(next);
                    if (recordEdgePath)
                        problemEdgePath.add(edgePath[end]);
                    counter++;
                    if (counter > n) {
                        continueSearching = true;
                        break;
                    }
                } while ((end = next) != start);

                if (!continueSearching) {
                    LOGGER.error("This graph contains a negative cycle.  It is being logged.");
                    throw new NegativeCycleException(q, problemPath.toNativeArray(), problemEdgePath.toNativeArray(), "This graph contains a negative cycle.");
                }
            }
        }
    }

    /**
     * Implements a modified Dijkstra's Algorithm to solve the widest path problem, which determines a path that
     * maximizes the minimum link weight from the source to the destination.  This can also be used to minimize the max
     * link weight through a simple graph conversion.  This subroutine has the added ability to constrain based on path
     * cardinality.
     *
     * @param g                  - the graph on which to solve the widest path problem.
     * @param sourceId           - the internal vertex id from which the paths and distances will be calculated
     * @param width              - the ith entry contains the width from source to vertex i.
     * @param path               - the ith entry contains the previous vertex on the widest path from source to vertex i.
     * @param maxPathCardinality - the max allowable path cardinality (e.g. 5 means only 5 hops from source are allowed)
     * @throws IllegalArgumentException
     */
    public static void dijkstrasWidestPathAlgorithmWithMaxPathCardinality(Graph<? extends Vertex, ? extends Link<? extends Vertex>> g, int sourceId, IndexedRecord<Integer>[] width, IndexedRecord<Integer>[] path, IndexedRecord<Integer>[] edgePath, int maxPathCardinality) throws IllegalArgumentException {

        int n = g.getVertices().size();
        if (width.length != n + 1 || path.length != n + 1 || edgePath.length != n + 1) {
            LOGGER.error("dijsktrasWidestPathAlgorithm: The passed in dist and path arrays have the wrong size.");
            throw new IllegalArgumentException();
        }

        //holds the remaining guys we need to process
        Stack<Integer> toProcess = new Stack<Integer>();

        //our starting point
        toProcess.push(sourceId);

        for (int i = 1; i <= n; i++) {
            width[i] = new IndexedRecord<Integer>(IndexedRecord.Objective.MAX);
            path[i] = new IndexedRecord<Integer>(IndexedRecord.Objective.MAX);
            edgePath[i] = new IndexedRecord<Integer>(IndexedRecord.Objective.MAX);
        }

        //trivially set the source
        for (int i = 0; i <= maxPathCardinality; i++) {
            width[sourceId].addEntry(i, Integer.MAX_VALUE);
            path[sourceId].addEntry(i, 0);
            edgePath[sourceId].addEntry(i, 0);
        }

        int underConsideration, max, alt;
        int vid = 0;
        int maxId = 0;
        TIntObjectHashMap<? extends Vertex> indexedVertices = g.getInternalVertexMap();
        Vertex u;
        boolean needToPush = false;
        while (!toProcess.isEmpty()) {
            underConsideration = toProcess.pop();
            u = indexedVertices.get(underConsideration);
            for (Vertex v : u.getNeighbors().keySet()) {

                List<? extends Link<? extends Vertex>> l = u.getNeighbors().get(v);
                max = Integer.MIN_VALUE;
                vid = v.getId();
                for (Link<? extends Vertex> link : l) {
                    if (link.getCost() > max) {
                        max = link.getCost();
                        maxId = link.getId();
                    }
                }

                needToPush = false;

                //make the comparisons for each of the step cardinalities
                for (int i = 0; i < maxPathCardinality; i++) {
                    if (width[underConsideration].hasKey(i))
                        alt = Math.min(width[underConsideration].getEntry(i), max);
                    else
                        continue;
                    if (!width[vid].hasKey(i + 1) || alt > width[vid].getEntry(i + 1)) {
                        //found a better path
                        width[vid].addEntry(i + 1, alt);
                        path[vid].addEntry(i + 1, underConsideration);
                        edgePath[vid].addEntry(i + 1, maxId);

                        needToPush = true;
                    }
                }
                if (needToPush && !toProcess.contains(vid)) {
                    toProcess.push(vid);
                }
            }
        }

    }


    /**
     * Implements a modified Dijkstra's Algorithm to solve the widest path problem, which determines a path that
     * maximizes the minimum link weight from the source to the destination.  This can also be used to minimize the max
     * link weight through a simple graph conversion.
     *
     * @param g        - the graph on which to solve the widest path problem.
     * @param sourceId - the internal vertex id from which the paths and distances will be calculated
     * @param width    - the ith entry contains the width from source to vertex i.
     * @param path     - the ith entry contains the previous vertex on the widest path from source to vertex i.
     * @throws IllegalArgumentException
     */
    public static void dijkstrasWidestPathAlgorithm(Graph<? extends Vertex, ? extends Link<? extends Vertex>> g, int sourceId, int[] width, int[] path) throws IllegalArgumentException {
        dijkstrasAlgorithm(g, sourceId, width, path, null);
    }

    /**
     * Implements a modified Dijkstra's Algorithm to solve the widest path problem, which determines a path that
     * maximizes the minimum link weight from the source to the destination.  This can also be used to minimize the max
     * link weight through a simple graph conversion.
     *
     * @param g        - the graph on which to solve the widest path problem.
     * @param sourceId - the internal vertex id from which the paths and distances will be calculated
     * @param width    - the ith entry contains the width from source to vertex i.
     * @param path     - the ith entry contains the previous vertex on the widest path from source to vertex i.
     * @param edgePath - the ith entry contains the edge id used to get from vertex i to vertex path[i].
     * @throws IllegalArgumentException
     */
    public static void dijkstrasWidestPathAlgorithm(Graph<? extends Vertex, ? extends Link<? extends Vertex>> g, int sourceId, int[] width, int[] path, int[] edgePath) throws IllegalArgumentException {

        if (g.getClass() == WindyGraph.class) {
            windyDijkstrasWidestPath((WindyGraph) g, sourceId, width, path, edgePath);
            return;
        }
        int n = g.getVertices().size();
        if (width.length != n + 1 || path.length != n + 1) {
            LOGGER.error("dijsktrasWidestPathAlgorithm: The passed in dist and path arrays have the wrong size.");
            throw new IllegalArgumentException();
        }

        boolean recordEdgePath = (edgePath != null);
        if (recordEdgePath && edgePath.length != n + 1) {
            LOGGER.error("dijsktrasWidestPathAlgorithm: The passed in dist and path arrays have the wrong size.");
            throw new IllegalArgumentException();
        }

        //initialize
        PriorityQueue<Pair<Integer>> pq = new PriorityQueue<Pair<Integer>>(n, new Utils.InverseDijkstrasComparator()); //first in the pair is the id, second is the weight; sorted by weight.

        width[sourceId] = Integer.MIN_VALUE;
        path[sourceId] = -1;
        for (int i = 1; i <= n; i++) {
            if (i != sourceId) {
                width[i] = Integer.MIN_VALUE;
                path[i] = -1;
                if (recordEdgePath)
                    edgePath[i] = -1;
            }
            pq.add(new Pair<Integer>(i, width[i]));
        }

        Vertex u;
        Pair<Integer> temp;
        int max, alt, uid, vid, maxId;
        maxId = Integer.MAX_VALUE;
        TIntObjectHashMap<? extends Vertex> indexedVertices = g.getInternalVertexMap();
        //now actually do the walk
        while (!pq.isEmpty()) {
            temp = pq.poll();
            u = indexedVertices.get(temp.getFirst());
            uid = u.getId();
            for (Vertex v : u.getNeighbors().keySet()) {
                List<? extends Link<? extends Vertex>> l = u.getNeighbors().get(v);
                max = Integer.MIN_VALUE;
                vid = v.getId();
                if (!pq.contains(new Pair<Integer>(vid, width[vid])))
                    continue;
                for (Link<? extends Vertex> link : l) {
                    if (link.getCost() > max) {
                        max = link.getCost();
                        maxId = link.getId();
                    }
                }
                if (width[uid] == Integer.MIN_VALUE)
                    alt = max;
                else
                    alt = Math.min(width[uid], max);

                if (alt > width[vid] || width[vid] == Integer.MIN_VALUE) {
                    //found a better path
                    pq.remove(new Pair<Integer>(vid, width[vid]));
                    width[vid] = alt;
                    path[vid] = uid;
                    if (recordEdgePath)
                        edgePath[vid] = maxId;
                    pq.add(new Pair<Integer>(vid, width[vid]));
                }
            }
        }

    }

    private static void windyDijkstrasWidestPath(WindyGraph g, int sourceId, int[] width, int[] path, int[] edgePath) throws IllegalArgumentException {

        int n = g.getVertices().size();
        if (width.length != n + 1 || path.length != n + 1) {
            LOGGER.error("dijsktrasWidestPathAlgorithm: The passed in dist and path arrays have the wrong size.");
            throw new IllegalArgumentException();
        }

        boolean recordEdgePath = (edgePath != null);
        if (recordEdgePath && edgePath.length != n + 1) {
            LOGGER.error("dijsktrasWidestPathAlgorithm: The passed in dist and path arrays have the wrong size.");
            throw new IllegalArgumentException();
        }

        DirectedGraph virtual = new DirectedGraph(n);

        try {
            for (WindyEdge l : g.getEdges()) {
                virtual.addEdge(l.getEndpoints().getFirst().getId(), l.getEndpoints().getSecond().getId(), l.getCost(), l.getId());
                virtual.addEdge(l.getEndpoints().getSecond().getId(), l.getEndpoints().getFirst().getId(), l.getReverseCost(), l.getId());
            }
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }

        //initialize
        PriorityQueue<Pair<Integer>> pq = new PriorityQueue<Pair<Integer>>(n, new Utils.InverseDijkstrasComparator()); //first in the pair is the id, second is the weight; sorted by weight.
        width[sourceId] = Integer.MIN_VALUE;
        path[sourceId] = -1;
        for (int i = 1; i <= n; i++) {
            if (i != sourceId) {
                width[i] = Integer.MIN_VALUE;
                path[i] = -1;
                if (recordEdgePath)
                    edgePath[i] = -1;
            }
            pq.add(new Pair<Integer>(i, width[i]));
        }

        Vertex u;
        Pair<Integer> temp;
        int max, alt, uid, vid, maxId;
        maxId = Integer.MAX_VALUE;
        TIntObjectHashMap<? extends Vertex> indexedVertices = virtual.getInternalVertexMap();
        //now actually do the walk
        while (!pq.isEmpty()) {
            temp = pq.poll();
            u = indexedVertices.get(temp.getFirst());
            uid = u.getId();
            for (Vertex v : u.getNeighbors().keySet()) {
                List<? extends Link<? extends Vertex>> l = u.getNeighbors().get(v);
                max = Integer.MIN_VALUE;
                vid = v.getId();
                if (!pq.contains(new Pair<Integer>(vid, width[vid])))
                    continue;
                for (Link<? extends Vertex> link : l) {
                    if (link.getCost() > max) {
                        max = link.getCost();
                        maxId = link.getMatchId();
                    }
                }

                if (width[uid] == Integer.MIN_VALUE)
                    alt = max;
                else
                    alt = Math.min(width[uid], max);

                if (alt > width[vid] || width[vid] == Integer.MIN_VALUE) {
                    //found a better path
                    pq.remove(new Pair<Integer>(vid, width[vid]));
                    width[vid] = alt;
                    path[vid] = uid;
                    if (recordEdgePath)
                        edgePath[vid] = maxId;
                    pq.add(new Pair<Integer>(vid, width[vid]));
                }
            }
        }

    }

    /**
     * Implements Dijkstra's Algorithm with Priority Queues, to achieve |E| + |V|log|V| single-source shortest paths
     *
     * @param g        - the graph on which to solve our shortest path problem.
     * @param sourceId - the internal vertex id from which paths and distances will be calculated
     * @param dist     - the ith entry contains the shortest distance from source to vertex i.
     * @param path     - the ith entry contains the previous vertex on the shortest path from source to vertex i.
     */
    public static void dijkstrasAlgorithm(Graph<? extends Vertex, ? extends Link<? extends Vertex>> g, int sourceId, int[] dist, int[] path) throws IllegalArgumentException {
        dijkstrasAlgorithm(g, sourceId, dist, path, null);
    }

    /**
     * Implements Dijkstra's Algorithm with Priority Queues, to achieve |E| + |V|log|V| single-source shortest paths
     *
     * @param g        - the graph on which to solve our shortest path problem.
     * @param sourceId - the vertex id from which paths and distances will be calculated
     * @param dist     - the ith entry will contain the shortest distance from source to vertex i.
     * @param path     - the ith entry will contain the previous vertex on the shortest path from source to vertex i.
     * @param edgePath - the ith entry will contain the id of the edge that gets traversed to get from the previous vertex in the path to the ith vertex.
     */
    public static void dijkstrasAlgorithm(Graph<? extends Vertex, ? extends Link<? extends Vertex>> g, int sourceId, int[] dist, int[] path, int[] edgePath) throws IllegalArgumentException {

        int n = g.getVertices().size();
        if (dist.length != n + 1 || path.length != n + 1) {
            LOGGER.error("dijsktrasWidestPathAlgorithm: The passed in dist and path arrays have the wrong size.");
            throw new IllegalArgumentException();
        }

        //initialize
        boolean recordEdgePath = (edgePath != null);
        if (recordEdgePath && edgePath.length != n + 1) {
            LOGGER.error("dijsktrasWidestPathAlgorithm: The passed in dist and path arrays have the wrong size.");
            throw new IllegalArgumentException();
        }

        DirectedGraph virtual = new DirectedGraph(n);
        try {
            for (Link<? extends Vertex> l : g.getEdges()) {
                if (l.isDirected()) {
                    virtual.addEdge(l.getEndpoints().getFirst().getId(), l.getEndpoints().getSecond().getId(), l.getCost(), l.getId());
                } else if (l instanceof AsymmetricLink) {
                    virtual.addEdge(l.getEndpoints().getFirst().getId(), l.getEndpoints().getSecond().getId(), l.getCost(), l.getId());
                    virtual.addEdge(l.getEndpoints().getSecond().getId(), l.getEndpoints().getFirst().getId(), ((AsymmetricLink) l).getReverseCost(), l.getId());
                } else {
                    virtual.addEdge(l.getEndpoints().getFirst().getId(), l.getEndpoints().getSecond().getId(), l.getCost(), l.getId());
                    virtual.addEdge(l.getEndpoints().getSecond().getId(), l.getEndpoints().getFirst().getId(), l.getCost(), l.getId());
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }

        PriorityQueue<Pair<Integer>> pq = new PriorityQueue<Pair<Integer>>(n, new DijkstrasComparator()); //first in the pair is the id, second is the weight; sorted by weight.
        dist[sourceId] = 0;
        path[sourceId] = -1;
        for (int i = 1; i <= n; i++) {
            if (i != sourceId) {
                dist[i] = Integer.MAX_VALUE;
                path[i] = -1;
                if (recordEdgePath)
                    edgePath[i] = -1;
            }
            pq.add(new Pair<Integer>(i, dist[i]));
        }

        DirectedVertex u;
        Pair<Integer> temp;
        int min, alt, uid, vid, minid;
        minid = Integer.MAX_VALUE;
        TIntObjectHashMap<DirectedVertex> indexedVertices = virtual.getInternalVertexMap();
        //now actually do the walk
        while (!pq.isEmpty()) {
            temp = pq.poll();
            u = indexedVertices.get(temp.getFirst());
            uid = u.getId();
            if (dist[uid] == Integer.MAX_VALUE)
                continue; //we got to the point where it's disconnected; don't add to max integer, and cause loop around
            for (DirectedVertex v : u.getNeighbors().keySet()) {
                List<Arc> l = u.getNeighbors().get(v);
                min = Integer.MAX_VALUE;
                vid = v.getId();
                if (!pq.contains(new Pair<Integer>(vid, dist[vid])))
                    continue;
                for (Arc link : l) {
                    if (link.getCost() < min) {
                        min = link.getCost();
                        minid = link.getMatchId();
                    }
                }
                //don't go past max integer, that's bad
                alt = dist[uid] + min;
                if (alt < dist[vid]) {
                    //found a better path
                    pq.remove(new Pair<Integer>(vid, dist[vid]));
                    dist[vid] = alt;
                    path[vid] = uid;
                    if (recordEdgePath)
                        edgePath[vid] = minid;
                    pq.add(new Pair<Integer>(vid, dist[vid]));
                }
            }
        }
    }

    /**
     * Implements the Floyd-Warshall shortest paths algorithm.
     *
     * @param g    - the graph in which the shortest paths should be calculated
     * @param dist - an [n+1][n+1] matrix that will be filled with shortest paths at the end: the 0th column and row
     *             will be filled with Integer.MAX, and dist[i][j] will hold the shortest path cost between node i and node j.
     * @param path - an [n+1][n+1] matrix that will tell us how to reconstruct the shortest path: the 0th column and row
     *             will be filled with Integer.MAX, and path[i][j] holds the id of the node to go to next in the shortest path from node i t node j.
     */
    public static void fwLeastCostPaths(Graph<? extends Vertex, ? extends Link<? extends Vertex>> g, int[][] dist, int[][] path) throws IllegalArgumentException {
        fwLeastCostPaths(g, dist, path, null);
    }

    /**
     * Solves the min-cost spanning tree problem using Prim's algorithm
     *
     * @param g - the undirected graph on which to solve the MST problem.
     * @return - an 0-1 array where the ith entry is 1 if the ith edge is included in the tree.
     */
    public static int[] minCostSpanningTree(UndirectedGraph g) {
        int m = g.getEdges().size();
        int[] ans = new int[m + 1];
        TIntObjectHashMap<Edge> indexedEdges = g.getInternalEdgeMap();
        Edge temp;
        ArrayList<Pair<Integer>> pq = new ArrayList<Pair<Integer>>();
        for (int i = 1; i <= m; i++) {
            temp = indexedEdges.get(i);
            pq.add(new Pair<Integer>(i, temp.getCost()));
        }
        Collections.sort(pq, new DijkstrasComparator()); //a sorted list of our edges
        Collections.reverse(pq);

        //now pick a start vertex, and start expanding
        int start = pq.size() - 1;
        HashSet<Integer> visitedVertices = new HashSet<Integer>();
        temp = indexedEdges.get(pq.get(start).getFirst()); //might as well start with the cheapest guy
        ans[temp.getId()] = 1;
        visitedVertices.add(temp.getEndpoints().getFirst().getId());
        visitedVertices.add(temp.getEndpoints().getSecond().getId());
        pq.remove(start);

        //now grow this guy organically
        Pair<UndirectedVertex> tempEndpoints;
        int currTreeSize = 1;
        int mstSize = g.getVertices().size() - 1;
        while (currTreeSize < mstSize) {
            start = pq.size() - 1;
            temp = indexedEdges.get(pq.get(start).getFirst());
            tempEndpoints = temp.getEndpoints();
            while (visitedVertices.contains(tempEndpoints.getFirst().getId()) == visitedVertices.contains(tempEndpoints.getSecond().getId())) {
                start--;
                temp = indexedEdges.get(pq.get(start).getFirst());
                tempEndpoints = temp.getEndpoints();
            }
            ans[temp.getId()] = 1;
            visitedVertices.add(temp.getEndpoints().getFirst().getId());
            visitedVertices.add(temp.getEndpoints().getSecond().getId());
            pq.remove(start);
            currTreeSize++;
        }

        return ans;
    }


    /**
     * Solves the min-cost spanning tree problem using Prim's algorithm
     *
     * @param g       - the undirected graph on which to solve the MST problem.
     * @param setSize - the size of the set from which an edge is greedily selected.  setSize = 1 reduces to a
     *                normal MST algorithm.  setSize = 2 chooses from the cheapest 2 edges at each stage.
     * @return - an 0-1 array where the ith entry is 1 if the ith edge is included in the tree.
     */
    public static int[] randomizedLowCostSpanningTree(UndirectedGraph g, int setSize) {
        int m = g.getEdges().size();
        int[] ans = new int[m + 1];
        TIntObjectHashMap<Edge> indexedEdges = g.getInternalEdgeMap();
        Edge temp;
        ArrayList<Pair<Integer>> pq = new ArrayList<Pair<Integer>>();
        for (int i = 1; i <= m; i++) {
            temp = indexedEdges.get(i);
            pq.add(new Pair<Integer>(i, temp.getCost()));
        }
        Collections.sort(pq, new DijkstrasComparator()); //a sorted list of our edges
        Collections.reverse(pq);

        //now pick a start vertex, and start expanding
        int start = pq.size() - 1;
        HashSet<Integer> visitedVertices = new HashSet<Integer>();
        temp = indexedEdges.get(pq.get(start).getFirst()); //might as well start with the cheapest guy
        ans[temp.getId()] = 1;
        visitedVertices.add(temp.getEndpoints().getFirst().getId());
        visitedVertices.add(temp.getEndpoints().getSecond().getId());
        pq.remove(start);

        //now grow this guy organically
        Pair<UndirectedVertex> tempEndpoints;
        int currTreeSize = 1;
        int mstSize = g.getVertices().size() - 1;
        Random rng = new Random(1000);
        int offset, counter;

        while (currTreeSize < mstSize) {
            start = pq.size() - 1;
            temp = indexedEdges.get(pq.get(start).getFirst());
            tempEndpoints = temp.getEndpoints();
            offset = rng.nextInt(setSize);
            counter = 0;
            while (counter <= offset) {
                while (visitedVertices.contains(tempEndpoints.getFirst().getId()) == visitedVertices.contains(tempEndpoints.getSecond().getId())) {
                    if (start == 0)
                        break;
                    start--;
                    temp = indexedEdges.get(pq.get(start).getFirst());
                    tempEndpoints = temp.getEndpoints();
                }
                counter++;
            }
            ans[temp.getId()] = 1;
            visitedVertices.add(temp.getEndpoints().getFirst().getId());
            visitedVertices.add(temp.getEndpoints().getSecond().getId());
            pq.remove(start);
            currTreeSize++;
        }

        return ans;
    }

    /*
     * Mainly for use with the MCPP Solvers, once we have our answer, we want to be able to search for
	 * convert an eulerian mixed graph into an eulerian directed graph.  This graph simply identifies
	 * undirected cycles in the input graph, and directs them in an arbitrary direction.  
	 */
    private static DirectedGraph directUndirectedCycles(MixedGraph input) throws IllegalArgumentException {
        if (!CommonAlgorithms.isStronglyEulerian(input)) {
            LOGGER.error("Tried to run directUndirectedCycles on a non-eulerian Mixed Graph.");
            throw new IllegalArgumentException();
        }
        try {
            DirectedGraph ans = new DirectedGraph();
            UndirectedGraph temp = new UndirectedGraph();
            int n = input.getVertices().size();
            for (int i = 0; i < n; i++) {
                temp.addVertex(new UndirectedVertex("temp"));
                ans.addVertex(new DirectedVertex("ans"));
            }
            for (MixedEdge e : input.getEdges()) {
                //create an undirected graph that only has the undirected edges from input
                if (!e.isDirected())
                    temp.addEdge(e.getEndpoints().getFirst().getId(), e.getEndpoints().getSecond().getId(), "temp", e.getCost(), e.getId());
                else
                    ans.addEdge(e.getTail().getId(), e.getHead().getId(), "ans", e.getCost(), e.getId());
            }

            UndirectedVertex start = null;
            HashSet<Integer> usedEdges;
            ArrayList<Integer> vertexIds;
            ArrayList<Integer> edgeIds;
            HashMap<UndirectedVertex, ArrayList<Edge>> neighbors;
            TIntObjectHashMap<Edge> indexedEdges = temp.getInternalEdgeMap();
            TIntObjectHashMap<UndirectedVertex> indexedVertices = temp.getInternalVertexMap();
            UndirectedVertex curr;
            int startId = 0;
            int currId = 0;
            boolean foundNewEdge;
            Edge e2;
            while (temp.getEdges().size() > 0) {
                //pick any guy with an incident edge
                for (UndirectedVertex v : temp.getVertices()) {
                    if (v.getDegree() > 0) {
                        start = v;
                        startId = start.getId();
                        currId = startId;
                        break;
                    }
                }

                foundNewEdge = false;
                //greedily move until we make it back to start
                usedEdges = new HashSet<Integer>();
                vertexIds = new ArrayList<Integer>();
                edgeIds = new ArrayList<Integer>();
                vertexIds.add(startId);
                do {
                    curr = indexedVertices.get(currId);
                    neighbors = curr.getNeighbors();
                    for (UndirectedVertex v : neighbors.keySet()) {
                        for (Edge e : neighbors.get(v)) {
                            if (!usedEdges.contains(e.getId())) {
                                foundNewEdge = true;
                                currId = v.getId();
                                vertexIds.add(currId);
                                edgeIds.add(e.getId());
                                usedEdges.add(e.getId());
                                break;
                            }
                        }
                        if (foundNewEdge) {
                            foundNewEdge = false;
                            break;
                        }
                    }
                } while (currId != startId);

                //remove cycle from temp, and add cycle to ans
                int lim = edgeIds.size();
                for (int i = 0; i < lim; i++) {
                    e2 = indexedEdges.get(edgeIds.get(i));
                    temp.removeEdge(e2);
                    ans.addEdge(vertexIds.get(i), vertexIds.get(i + 1), "directing cycle", e2.getCost(), e2.getMatchId());
                }

            }
            return ans;

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    //TODO: get rid of ?'s in arguments for tighter coupling
    public static void fwLeastCostPaths(Graph<? extends Vertex, ? extends Link<? extends Vertex>> g, int[][] dist, int[][] path, int[][] edgePath) throws IllegalArgumentException {
        //initialize dist and path
        int n = g.getVertices().size();
        int m = g.getEdges().size();

        boolean recordEdgePath = (edgePath != null);

        if (dist.length != n + 1 || path.length != n + 1) {
            LOGGER.error("The input arrays to the Floyd-Warshall least cost paths procedure is not of the expected size.");
            throw new IllegalArgumentException();
        }

        if (recordEdgePath && edgePath.length != n + 1) {
            LOGGER.error("The input arrays to the Floyd-Warshall procedure is not of the expected size.");
            throw new IllegalArgumentException();
        }

        try {
            //setup the digraph so and solve as normal; just use the match id for edgepath

            DirectedGraph g2 = new DirectedGraph();
            for (int i = 0; i < n; i++) {
                g2.addVertex(new DirectedVertex("original"));
            }

            TIntObjectHashMap<? extends Link<? extends Vertex>> indexedWindyEdges = g.getInternalEdgeMap();
            Link<? extends Vertex> temp;
            for (int i = 1; i <= m; i++) {
                temp = indexedWindyEdges.get(i);
                if(temp == null)
                    continue;
                if (temp.isDirected())
                    g2.addEdge(temp.getEndpoints().getFirst().getId(), temp.getEndpoints().getSecond().getId(), "forward", temp.getCost(), i);
                else if (temp instanceof AsymmetricLink) {
                    g2.addEdge(temp.getEndpoints().getFirst().getId(), temp.getEndpoints().getSecond().getId(), "forward", temp.getCost(), i);
                    g2.addEdge(temp.getEndpoints().getSecond().getId(), temp.getEndpoints().getFirst().getId(), "backward", ((AsymmetricLink) temp).getReverseCost(), i);
                } else {
                    g2.addEdge(temp.getFirstEndpointId(), temp.getSecondEndpointId(), "forward", temp.getCost(), i);
                    g2.addEdge(temp.getSecondEndpointId(), temp.getFirstEndpointId(), "backward", temp.getCost(), i);
                }
            }

            //initialize dist and path
            for (int i = 0; i <= n; i++) {
                for (int j = 0; j <= n; j++) {
                    dist[i][j] = Integer.MAX_VALUE;
                }
                path[0][i] = Integer.MAX_VALUE;
                path[i][0] = Integer.MAX_VALUE;
                if (recordEdgePath) {
                    edgePath[0][i] = Integer.MAX_VALUE;
                    edgePath[i][0] = Integer.MAX_VALUE;
                }
            }

            Vertex vi;
            int min;
            for (int i = 1; i <= n; i++) {
                vi = g2.getInternalVertexMap().get(i);
                for (Vertex v : vi.getNeighbors().keySet()) {
                    List<? extends Link<? extends Vertex>> l = vi.getNeighbors().get(v);
                    min = Integer.MAX_VALUE;
                    Link<? extends Vertex> edge = null;
                    for (Link<? extends Vertex> link : l) {
                        if (link.getCost() < min) {
                            min = link.getCost();
                            edge = link;
                        }
                    }
                    dist[vi.getId()][v.getId()] = min;
                    path[vi.getId()][v.getId()] = v.getId();
                    if (recordEdgePath)
                        edgePath[vi.getId()][v.getId()] = edge.getMatchId();
                }
            }

            //business logic
            for (int k = 1; k <= n; k++) {
                for (int i = 1; i <= n; i++) {
                    //if there is an edge from i to k
                    if (dist[i][k] < Integer.MAX_VALUE)
                        for (int j = 1; j <= n; j++) {
                            //if there is an edge from k to j
                            if (dist[k][j] < Integer.MAX_VALUE
                                    && (!(dist[i][j] < Integer.MAX_VALUE) || dist[i][j] > dist[i][k] + dist[k][j])) {
                                path[i][j] = path[i][k];
                                if (recordEdgePath)
                                    edgePath[i][j] = edgePath[i][k];
                                dist[i][j] = dist[i][k] + dist[k][j];
                                if (i == j && dist[i][j] < 0)
                                    return; //negative cycle
                            }
                        }

                }
            }
            for (int i = 1; i <= n; i++) {
                if (dist[i][i] == Integer.MAX_VALUE)
                    dist[i][i] = 0;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * adds the shortest path from p1 to p2 to g.
     *
     * @param g    - the directed graph in which to add the paths
     * @param dist - the dist matrix (probably output from fwLeastCostPaths)
     * @param path - the path matrix (probably output from fwLeastCostPaths)
     * @param p    - the ids (in g) of the vertices you want to add the shortest path from (to)
     */
    public static void addShortestPath(DirectedGraph g, int[][] dist, int[][] path, Pair<Integer> p) {
        try {
            int curr = p.getFirst();
            int end = p.getSecond();
            int next = 0;
            int cost = 0;
            DirectedVertex u, v;
            do {
                next = path[curr][end];
                cost = dist[curr][next];
                u = g.getInternalVertexMap().get(curr);
                v = g.getInternalVertexMap().get(next);
                g.addEdge(new Arc("from addShortestPath", new Pair<DirectedVertex>(u, v), cost));
            } while ((curr = next) != end);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * adds the shortest path from p1 to p2 to g.
     *
     * @param g        - the directed graph in which to add the paths
     * @param dist     - the dist matrix (probably output from fwLeastCostPaths)
     * @param path     - the path matrix (probably output from fwLeastCostPaths)
     * @param edgePath - the edgePath matrix (probably output from fwLeastCostPaths) that gives the edge ids of the paths in path.
     * @param p        - the ids (in g) of the vertices you want to add the shortest path from (to)
     */
    public static void addShortestPath(DirectedGraph g, int[][] dist, int[][] path, int[][] edgePath, Pair<Integer> p) {
        try {
            int curr = p.getFirst();
            int end = p.getSecond();
            int next = 0;
            int nextEdge = 0;
            TIntObjectHashMap<Arc> indexedArcs = g.getInternalEdgeMap();
            do {
                next = path[curr][end];
                nextEdge = edgePath[curr][end];
                g.addEdge(indexedArcs.get(nextEdge).getCopy(), nextEdge);
            } while ((curr = next) != end);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * adds the shortest path from p1 to p2 to g.
     *
     * @param g        - the directed graph in which to add the paths
     * @param dist     - the dist matrix (probably output from fwLeastCostPaths)
     * @param path     - the path matrix (probably output from fwLeastCostPaths)
     * @param edgePath - the edgePath matrix (probably output from fwLeastCostPaths) that gives the edge ids of the paths in path.
     * @param p        - the ids (in g) of the vertices you want to add the shortest path from (to)
     */
    public static void addShortestPath(WindyGraph g, int[][] dist, int[][] path, int[][] edgePath, Pair<Integer> p) {
        try {
            int curr = p.getFirst();
            int end = p.getSecond();
            int next = 0;
            int nextEdge = 0;
            TIntObjectHashMap<WindyEdge> indexedEdges = g.getInternalEdgeMap();
            do {
                next = path[curr][end];
                nextEdge = edgePath[curr][end];
                g.addEdge(indexedEdges.get(nextEdge).getCopy(), nextEdge);
            } while ((curr = next) != end);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * adds the shortest path from p1 to p2 to g.
     *
     * @param g    - the undirected graph in which to add the paths
     * @param dist - the dist matrix (probably output from fwLeastCostPaths)
     * @param path - the path matrix (probably output from fwLeastCostPaths)
     * @param p    - the ids (in g) of the vertices you want to add the shortest path from (to)
     */
    public static void addShortestPath(UndirectedGraph g, int[][] dist, int[][] path, Pair<Integer> p) {
        try {
            int curr = p.getFirst();
            int end = p.getSecond();
            int next = 0;
            int cost = 0;
            UndirectedVertex u, v;
            do {
                next = path[curr][end];
                cost = dist[curr][next];
                u = g.getInternalVertexMap().get(curr);
                v = g.getInternalVertexMap().get(next);
                g.addEdge(new Edge("from addShortestPath", new Pair<UndirectedVertex>(u, v), cost));
            } while ((curr = next) != end);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * adds the shortest path from p1 to p2 to g.
     *
     * @param g        - the directed graph in which to add the paths
     * @param dist     - the dist matrix (probably output from fwLeastCostPaths)
     * @param path     - the path matrix (probably output from fwLeastCostPaths)
     * @param edgePath - the edgePath matrix (probably output from fwLeastCostPaths) that gives the edge ids of the paths in path.
     * @param p        - the ids (in g) of the vertices you want to remove the shortest path from (to)
     */
    public static void removeShortestPath(UndirectedGraph g, int[][] dist, int[][] path, int[][] edgePath, Pair<Integer> p) {
        try {
            int curr = p.getFirst();
            int end = p.getSecond();
            int next = 0;
            int nextEdge = 0;
            TIntObjectHashMap<Edge> indexedEdges = g.getInternalEdgeMap();
            Edge toRemove;
            do {
                next = path[curr][end];
                nextEdge = edgePath[curr][end];
                toRemove = indexedEdges.get(nextEdge);
                g.removeEdge(toRemove);
            } while ((curr = next) != end);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * adds the shortest path from p1 to p2 to g.
     *
     * @param g        - the directed graph in which to add the paths
     * @param dist     - the dist matrix (probably output from fwLeastCostPaths)
     * @param path     - the path matrix (probably output from fwLeastCostPaths)
     * @param edgePath - the edgePath matrix (probably output from fwLeastCostPaths) that gives the edge ids of the paths in path.
     * @param p        - the ids (in g) of the vertices you want to add the shortest path from (to)
     */
    public static void addShortestPath(UndirectedGraph g, int[][] dist, int[][] path, int[][] edgePath, Pair<Integer> p) {
        try {
            int curr = p.getFirst();
            int end = p.getSecond();
            int next = 0;
            int nextEdge = 0;
            TIntObjectHashMap<Edge> indexedEdges = g.getInternalEdgeMap();
            do {
                next = path[curr][end];
                nextEdge = edgePath[curr][end];
                g.addEdge(indexedEdges.get(nextEdge).getCopy(), nextEdge);
            } while ((curr = next) != end);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Gets the residual graph of g, given flow given by f.  Note that this does not respect capacities; use the other getResidualGraph
     * for that.
     *
     * @param g - the original graph, for which we want the residual
     * @param f - the current feasible flow solution relative to which the residual graph will be generated
     * @return - the residual graph g_x relative to the flow solution specified by f.
     */
    private static DirectedGraph getResidualGraph(DirectedGraph g, HashMap<Integer, Integer> f) {
        try {
            DirectedGraph ans = g.getDeepCopy();
            HashSet<Arc> origEdges = new HashSet<Arc>();
            origEdges.addAll(ans.getEdges());
            for (Arc a : origEdges) {
                if (f.containsKey(a.getMatchId()) && f.get(a.getMatchId()) > 0) {
                    ans.addEdge(new Arc("artificial residual edge", new Pair<DirectedVertex>(a.getHead(), a.getTail()), -a.getCost()));
                }
            }
            return ans;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Implements the shortest successive paths with potentials algorithm to calculate a min cost flow through the graph g
     *
     * @param g
     * @return - an array that contains flow values.  That is, entry i has value j if edge i  has j units of flow pushed across it
     * in the min cost solution.
     * @throws IllegalArgumentException - if the problem is determined to be infeasible.
     */
    public static int[] shortestSuccessivePathsMinCostNetworkFlow(DirectedGraph g) throws IllegalArgumentException {

        //so we don't mess with the original
        DirectedGraph copy = g.getDeepCopy();
        int m = copy.getEdges().size(); //for trimming
        //int[] retArray = new int[m+1];
        int[] retArray = new int[g.getEidCounter()];

        if (CommonAlgorithms.isEulerian(g))
            return retArray;

        //add a source a sink
        DirectedVertex source = new DirectedVertex("source");
        DirectedVertex sink = new DirectedVertex("sink");
        copy.addVertex(source);
        copy.addVertex(sink);

        //add the sink and source edges
        Arc temp, temp2;
        boolean hasDemand = false;
        for (DirectedVertex v : copy.getVertices()) {
            try {
                if (v.getDemand() > 0) //has supply
                {
                    temp = new Arc("source arc", new Pair<DirectedVertex>(source, v), 0);
                    temp.setCapacity(v.getDemand());
                    copy.addEdge(temp);
                    temp.setMatchId(temp.getId());
                    hasDemand = true;
                } else if (v.getDemand() < 0) //has demand
                {
                    temp = new Arc("sink arc", new Pair<DirectedVertex>(v, sink), 0);
                    temp.setCapacity(-v.getDemand());
                    copy.addEdge(temp);
                    temp.setMatchId(temp.getId());
                    hasDemand = true;
                }
            } catch (NoDemandSetException e) {
                //do nothing
            } catch (InvalidEndpointsException e) {
                //bad
                e.printStackTrace();
                return null;
            }
        }

        int newm = copy.getEdges().size();
        int[] realIds = new int[newm + 1]; //entry i holds the id in copy of the edge that maps to edge i of g
        int[] artificialIds = new int[newm + 1]; //entry i holds the id in copy of the artificial edge that maps to edge i of g
        int[] ans = new int[newm + 1]; //the answer

        if (!hasDemand)
            return ans;

        TIntObjectHashMap<Arc> indexedArcs = copy.getInternalEdgeMap();

        //initialize
        for (int i = 1; i < newm + 1; i++) {
            realIds[indexedArcs.get(i).getMatchId()] = i;
        }

        //figure out residual graph, and look for paths from source to sink
        int n = copy.getVertices().size();
        int[] dist = new int[n + 1];
        int[] path = new int[n + 1];


        //reduce costs
        int sourceId = source.getId();
        int sinkId = sink.getId();

        try {
            CommonAlgorithms.slfShortestPaths(copy, sourceId, dist, path);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }

        for (Arc a : copy.getEdges()) {
            if (a.getTail().getId() == sourceId)
                continue;
            a.setCost(a.getCost() + dist[a.getTail().getId()] - dist[a.getHead().getId()]);
        }

        //check for legality
        if (dist[sinkId] == Integer.MAX_VALUE) {
            LOGGER.error("Your graph is not connected, or this is not a valid flow problem");
            throw new IllegalArgumentException();
        }


        //start looking for augmenting paths
        int prev, prevEdge;
        int maxFlow;
        int[] dijkstraDist = new int[n + 1];
        int[] dijkstraPath = new int[n + 1];
        int[] dijkstraEdge = new int[n + 1];
        ArrayList<Integer> augmentingPath;

        //first time
        CommonAlgorithms.dijkstrasAlgorithm(copy, sourceId, dijkstraDist, dijkstraPath, dijkstraEdge);

        try {
            while (dijkstraDist[sinkId] < Integer.MAX_VALUE) //while a path from source to sink exists
            {
                //push as much flow as possible along the shortest path from source to sink
                prev = sinkId;
                augmentingPath = new ArrayList<Integer>();
                maxFlow = Integer.MAX_VALUE; //how much we're entitled to push in this iteration
                do {
                    prevEdge = dijkstraEdge[prev];
                    augmentingPath.add(prevEdge);
                    temp = indexedArcs.get(prevEdge);
                    if (temp.isCapacitySet() && maxFlow > temp.getCapacity()) {
                        maxFlow = temp.getCapacity();
                    }
                    prev = dijkstraPath[prev];
                } while (prev != sourceId);
                //now push it
                for (Integer index : augmentingPath) {
                    temp = indexedArcs.get(index);
                    if (artificialIds[temp.getMatchId()] == temp.getId()) // if we're artificial
                    {
                        ans[temp.getMatchId()] -= maxFlow;
                        //if we don't have a real arc corresponding to this, then add one
                        if (realIds[temp.getMatchId()] == 0) {
                            temp2 = new Arc("real insertion", new Pair<DirectedVertex>(temp.getHead(), temp.getTail()), -temp.getCost());
                            temp2.setCapacity(maxFlow);
                            copy.addEdge(temp2, temp.getMatchId());
                            realIds[temp.getMatchId()] = temp2.getId();
                        }
                        //if we do have a real arc, update its capacity
                        else {
                            temp2 = indexedArcs.get(realIds[temp.getMatchId()]);
                            if (temp2.isCapacitySet())
                                temp2.setCapacity(temp2.getCapacity() + maxFlow);
                        }

                        //update capacities
                        temp.setCapacity(temp.getCapacity() - maxFlow);
                        //remove if the capcity is zero
                        if (temp.getCapacity() == 0) {
                            artificialIds[temp.getMatchId()] = 0;
                            copy.removeEdge(temp);
                        }
                    } else if (realIds[temp.getMatchId()] == temp.getId())//we're real
                    {
                        ans[temp.getMatchId()] += maxFlow;
                        //if  we don't have an artificial arc corresponding to this, then add one
                        if (artificialIds[temp.getMatchId()] == 0) {
                            temp2 = new Arc("real insertion", new Pair<DirectedVertex>(temp.getHead(), temp.getTail()), -temp.getCost());
                            temp2.setCapacity(maxFlow);
                            copy.addEdge(temp2, temp.getMatchId());
                            artificialIds[temp.getMatchId()] = temp2.getId();
                        } else {
                            temp2 = indexedArcs.get(artificialIds[temp.getMatchId()]);
                            temp2.setCapacity(temp2.getCapacity() + maxFlow);
                        }

                        //update capacity of temp
                        if (temp.isCapacitySet())//if no capacity set, then we assume infinite capacity
                        {
                            temp.setCapacity(temp.getCapacity() - maxFlow);
                            //remove if the capcity is zero
                            if (temp.getCapacity() == 0) {
                                realIds[temp.getMatchId()] = 0;
                                copy.removeEdge(temp);
                            }
                        }
                    }
                }

                //reduce costs
                for (Arc a : copy.getEdges()) {
                    a.setCost(a.getCost() + dijkstraDist[a.getTail().getId()] - dijkstraDist[a.getHead().getId()]);
                }

                //recalculate shortest paths
                dist = new int[n + 1];
                path = new int[n + 1];
                CommonAlgorithms.dijkstrasAlgorithm(copy, sourceId, dijkstraDist, dijkstraPath, dijkstraEdge);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
        //trim out artificial flows
        TIntObjectHashMap<Arc> gArcs = g.getInternalEdgeMap();
        int realIndex = 1;
        for (int i = 1; i <= m; i++) {
            while (!gArcs.containsKey(realIndex))
                realIndex++;
            retArray[realIndex] = ans[i];
            realIndex++;
        }
        return retArray;
    }

    /**
     * Implements the cycle cancelling algorithm to calculate a min cost flow through the graph g with distance matrix given by dist.
     * NOTE: Currently does not support capacities; for that, use shortestSuccessivePaths.
     *
     * @param g
     * @return - a map that has node id pairs as keys, and integers as values.  An entry of ((i,j), k) means that k units of flow
     * should be pushed along the shortest path from i to j.
     */
    public static HashMap<Pair<Integer>, Integer> cycleCancelingMinCostNetworkFlow(DirectedGraph g, int[][] dist) throws IllegalArgumentException {
        HashMap<Pair<Integer>, Integer> ans = new HashMap<Pair<Integer>, Integer>();
        HashMap<Integer, Integer> flow = new HashMap<Integer, Integer>();
        ArrayList<DirectedVertex> Dplus = new ArrayList<DirectedVertex>();
        ArrayList<DirectedVertex> Dminus = new ArrayList<DirectedVertex>();
        //vars to check for valid demand setting
        int supply = 0;
        int demand = 0;
        for (DirectedVertex v : g.getVertices()) {
            //only nonzero demands are set
            try {
                if (v.getDemand() > 0) {
                    Dplus.add(v);
                    supply += v.getDemand();
                } else {
                    Dminus.add(v);
                    demand += v.getDemand();
                }
            } catch (NoDemandSetException e) {
                //do nothing
            }
        }

        if (-demand > supply || demand > 0) {
            LOGGER.error("There is insufficient supply to meet demand.");
            throw new IllegalArgumentException();
        }

        //greedily establish an initial feasible flow
        try {
            DirectedVertex u = Dplus.get(0);
            DirectedVertex v = Dminus.get(0); // holds the current Dplus and Dminus nodes respectively
            int i = 0; //counter for Dplus
            int j = 0; //counter for Dminus
            int k = 0; //amount of flow to push from u to v
            int leftover = u.getDemand();
            int leftover2 = v.getDemand(); //counters for remaining supply/demand
            while (j < Dminus.size()) {
                k = (-leftover2 < leftover) ? -leftover2 : leftover;
                ans.put(new Pair<Integer>(u.getId(), v.getId()), k);
                leftover -= k;
                leftover2 += k;

                if (leftover == 0 && ++i != Dplus.size()) {
                    u = Dplus.get(i);
                    leftover = u.getDemand();
                }
                if (leftover2 == 0 && ++j != Dminus.size()) {
                    v = Dminus.get(j);
                    leftover2 = v.getDemand();
                }
            }

            //set up the reduced graph (the base for the residual)
            ArrayList<DirectedVertex> DallResid;
            DirectedVertex temp;
            Arc toAdd;
            DirectedGraph reduced = new DirectedGraph();
            DallResid = new ArrayList<DirectedVertex>();
            for (DirectedVertex v1 : Dplus) {
                temp = new DirectedVertex("resid plus");
                reduced.addVertex(temp, v1.getId());
                DallResid.add(temp);
            }
            for (DirectedVertex v1 : Dminus) {
                temp = new DirectedVertex("resid minus");
                reduced.addVertex(temp, v1.getId());
                DallResid.add(temp);
            }
            //add all the normal edges
            for (i = 0; i < DallResid.size(); i++) {
                u = DallResid.get(i);
                for (j = 0; j < DallResid.size(); j++) {
                    if (i == j)
                        continue;
                    v = DallResid.get(j);
                    toAdd = new Arc("reduced original", new Pair<DirectedVertex>(u, v), 1 + dist[u.getMatchId()][v.getMatchId()]);
                    reduced.addEdge(toAdd);
                    //if it's part of our greedy solution, then record the amount of flow in flow
                    Pair<Integer> key = new Pair<Integer>(u.getMatchId(), v.getMatchId());
                    if (ans.containsKey(key)) {
                        flow.put(toAdd.getId(), ans.get(key));
                    }
                }
            }


            //setup the residual graph
            boolean improvements = true;
            DirectedGraph resid;
            TIntObjectHashMap<DirectedVertex> reducedVertexMap = reduced.getInternalVertexMap();

            //while there's a negative cycle
            while (improvements) {
                improvements = false;
                //preliminaries
                resid = getResidualGraph(reduced, flow);

                //solve the all pairs shortest paths
                int n = resid.getVertices().size();
                int[][] residDist = new int[n + 1][n + 1];
                int[][] residPath = new int[n + 1][n + 1];
                fwLeastCostPaths(resid, residDist, residPath);

                //cancel negative cycles
                Pair<Integer> pair;
                for (i = 1; i <= n; i++) {
                    if (residDist[i][i] < 0) //negative cycle detected
                    {
                        k = 0;
                        int b, c;
                        int fvu = 0;
                        boolean kunset = true;
                        b = i;
                        //calculate how much flow we can push around it
                        do {
                            c = residPath[b][i];
                            u = resid.getInternalVertexMap().get(b);
                            v = resid.getInternalVertexMap().get(c);
                            List<Arc> connections = v.getNeighbors().get(u);
                            for (Arc a : connections) {
                                if (a.getCost() == -residDist[b][c] && residDist[b][c] < 0) {
                                    fvu = flow.get(a.getMatchId());
                                    break;
                                }
                            }
                            if (residDist[b][c] < 0 && (kunset || k > fvu)) {
                                k = fvu;
                                kunset = false;
                            }
                        } while ((b = c) != i);
                        //cancel k units of flow around the cycle
                        b = i;
                        do {
                            c = residPath[b][i];
                            u = resid.getInternalVertexMap().get(b);
                            v = resid.getInternalVertexMap().get(c);
                            if (residDist[b][c] < 0) {
                                pair = new Pair<Integer>(reducedVertexMap.get(v.getMatchId()).getMatchId(), reducedVertexMap.get(u.getMatchId()).getMatchId());
                                List<Arc> connections = v.getNeighbors().get(u); //to find the correct guy to cancel
                                for (Arc a : connections) {
                                    if (a.getCost() == -residDist[b][c]) {
                                        flow.put(a.getMatchId(), flow.get(a.getMatchId()) - k);
                                        break;
                                    }
                                }
                                if (ans.get(pair) == k)
                                    ans.remove(pair);
                                else
                                    ans.put(pair, ans.get(pair) - k);
                            } else {
                                pair = new Pair<Integer>(reducedVertexMap.get(u.getMatchId()).getMatchId(), reducedVertexMap.get(v.getMatchId()).getMatchId());
                                List<Arc> connections = u.getNeighbors().get(v);
                                for (Arc a : connections) {
                                    if (a.getCost() == residDist[b][c]) {
                                        if (!flow.containsKey(a.getMatchId()))
                                            flow.put(a.getMatchId(), k);
                                        else
                                            flow.put(a.getMatchId(), flow.get(a.getMatchId()) + k);
                                        break;
                                    }
                                }
                                if (!ans.containsKey(pair))
                                    ans.put(pair, k);
                                else
                                    ans.put(pair, ans.get(pair) + k);
                            }


                        } while ((b = c) != i);
                        improvements = true;
                        break;
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return ans;
    }

    /**
     * Finds the minimum cost spanning arborescence if one exists.
     * NOTE: Be careful about scale; this will only work with graphs
     * of vertex set size 255 or less I think.  Consider writing my own MSA.
     *
     * @param g - the graph on which to compute the arborescence
     * @return - A set of integers, corresponding to the ids of the arcs
     * in the MSA.
     */
    public static HashSet<Integer> minSpanningArborescence(DirectedGraph g, int root) throws IllegalArgumentException {
        try {
            int n = g.getVertices().size();
            int m = g.getEdges().size();
            HashSet<Integer> ans = new HashSet<Integer>();

            //graph is already connected!
            if(n==1)
                return new HashSet<Integer>();

            //error checking
            if (!g.getInternalEdgeMap().containsKey(root)) {
                LOGGER.error("You had specified a root that is not in the graph.");
                throw new IllegalArgumentException();
            }

            //inputs to MSArbor
            int[] weights = new int[n * (n - 1)];

            TIntObjectHashMap<DirectedVertex> gVertices = g.getInternalVertexMap();
            DirectedVertex dv1, dv2;
            List<Arc> tempConnections;
            int tempMin, realI, realJ;
            Arc kConnect;
            for (int i = 1; i <= n; i++) {
                realI = i - 1; //the index as far as MSArbor is concerned
                dv1 = gVertices.get(i);
                for (int j = 1; j <= n; j++) //j <= n-1 since we assume the last guy is the root
                {
                    if (j == root) //if it's to root, ignore it
                        continue;
                    else if (j == n) //if it's to the last node, swap it with the root 
                        realJ = root - 1;
                    else // oth. normal
                        realJ = j - 1;
                    dv2 = gVertices.get(j);
                    tempConnections = g.findEdges(new Pair<DirectedVertex>(dv1, dv2));
                    if (tempConnections.size() > 0) {
                        tempMin = tempConnections.get(0).getCost();
                        for (int k = 1; k < tempConnections.size(); k++) {
                            kConnect = tempConnections.get(k);
                            if (kConnect.getCost() < tempMin)
                                tempMin = kConnect.getCost();
                        }

                        //swap root and the last guy
                        if (i == root) // if it's from the root, move it to the last row
                            weights[(n - 1) * (n - 1) + realJ] = tempMin;
                        else if (i == n) // if it's from the last guy, move it to root
                            weights[(n - 1) * (root - 1) + realJ] = tempMin;
                        else // oth. normal
                            weights[(n - 1) * realI + realJ] = tempMin;
                    } else {
                        //swap root and the last guy
                        if (i == root) // if it's from the root, move it to the last row
                            weights[(n - 1) * (n - 1) + realJ] = 32766;
                        else if (i == n) // if it's form the last guy, move it to the root
                            weights[(n - 1) * (root - 1) + realJ] = 32766;
                        else // oth. normal
                            weights[(n - 1) * realI + realJ] = 32766;
                        // tells MSArbor there is no arc here
                    }
                }
            }

            int[] predArray = MSArbor.msArbor(n, m, weights);
            //now figure out the ids of the arcs contained within.
            int tempMinID;
            for (int i = 0; i < predArray.length; i++) {
                if (predArray[i] == root - 1) // if the solution says the prev. guy is the root, it's really n
                    dv1 = gVertices.get(n);
                else if (predArray[i] == n - 1) // if the solution says the prev. guy is the nth vertex, it's really the root
                    dv1 = gVertices.get(root);
                else // oth. normal
                    dv1 = gVertices.get(predArray[i] + 1);

                if (i == root - 1) // if we're inspecting the root position, we're really looking at the nth vertex
                    dv2 = gVertices.get(n);
                else if (i == n - 1) // if we're inspecting the nth position, we're really looking at the root vertex
                    dv2 = gVertices.get(root);
                else // oth. normal
                    dv2 = gVertices.get(i + 1);

                tempConnections = g.findEdges(new Pair<DirectedVertex>(dv1, dv2));

                if (tempConnections.size() == 0)
                    throw new Exception("Something went wrong in the MSArbor solver");
                tempMin = tempConnections.get(0).getCost();
                tempMinID = tempConnections.get(0).getId();
                for (int k = 1; k < tempConnections.size(); k++) {
                    kConnect = tempConnections.get(k);
                    if (kConnect.getCost() < tempMin) {
                        tempMin = kConnect.getCost();
                        tempMinID = kConnect.getId();
                    }
                }
                ans.add(tempMinID);

            }
            return ans;

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Performs  min-cost perfect matching using Kolmogorov's publicly available Blossom V C code.
     *
     * @param graph
     * @return
     */
    public static Set<Pair<UndirectedVertex>> minCostMatching(UndirectedGraph graph) throws UnsupportedFormatException {
        HashSet<Pair<UndirectedVertex>> matching = new HashSet<Pair<UndirectedVertex>>();
        TIntObjectHashMap<UndirectedVertex> indexedVertices = graph.getInternalVertexMap();

        //setup our input to Kolmogorov's Blossom V code
        int n = graph.getVertices().size();
        int m = graph.getEdges().size();
        int[] edges = new int[2 * m];
        int[] weights = new int[m];

        //edges[2m] and edges[2m+1] hold the endpoints of each edge, weight[m] holds the weight between them.
        for (Edge e : graph.getEdges()) {
            //the c code indexes things by zero, so we need to be compliant
            edges[2 * e.getId() - 2] = e.getEndpoints().getFirst().getId() - 1;
            edges[2 * e.getId() - 1] = e.getEndpoints().getSecond().getId() - 1;
            weights[e.getId() - 1] = e.getCost();
        }


        int[] ans = BlossomV.blossomV(n, m, edges, weights);

        //to make sure we only report unique pairs, (and not, say 0-1 and 1-0).
        ArrayList<Integer> matched = new ArrayList<Integer>();

        //now reinterpret the results
        for (int i = 0; i < ans.length; i++) {
            if (matched.contains(i))
                continue;
            matching.add(new Pair<UndirectedVertex>(indexedVertices.get(ans[i] + 1), indexedVertices.get(i + 1)));
            matched.add(ans[i]);

        }
        return matching;
    }

    public static WindyGraph collapseIndices(WindyGraph input) {
        try {
            WindyGraph ans = new WindyGraph();
            TIntObjectHashMap<WindyEdge> indexedEdges = input.getInternalEdgeMap();
            TIntObjectHashMap<WindyVertex> indexedVertices = input.getInternalVertexMap();
            WindyVertex temp, temp2;

            TIntArrayList forSortingV = new TIntArrayList(indexedVertices.keys());
            forSortingV.sort();
            int n = input.getVertices().size();
            TIntIntHashMap reverseMap = new TIntIntHashMap();

            for (int i = 0; i < n; i++) {
                temp2 = indexedVertices.get(forSortingV.get(i));
                temp = new WindyVertex(temp2.getLabel()); //the new guy
                temp.setCoordinates(temp2.getX(), temp2.getY());
                if (temp2.isDemandSet())
                    temp.setDemand(temp2.getDemand());
                ans.addVertex(temp, temp2.getId());
                reverseMap.put(forSortingV.get(i), i + 1);
            }
            WindyEdge e, e2;
            TIntArrayList forSortingE = new TIntArrayList(indexedEdges.keys());
            forSortingE.sort();
            int m = forSortingE.size();
            for (int i = 0; i < m; i++) {
                e = indexedEdges.get(forSortingE.get(i));
                e2 = new WindyEdge(e.getLabel(), new Pair<WindyVertex>(ans.getVertex(reverseMap.get(e.getFirstEndpointId())), ans.getVertex(reverseMap.get(e.getSecondEndpointId()))), e.getCost(), e.getReverseCost());
                e2.setMatchId(e.getId());
                e2.setRequired(e.isRequired());
                e2.setZone(e.getZone());
                e2.setType(e.getType());
                e2.setMaxSpeed(e.getMaxSpeed());
                ans.addEdge(e2, e.getId());
            }

            ans.setDepotId(input.getDepotId());
            return ans;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }


}
