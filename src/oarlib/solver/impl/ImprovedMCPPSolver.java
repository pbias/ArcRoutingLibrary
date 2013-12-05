package oarlib.solver.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

import oarlib.core.Arc;
import oarlib.core.Edge;
import oarlib.core.MixedEdge;
import oarlib.core.MultiEdge;
import oarlib.core.MultiEdge.EDGETYPE;
import oarlib.core.Problem;
import oarlib.core.Problem.Type;
import oarlib.core.Route;
import oarlib.core.Solver;
import oarlib.graph.impl.DirectedGraph;
import oarlib.graph.impl.MixedGraph;
import oarlib.graph.impl.UndirectedGraph;
import oarlib.graph.util.CommonAlgorithms;
import oarlib.graph.util.Pair;
import oarlib.problem.impl.MixedCPP;
import oarlib.vertex.impl.DirectedVertex;
import oarlib.vertex.impl.MixedVertex;
import oarlib.vertex.impl.UndirectedVertex;

public class ImprovedMCPPSolver extends Solver {

	MixedCPP mInstance;
	public ImprovedMCPPSolver(MixedCPP instance) throws IllegalArgumentException {
		super(instance);
		mInstance = instance;
	}


	@Override
	protected Collection<Route> solve() {
		try{
			//Compute the inputs G, Gm and G star
			MixedGraph G = mInstance.getGraph(); //original
			HashMap<Integer, MixedEdge> gEdges = G.getInternalEdgeMap();
			ArrayList<MultiEdge<MixedEdge>> gEdgeContainers = new ArrayList<MultiEdge<MixedEdge>>(); //machinery to help us keep track of status in G*
			gEdgeContainers.add(null); //we want indices to be sympatico with the ids
			int m = G.getEdges().size();
			for(int i = 1; i < m+1; i++)
			{
				gEdgeContainers.add(new MultiEdge<MixedEdge>(gEdges.get(i)));
			}
			MixedGraph Gm = G.getDeepCopy(); // original + even degree
			MixedGraph Gstar = G.getDeepCopy(); // original + in-out degree

			//Vars for bookkeeping
			ArrayList<MixedEdge> U = new ArrayList<MixedEdge>();
			ArrayList<MixedEdge> M = new ArrayList<MixedEdge>();
			ArrayList<MixedEdge> Em = new ArrayList<MixedEdge>();
			ArrayList<MixedEdge> Am = new ArrayList<MixedEdge>();
			ArrayList<Boolean> inMdubPrime =  new ArrayList<Boolean>();


			evenDegree(Gm, Em, Am); //modified even degree to store the edges and arcs added, (the guys in Em, and Am will be in Gm afterwards)
			inOutDegree(Gstar, U, M, inMdubPrime, gEdgeContainers); //Gstar doesn't actually get modified, so it's still G after this. Initializes type correctly.

			//start SAPH
			boolean improvements = true; //whether or not improvements were made in in block 2
			MixedGraph Gij1;
			MixedGraph Gij2;
			MixedGraph Gij3;
			MixedGraph Gij4;
			ArrayList<MultiEdge<MixedEdge>> Gnew;
			EDGETYPE iStat = null;
			int idToImprove, i, j;
			int n = G.getVertices().size();
			int[][] dist, dist2;
			int[][] path, path2;
			int[][] edgePath, edgePath2;
			int cost1, cost2;
			MultiEdge<MixedEdge> toImprove;
			HashMap<Integer, MixedVertex> gij1Vertices;
			HashMap<Integer, MixedEdge> gij1Edges;
			HashMap<Integer, MixedEdge> gij3Edges;
			HashMap<Integer, MixedEdge> gij4Edges;
			List<MixedEdge> toRemove;
			int curr, next, end;
			int start = 1;
			while(improvements) //while we're making progress
			{
				improvements = false;
				//SAPH Concept 1
				while(true) // links of type acdf in Gstar, so we carry out the first part of SAPH
				{
					//pick a random one
					idToImprove = 0;
					for(int k = start; k < m+1; k++)
					{
						iStat = gEdgeContainers.get(k).getType();
						if(iStat == EDGETYPE.A || iStat == EDGETYPE.C || iStat == EDGETYPE.D || iStat == EDGETYPE.F)
						{
							idToImprove = k;
							start = k+1;
							break;
						}
					}
					if(idToImprove == 0) //no acdf edges left
						break;
					toImprove = gEdgeContainers.get(idToImprove); //the edge container that we want to operate on
					i = toImprove.getFirst().getEndpoints().getFirst().getId(); //vertex i
					j = toImprove.getFirst().getEndpoints().getSecond().getId(); //vertex j
					//initialize
					Gij1 = G.getDeepCopy();
					Gij2 = G.getDeepCopy();
					gij1Vertices = Gij1.getInternalVertexMap();
					gij1Edges  = Gij1.getInternalEdgeMap();

					CostMod1(Gij1, gEdgeContainers, Em, Am); //cost mod 1 on Gij1
					CostMod2(Gij1, G, gEdgeContainers, i, j); //cost mod 2 on Gij1
					CostMod2(Gij2, G, gEdgeContainers, i, j); //cost mod 2 on Gij2

					//set Cij and Cji to infinity in Gij1
					toRemove = Gij1.findEdges(new Pair<MixedVertex>(gij1Vertices.get(i), gij1Vertices.get(j)));
					toRemove.addAll(Gij1.findEdges(new Pair<MixedVertex>(gij1Vertices.get(j), gij1Vertices.get(i))));
					for(MixedEdge elim: toRemove)
					{
						Gij1.removeEdge(elim);
					}

					//solve the shortest paths problem in Gij1
					dist = new int[n+1][n+1]; //we want to use distances in Gij2, but shortest paths from Gij1
					path = new int[n+1][n+1];
					edgePath = new int[n+1][n+1];
					path2 = new int[n+1][n+1];
					CommonAlgorithms.fwLeastCostPaths(Gij1, dist, path, edgePath);
					dist = new int[n+1][n+1];
					CommonAlgorithms.fwLeastCostPaths(Gij2, dist, path2);
					//now branch off; if we're type a or d, we need to check both directions SP ij, and SP ji; if we're type c or f, just SP ij
					if(iStat == EDGETYPE.A || iStat == EDGETYPE.D)
					{
						cost1 = 0; // cost of SPij
						cost2 = 0; // cost of SPji
						//SP ij
						curr = i;
						end = j;
						do
						{
							next = path[curr][end];
							cost1 += dist[curr][next];
						} while((curr = next) != end);
						//SP ji
						curr = j;
						end = i;
						do
						{
							next = path[curr][end];
							cost2 += dist[curr][next];
						}while((curr = next) != end);

						//now do the cost comparisons to decide whether it's fruitful to replace
						if(cost1 < toImprove.getFirst().getCost() && cost1 < cost2)
						{
							start = 1;
							//add SPij, and if type a, orient from j to i, if type d, get rid of the ij direction
							toImprove.directBackward();
							//add SPij
							addShortestPathAndUpdate(i, j, path, edgePath, gij1Edges, gEdgeContainers);
						}
						else if(cost2 < toImprove.getFirst().getCost() && cost2 < cost1)
						{
							start = 1;
							//add SPji, and if type a, orient from i to j, if type d, get rid of the ji direction
							toImprove.directForward();
							//add SPji
							addShortestPathAndUpdate(j, i, path, edgePath, gij1Edges, gEdgeContainers);
						}
					}
					else //it's c, or f
					{
						cost1 = 0;
						curr = (toImprove.isDirectedBackward())?j:i;
						end = (toImprove.isDirectedBackward())?i:j;
						do
						{
							next = path[curr][end];
							cost1 += dist[curr][next];
						} while((curr = next) != end);

						//now do the cost comparisons to decide whether it's fruitful to replace
						if(cost1 < toImprove.getFirst().getCost())
						{
							start = 1;
							//add SPij or SPji, and delete a copy of ij or ji
							curr = (toImprove.isDirectedBackward())?j:i;
							addShortestPathAndUpdate(curr, end, path, edgePath, gij1Edges, gEdgeContainers);
						}
					}

					//to check for directed cycles, make a graph ONLY using the arcs in Mdubprime, as those are the only we 
					//can afford to delete
					eliminateAddedDirectedCycles(n, gEdgeContainers);
				}

				start = 1;
				while(true) // links of type b in Gstar, so we carry out the first part of SAPH
				{
					//pick a random one
					idToImprove = 0;
					for(int k = start; k < m+1; k++)
					{
						iStat = gEdgeContainers.get(k).getType();
						if(iStat == EDGETYPE.B)
						{
							idToImprove = k;
							start = k+1;
							break;
						}
					}
					if(idToImprove == 0) //no acdf edges left
						break;
					toImprove = gEdgeContainers.get(idToImprove); //the edge container that we want to operate on
					i = toImprove.getFirst().getEndpoints().getFirst().getId(); //vertex i
					j = toImprove.getFirst().getEndpoints().getSecond().getId(); //vertex j
					//initialize
					Gij3 = G.getDeepCopy();
					Gij4 = G.getDeepCopy();
					gij3Edges = Gij3.getInternalEdgeMap();
					gij4Edges = Gij4.getInternalEdgeMap();

					CostMod2(Gij3, G, gEdgeContainers, i, j);
					
					dist = new int[n+1][n+1]; //find SPij in Gij3
					path = new int[n+1][n+1];
					edgePath = new int[n+1][n+1];
				
					CommonAlgorithms.fwLeastCostPaths(Gij3, dist, path, edgePath);
					
					//calculate cost
					cost1 = 0;
					curr = (toImprove.isDirectedBackward())?j:i;
					end = (toImprove.isDirectedBackward())?i:j;
					do
					{
						next = path[curr][end];
						cost1 += dist[curr][next];
					} while((curr = next) != end);
					
					//clone gEdgeContainers, which effectively represents Gstar
					Gnew = new ArrayList<MultiEdge<MixedEdge>>();
					Gnew.add(null);
					for(int k = 1; k < gEdgeContainers.size(); k++)
					{
						Gnew.add(gEdgeContainers.get(k).getCopy());
					}
					
					//now add SPij to Gnew
					curr = (toImprove.isDirectedBackward())?j:i;
					addShortestPathAndUpdate(curr, end, path, edgePath, gij3Edges,Gnew);
					
					CostMod2(Gij4, G, Gnew, i, j);
					
					dist2 = new int[n+1][n+1]; //find SPij in Gij4
					path2 = new int[n+1][n+1];
					edgePath2 = new int[n+1][n+1];
					
					CommonAlgorithms.fwLeastCostPaths(Gij4, dist2, path2, edgePath2);
					
					//calculate cost
					cost2 = 0;
					curr = (toImprove.isDirectedBackward())?j:i;
					end = (toImprove.isDirectedBackward())?i:j;
					do
					{
						next = path[curr][end];
						cost2 += dist[curr][next];
					} while((curr = next) != end);
					
					//check if costs line up, and then add accordingly
					if(cost1 + cost2 < 0)
					{
						start = 1;
						improvements = true;
						if(toImprove.isDirectedForward())
							toImprove.directBackward();
						else
							toImprove.directForward();
						addShortestPathAndUpdate(curr, end, path, edgePath, gij3Edges,gEdgeContainers);
						addShortestPathAndUpdate(curr, end, path2, edgePath2, gij4Edges,gEdgeContainers);
					}
				}
			}
			//replace any remaining type a's with type d's
			for(int k=0;k<gEdgeContainers.size(); k++)
			{
				toImprove = gEdgeContainers.get(k);
				if(toImprove.getType() == EDGETYPE.A)
					toImprove.addReverseCopy();
			}
			
			//now finally construct the eulerian mixed graph.
			G.clearEdges();
			MixedEdge e;
			for(int k=0; k<gEdgeContainers.size(); k++)
			{
				toImprove = gEdgeContainers.get(k);
				e = toImprove.getFirst();
				if(toImprove.isDirectedForward())
				{
					for(int k2 = 0; k2 < toImprove.getNumCopies() + 1; k2++)
					{
						G.addEdge(new MixedEdge("final", e.getEndpoints(),e.getCost(), true));
					}
				}
				else if(toImprove.isDirectedBackward())
				{
					for(int k2 = 0; k2 < toImprove.getNumCopies() + 1; k2++)
					{
						G.addEdge(new MixedEdge("final", new Pair<MixedVertex>(e.getEndpoints().getSecond(), e.getEndpoints().getFirst()),e.getCost(), true));
					}
				}
				else
				{
					G.addEdge(new MixedEdge("final", new Pair<MixedVertex>(e.getEndpoints().getSecond(), e.getEndpoints().getFirst()),e.getCost(), true));
					G.addEdge(new MixedEdge("final", e.getEndpoints(),e.getCost(), true));
				}
			}
			if(CommonAlgorithms.isStronglyEulerian(G))
				System.out.println("Halleleujah!!!");

			return null;
		} catch(Exception ex)
		{
			ex.printStackTrace();
			return null;
		}
	}
	/**
	 * Eliminates directed cycles in the mixed graph if valid (that is, it only eliminates those cycles consisting entirely
	 * of added edges, as specified by M and inMdubPrime)
	 * @param input - the graph in which we wish to carry out the elimination.
	 * @param M - a list that contains the arcs in input which have been added (and are therefore candidates for removal).
	 */
	private static void eliminateAddedDirectedCycles(int n, ArrayList<MultiEdge<MixedEdge>> edgeContainers) throws IllegalArgumentException
	{
		try {
			int mSize = edgeContainers.size() - 1;
			DirectedGraph add = new DirectedGraph();
			for(int i = 1; i < n + 1; i ++)
			{
				add.addVertex(new DirectedVertex(""),i);
			}
			//only add the edges that correspond to the added ones before.
			HashMap<Integer, DirectedVertex> addVertices = add.getInternalVertexMap();
			MixedEdge e;
			MultiEdge<MixedEdge> temp;
			for(int i = 1; i < mSize + 1; i++)
			{
				temp = edgeContainers.get(i);
				if(temp.getNumCopies() == 0) //a, b, or e
					continue;
				e = temp.getFirst();
				if(temp.isDirectedForward()) //don't mess with the added edges.
					add.addEdge(new Arc("for cycle elimination", new Pair<DirectedVertex>(addVertices.get(e.getEndpoints().getFirst().getId()), addVertices.get(e.getEndpoints().getSecond().getId())), e.getCost()), e.getId());
				else
					add.addEdge(new Arc("for cycle elimination", new Pair<DirectedVertex>(addVertices.get(e.getEndpoints().getSecond().getId()), addVertices.get(e.getEndpoints().getFirst().getId())), e.getCost()), e.getId());
				
			}

			int[][] dist;
			int[][] path;
			int[][] edgePath;
			boolean cycleDetected = true;
			int curr,next, nextEdge;
			HashMap<Integer, Arc> addArcs = add.getInternalEdgeMap();
			Arc u;
			while(cycleDetected)
			{
				cycleDetected = false;
				dist = new int[n+1][n+1];
				path = new int[n+1][n+1];
				edgePath = new int[n+1][n+1];
				CommonAlgorithms.fwLeastCostPaths(add, dist, path, edgePath);
				for(int i = 1; i < n+1; i++)
				{
					if(dist[i][i] < Integer.MAX_VALUE)
					{
						cycleDetected = true;
						//remove cycle
						curr = i;
						do {
							next = path[curr][i];
							nextEdge = edgePath[curr][i];
							//delete the arc both in add, and in the input graph
							System.out.println("nextEdge: " + nextEdge);
							u = addArcs.get(nextEdge); //the arc in add
							System.out.println("u's match id:" + u.getMatchId());
							edgeContainers.get(u.getMatchId()).tryRemoveCopy(); //the mixed edge in input
							add.removeEdge(u);
						}while((curr = next) != i);
						break;
					}
				}
			}
		} catch (Exception ex)
		{
			ex.printStackTrace();
			return;
		}
	}
	private static void addShortestPathAndUpdate(int start, int end, int[][]path, int[][] edgePath, HashMap<Integer, MixedEdge> gijEdges, ArrayList<MultiEdge<MixedEdge>> edgeContainers)
	{

		try{
			int curr = start;
			int next;
			boolean forward;
			MultiEdge<MixedEdge> toEdit;
			do 
			{
				next = path[curr][end];
				//attempt to add a copy along each container
				toEdit = edgeContainers.get(gijEdges.get(edgePath[curr][end]).getMatchId());

				//determine the direction that we're actually traversing the thing
				if(next == toEdit.getFirst().getEndpoints().getSecond().getId()) //we're walking forward
					forward = true;
				else
					forward = false;
				//cases for the various states of the edge
				if(toEdit.getType() == EDGETYPE.A) //direct it in the direction of traversal
				{
					if(forward)
						toEdit.directForward();
					else
						toEdit.directBackward();
				}
				else if(toEdit.getType() == EDGETYPE.B) //add a copy or reverse copy depending on direction of traversal
				{
					if(forward == toEdit.isDirectedForward())
						toEdit.addCopy();
					else 
						toEdit.addReverseCopy();

				}
				else if(toEdit.getType() == EDGETYPE.C) //add a copy, or remove a copy depending on direction of traversal
				{
					if(forward == toEdit.isDirectedForward())
						toEdit.addCopy();
					else
						toEdit.tryRemoveCopy();
				}
				else if(toEdit.getType() == EDGETYPE.D) //orient it properly depending on direction
				{
					if(forward)
						toEdit.directForward();
					else
						toEdit.directBackward();
				}
				else if(toEdit.getType() == EDGETYPE.E) // add a copy
				{
					toEdit.addCopy();
				}
				else if(toEdit.getType() == EDGETYPE.F) // add a copy or remove a copy depending on direction of traversal
				{
					if(forward)
						toEdit.addCopy();
					else
						toEdit.tryRemoveCopy();
				}
			} while((curr = next) != end);
		} catch(Exception ex)
		{
			ex.printStackTrace();
			return;
		}
	}
	/**
	 * The first cost modification method proposed by Yaoyuenyong.  This reduces costs of edges / arcs added from evendegree's matching
	 * procedure.  These graphs are used for shortest path costs only.
	 * @param Gij - the graph to be modified ( in almost all cases, the original graph G )
	 * @param type - the ith entry holds the type/status of the ith edge in G as it appears in G*
	 * @param Em - An arraylist of edges (undirected) added to G during evendegree's matching phase.
	 * @param Am - An arraylist of arcs (directed) added to G during evendegree's matching.
	 */
	private static void CostMod1(MixedGraph Gij, ArrayList<MultiEdge<MixedEdge>> edgeContainers, ArrayList<MixedEdge> Em, ArrayList<MixedEdge> Am) throws IllegalArgumentException
	{
		try {
			int K = -5; //labeled 'attractive cost'
			HashMap<Integer, MixedEdge> gijEdges = Gij.getInternalEdgeMap();
			MixedEdge temp;
			for(MixedEdge e: Em)
			{
				if(e.isDirected())
					throw new IllegalArgumentException("Em is malformed.");
				//reduce the cost of this edge
				temp = gijEdges.get(e.getMatchId());
				temp.setCost(K);
			}
			for(MixedEdge a: Am)
			{
				if(!a.isDirected())
					throw new IllegalArgumentException("Am is malformed.");
				//reduce the cost of this arc
				temp = gijEdges.get(a.getMatchId());
				if(edgeContainers.get(a.getMatchId()).getType() == EDGETYPE.E)
				{
					temp.setCost(0);
				}
				else if(edgeContainers.get(a.getMatchId()).getType() == EDGETYPE.F)
				{
					temp.setCost(K);
					Gij.addEdge(new MixedEdge("from cost mod 1", new Pair<MixedVertex>(temp.getHead(), temp.getTail()), K, true), temp.getId()); //going to need to figure out who to mod if we traverse this arc.
				}
				else
				{
					throw new IllegalArgumentException("Wrong type.");
				}
			} 
			return;
		} catch(Exception e)
		{
			e.printStackTrace();
			return;
		}
	}
	/**
	 * The second cost modification method proposed by Yoayuenyong.  This reduces the cost of edges / arcs that stand to benefit from cycle
	 * elimination during the improvement procedure (in other words, if an added arc / edge gets deleted as a part of deleting a circuit, we want to 
	 * take that into consideration when thinking about which cycles to eliminate). 
	 * @param Gij
	 * @param G
	 * @param type
	 * @param i
	 * @param j
	 */
	private static void CostMod2(MixedGraph Gij, MixedGraph G, ArrayList<MultiEdge<MixedEdge>> edgeContainers, int i, int j)
	{
		try {
			EDGETYPE temp;
			int cost;
			MixedEdge e, e2;
			HashMap<Integer, MixedEdge> gEdges = G.getInternalEdgeMap();
			HashMap<Integer, MixedEdge> gijEdges = Gij.getInternalEdgeMap();
			int m = edgeContainers.size() - 1;
			for(int k = 1; k < m + 1; k++)
			{
				temp = edgeContainers.get(k).getType();
				if(temp == EDGETYPE.A || temp == EDGETYPE.D)
				{
					e = gEdges.get(k);
					cost = e.getCost();
					gijEdges.get(k).setCost(-cost);

				}
				else if(temp == EDGETYPE.C || temp == EDGETYPE.F)
				{
					e = gEdges.get(k);
					e2 = gijEdges.get(k);
					cost = e.getCost();
					gijEdges.get(k).setCost(-cost);
					Gij.addEdge(new MixedEdge("", new Pair<MixedVertex>(e2.getHead(), e2.getTail()), -cost, true), e.getId()); //going to need to figure out who to mod if we traverse this arc.
				}
			}
			//now delete all links between vertex i and vertex j
			HashMap<Integer, MixedVertex> gijVertices = Gij.getInternalVertexMap();
			List<MixedEdge> toRemove = Gij.findEdges(new Pair<MixedVertex>(gijVertices.get(i), gijVertices.get(j)));
			toRemove.addAll(Gij.findEdges(new Pair<MixedVertex>(gijVertices.get(j), gijVertices.get(i))));
			for(MixedEdge elim: toRemove)
			{
				Gij.removeEdge(elim);
			}

			return;
		} catch(Exception e)
		{
			e.printStackTrace();
			return;
		}
	}
	@Override
	protected Problem getInstance() {
		return mInstance;
	}
	/**
	 * Essentially solves the DCPP on the Mixed Graph, as in Mixed 1 of Frederickson.
	 * @param input - a mixed graph
	 * @param U - should be an empty ArrayList.  At the end, it will contain edges for whom we are still unsure of their orientation
	 * @param M - should be an empty ArrayList.  At the end, it will contain arcs and edges for whom we know orientations
	 * @param inMdubPrime - should be an empty ArrayList.  At the end, it will be of the same size as M, and will hold true if the arc is a duplicate, and false if it's an original
	 * @param type - should be an empty char array of length m+1 that spells out what happened to the edges in input;
	 * a - original edge remains unoriented 
	 * b - original edge was directed, but not duplicated
	 * c - original edge was directed, and duplicated in the same direction
	 * d - original edge was directed, and duplicated once in the opposite direction (doesn't happen here)
	 * e - original arc remains unduplicated
	 * f - original arc was duplicated
	 */
	private static void inOutDegree(MixedGraph input, ArrayList<MixedEdge> U, ArrayList<MixedEdge> M, ArrayList<Boolean> inMdubPrime, ArrayList<MultiEdge<MixedEdge>> edgeContainers)
	{
		try {
			DirectedGraph setup = new DirectedGraph();
			for(int i = 1; i < input.getVertices().size() + 1; i++)
			{
				setup.addVertex(new DirectedVertex("symmetric setup graph"), i);
			}
			Arc a;
			MixedEdge e;
			HashMap<Integer, MixedEdge> inputEdges = input.getInternalEdgeMap();
			HashMap<Integer, MixedVertex> inputVertices = input.getInternalVertexMap();
			HashMap<Integer, DirectedVertex> setupVertices = setup.getInternalVertexMap();
			HashMap<Integer, Arc> setupEdges = setup.getInternalEdgeMap();
			int m = input.getEdges().size();
			for(int i = 1; i < m + 1; i++)
			{
				e = inputEdges.get(i);
				if(e.isDirected())
				{
					setup.addEdge(new Arc("symmetric setup graph", new Pair<DirectedVertex>(setupVertices.get(e.getTail().getId()), setupVertices.get(e.getHead().getId())), e.getCost()), e.getId());
				}
				else
				{
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
			for(DirectedVertex v: setup.getVertices())
			{
				if(v.getDelta() != 0)
				{
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
			int[] undirTraversals = new int[m+1]; 
			int setupM = setup.getEdges().size();
			MixedVertex temp; 
			MultiEdge<MixedEdge> eContainer;
			//iterate through flow solution, and add appropriate number of guys
			for(int i = 1; i < setupM + 1; i ++)
			{
				e = inputEdges.get(setupEdges.get(i).getMatchId());
				eContainer = edgeContainers.get(e.getId());
				a = setupEdges.get(i);
				if (e.isDirected())
				{
					//add back the original
					M.add(new MixedEdge("copy from symmetry", new Pair<MixedVertex>(e.getTail(), e.getHead()), e.getCost(), true));
					inMdubPrime.add(false);
					undirTraversals[e.getId()] = 2;
					//e should have been set by edgeContainer's initialization
					for(int j = 0; j < flowanswer[i]; j++)
					{
						//add copy to M
						M.add(new MixedEdge("copy from symmetry", new Pair<MixedVertex>(e.getTail(), e.getHead()), e.getCost(), true));
						inMdubPrime.add(true);
						eContainer.addCopy();//f
					}
				}
				else if(!a.isCapacitySet()) //arc corresponding to an edge, but not artificial
				{
					for(int j = 0; j < flowanswer[i]; j++)
					{
						//add copy to M
						M.add(new MixedEdge("copy from symmetry", new Pair<MixedVertex>(inputVertices.get(a.getTail().getId()), inputVertices.get(a.getHead().getId())), e.getCost(), true));
						inMdubPrime.add(true);
						//direct the edge
						if(a.getTail().getId() == e.getEndpoints().getFirst().getId()) //direct it forward
							edgeContainers.get(e.getId()).directForward();
						else //direct it backwards
							edgeContainers.get(e.getId()).directBackward();
						eContainer.addCopy();//c
					}
				}
				else //artificial arc corresponding to an edge
				{
					if(flowanswer[i] == 0)
						continue;
					temp = inputVertices.get(a.getTail().getId());
					if(temp.equals(e.getEndpoints().getFirst())) // arc is 'forward'
					{
						//update undirTraversals
						if(undirTraversals[e.getId()] == 0)
							undirTraversals[e.getId()] = 1;
						else // was already -1, so we have traversal in both directions, so add to U, we don't know
						{
							U.add(new MixedEdge("copy from symmetry", new Pair<MixedVertex>(e.getEndpoints().getFirst(), e.getEndpoints().getSecond()), e.getCost(), false));
							undirTraversals[e.getId()] = 2; //so we don't add it to M again later
							//a should have been set by the initialization of edgeContainers
						}
					}
					else // arc is backward
					{
						//update undirTraversals
						if(undirTraversals[e.getId()] == 0)
							undirTraversals[e.getId()] = -1;
						else // was already 1, so we have traversal in both directions, so add to U, we don't know
						{
							U.add(new MixedEdge("copy from symmetry", new Pair<MixedVertex>(e.getEndpoints().getFirst(), e.getEndpoints().getSecond()), e.getCost(), false));
							undirTraversals[e.getId()] = 2;
							//a should have been set by the initialization of edgeContainers
						}
					}
				}
			}

			//now just go through, and any undirTraversal entries of 1 should be added forward, -1 should be added backward
			for (int i=1; i < undirTraversals.length; i++)
			{
				e = inputEdges.get(i);
				eContainer = edgeContainers.get(e.getId());
				if(undirTraversals[i] == 0)
				{
					U.add(new MixedEdge("copy from symmetry", new Pair<MixedVertex>(e.getEndpoints().getFirst(), e.getEndpoints().getSecond()), e.getCost(), false));
					//a should have been set by the initialization of edgeContainuers
				}
				else if(undirTraversals[i] == 1) //add a forward copy
				{
					M.add(new MixedEdge("copy from symmetry", new Pair<MixedVertex>(e.getEndpoints().getFirst(), e.getEndpoints().getSecond()), e.getCost(), true));
					inMdubPrime.add(false);
					//direct it forward
					if(!eContainer.isDirectedForward())
						eContainer.directForward();
				}
				else if(undirTraversals[i] == -1) //add a backwards copy
				{
					M.add(new MixedEdge("copy from symmetry", new Pair<MixedVertex>(e.getEndpoints().getSecond(), e.getEndpoints().getFirst()), e.getCost(), true));
					inMdubPrime.add(false);
					if(!eContainer.isDirectedBackward())
						eContainer.directBackward();
				}
			}
		} catch(Exception e)
		{
			e.printStackTrace();
			return;
		}
	}
	/**
	 * Essentially solves the UCPP on the Mixed Graph, ignoring arc direction,
	 * as in Mixed 1 of Frederickson.
	 * @param input - a mixed graph, which is augmented with the solution to the matching.
	 */
	private static void evenDegree(MixedGraph input, ArrayList<MixedEdge> Em, ArrayList<MixedEdge> Am)
	{
		try {
			//set up the undirected graph, and then solve the min cost matching
			UndirectedGraph setup = new UndirectedGraph();
			for(int i = 1; i < input.getVertices().size()+1 ; i++)
			{
				setup.addVertex(new UndirectedVertex("even setup graph"), i);
			}
			HashMap<Integer, UndirectedVertex> indexedVertices = setup.getInternalVertexMap();
			for(MixedEdge e:input.getEdges())
			{
				setup.addEdge(new Edge("even setup graph", new Pair<UndirectedVertex>(indexedVertices.get(e.getEndpoints().getFirst().getId()), indexedVertices.get(e.getEndpoints().getSecond().getId())), e.getCost()), e.getId());
			}

			//solve shortest paths
			int n = setup.getVertices().size();
			int[][] dist = new int[n+1][n+1];
			int[][] path = new int[n+1][n+1];
			int[][] edgePath = new int[n+1][n+1];
			CommonAlgorithms.fwLeastCostPaths(setup, dist, path, edgePath);

			//setup the complete graph composed entirely of the unbalanced vertices
			UndirectedGraph matchingGraph = new UndirectedGraph();

			//setup our graph of unbalanced vertices
			for (UndirectedVertex v: setup.getVertices())
			{
				if(v.getDegree() % 2 == 1)
				{
					matchingGraph.addVertex(new UndirectedVertex("oddVertex"), v.getId());
				}
			}

			//connect with least cost edges
			Collection<UndirectedVertex> oddVertices = matchingGraph.getVertices();
			for (UndirectedVertex v: oddVertices)
			{
				for (UndirectedVertex v2: oddVertices)
				{
					//only add one edge per pair of vertices
					if(v.getId() <= v2.getId())
						continue;
					matchingGraph.addEdge(new Edge("matchingEdge",new Pair<UndirectedVertex>(v,v2), dist[v.getMatchId()][v2.getMatchId()]));
				}
			}

			Set<Pair<UndirectedVertex>> matchingSolution = CommonAlgorithms.minCostMatching(matchingGraph);


			//now add copies in the mixed graph
			MixedEdge e;
			MixedEdge temp;
			HashMap<Integer, Edge> setupEdges = setup.getInternalEdgeMap();
			for(Pair<UndirectedVertex> p : matchingSolution)
			{
				//add the 'undirected' shortest path
				int curr = p.getFirst().getMatchId();
				int end = p.getSecond().getMatchId();
				int next = 0;
				int nextEdge = 0;
				do {
					next = path[curr][end];
					nextEdge = edgePath[curr][end];
					e = input.getInternalEdgeMap().get(setupEdges.get(nextEdge).getMatchId());
					temp = new MixedEdge("added in phase I",  new Pair<MixedVertex>(e.getEndpoints().getFirst(), e.getEndpoints().getSecond()), e.getCost(), e.isDirected());
					input.addEdge(temp);
					if(temp.isDirected())
						Am.add(temp);
					else
						Em.add(temp);
				} while ( (curr =next) != end);
			}

		} catch(Exception e)
		{
			e.printStackTrace();
			return;
		}
	}
	@Override
	public Type getProblemType() {
		return Problem.Type.MIXED_CHINESE_POSTMAN;
	}
}