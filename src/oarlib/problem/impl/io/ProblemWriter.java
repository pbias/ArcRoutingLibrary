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
package oarlib.problem.impl.io;

import gnu.trove.TIntObjectHashMap;
import oarlib.core.Graph;
import oarlib.core.Link;
import oarlib.core.Problem;
import oarlib.core.Vertex;
import oarlib.exceptions.UnsupportedFormatException;
import oarlib.graph.impl.UndirectedGraph;
import oarlib.link.impl.Edge;
import oarlib.link.impl.WindyEdge;
import oarlib.problem.impl.ProblemAttributes;
import oarlib.vertex.impl.UndirectedVertex;
import org.apache.log4j.Logger;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;

/**
 * Writer to output various file formats.  Plans to use Gephi for visualization.
 *
 * @author Oliver
 */
public class ProblemWriter {

    private static final Logger LOGGER = Logger.getLogger(ProblemWriter.class);

    private ProblemFormat.Name mFormat;

    public ProblemWriter(ProblemFormat.Name format) {
        mFormat = format;
    }

    public ProblemFormat.Name getFormat() {
        return mFormat;
    }

    public void setFormat(ProblemFormat.Name newFormat) {
        mFormat = newFormat;
    }

    public boolean writeInstance(Problem p, String filename) throws UnsupportedFormatException {
        //TODO
        switch (mFormat) {
            case OARLib:
                return writeOarlibInstance(p, filename);
            case Campos:
                break;
            case Corberan:
                return writeCorberanInstance(p, filename);
            case Simple:
                break;
            case Yaoyuenyong:
                break;
            case METIS:
                return writeMETISInstance(p, filename);
            default:
                break;
        }
        LOGGER.error("While the format seems to have been added to the Format.Name type list,"
                + " there doesn't seem to be an appropriate write method assigned to it.  Support is planned in the future," +
                "but not currently available");
        throw new UnsupportedFormatException();
    }

    private <V extends Vertex, E extends Link<V>, G extends Graph<V, E>> boolean writeCorberanInstance(Problem<V, E, G> p, String filename) {
        try {
            G g = p.getGraph();
            if (g.getType() != Graph.Type.WINDY)
                throw new IllegalArgumentException("Currently, this type of not supported for this output format.");


            int n = g.getVertices().size();
            int mReq = 0;
            for (E edge : g.getEdges()) {
                if (edge.isRequired())
                    mReq++;
            }
            int mNoReq = g.getEdges().size() - mReq;

            //front matter
            PrintWriter pw = new PrintWriter(filename, "UTF-8");
            pw.println("NOMBRE : " + p.getName());
            pw.println("COMENTARIO : " + g.getDepotId() + " depot");
            pw.println("VERTICES : " + g.getVertices().size());
            pw.println("ARISTAS_REQ : " + mReq);
            pw.println("ARISTAS_NOREQ : " + mNoReq);

            pw.println("LISTA_ARISTAS_REQ :");

            String line;
            for (E edge : g.getEdges()) {
                if (edge.isRequired()) {
                    line = "(" + edge.getFirstEndpointId() + "," + edge.getSecondEndpointId() + ")";
                    line += " coste " + edge.getCost() + " " + ((WindyEdge) edge).getReverseCost();
                    pw.println(line);
                }
            }

            pw.println("LISTA_ARISTAS_NOREQ :");
            for (E edge : g.getEdges()) {
                if (!edge.isRequired()) {
                    line = "(" + edge.getFirstEndpointId() + "," + edge.getSecondEndpointId() + ")";
                    line += " coste " + edge.getCost() + " " + ((WindyEdge) edge).getReverseCost();
                    pw.println(line);
                }
            }

            pw.println("COORDENADAS :");
            for (int i = 1; i <= n; i++) {
                V v = g.getVertex(i);
                line = i + " " + v.getX() + " " + v.getY();
                pw.println(line);
            }


            pw.close();
            return true;
        } catch (Exception ex) {
            ex.printStackTrace();
            return false;
        }
    }

    private <V extends Vertex, E extends Link<V>, G extends Graph<V, E>> boolean writeMETISInstance(Problem<V, E, G> p, String filename) {
        try {
            G g = p.getGraph();
            Vertex first, second;
            UndirectedGraph g2 = new UndirectedGraph(g.getVertices().size());
            Collection<V> gVertices = g.getVertices();
            TIntObjectHashMap<UndirectedVertex> g2Vertices = g2.getInternalVertexMap();

            if (g.getClass() != UndirectedGraph.class) {
                for (E l : g.getEdges()) {
                    first = g2Vertices.get(l.getEndpoints().getFirst().getId());
                    second = g2Vertices.get(l.getEndpoints().getSecond().getId());
                    if (!first.getNeighbors().containsKey(second))
                        g2.addEdge(first.getId(), second.getId(), 1);
                }
                for (V v : gVertices) {
                    g2Vertices.get(v.getId()).setCost(v.getCost());
                }
            }

            //front matter
            PrintWriter pw = new PrintWriter(filename, "UTF-8");
            pw.println("%");
            pw.println("% This is a METIS file generated by the Open Source, Arc-Routing Library (OAR Lib).");
            pw.println("% For more information on the METIS Library, or the format please visit: ");
            pw.println("% http://glaros.dtc.umn.edu/gkhome/metis/metis/overview");
            pw.println("%");

            //the header
            int n = g.getVertices().size();
            int m = g2.getEdges().size();
            String header = "";
            header = header + n + " " + m + " " + "011" + " 1";
            pw.println(header);

            TIntObjectHashMap<UndirectedVertex> indexedVertices = g2.getInternalVertexMap();
            UndirectedVertex temp;
            HashMap<UndirectedVertex, ArrayList<Edge>> tempNeighbors;
            boolean shownWarning = false;
            for (int i = 1; i <= n; i++) {
                String line = "";
                temp = indexedVertices.get(i);

                line += temp.getCost() + " ";
                tempNeighbors = temp.getNeighbors();
                for (UndirectedVertex neighbor : tempNeighbors.keySet()) {
                    line += neighbor.getId() + " ";
                    line += tempNeighbors.get(neighbor).get(0).getCost() + " ";
                    if (tempNeighbors.get(neighbor).size() > 1 && !shownWarning) {
                        System.out.println("Multigraphs are not currently supported; we shall only use one of the edges connecting these vertices.");
                        shownWarning = true;
                    }
                }
                pw.println(line);
            }

            pw.close();
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private <V extends Vertex, E extends Link<V>, G extends Graph<V, E>> boolean writeOarlibInstance(Problem<V, E, G> p, String filename) {
        try {

            G g = p.getGraph();
            //front matter
            PrintWriter pw = new PrintWriter(filename, "UTF-8");
            pw.println("%");
            pw.println("% This is a file generated by the Open Source, Arc-Routing Library (OAR Lib).");
            pw.println("% For more information on OAR Lib, or the format please visit: ");
            pw.println("% https://github.com/Olibear/ArcRoutingLibrary ");
            pw.println("%");

            ProblemAttributes pa = p.getProblemAttributes();
            boolean isWindy = pa.getmGraphType() == Graph.Type.WINDY;

            //the header
            pw.println();
            pw.println("================================");
            pw.println("Format: OAR Lib");
            pw.println("Graph Type:" + pa.getmGraphType());
            pw.println("Problem Type:" + pa.getmProblemType());
            pw.println("Fleet Size:" + pa.getmNumVehicles());
            pw.println("Number of Depots:" + pa.getmNumDepots());

            String depotIDString = "Depot ID(s):" + g.getDepotId();

            pw.println(depotIDString);
            if (pa.getmProperties() != null) {
                String addedProps = "Additional Properties:";
                for (ProblemAttributes.Properties prop : pa.getmProperties()) {
                    addedProps += prop.toString() + ",";
                }
                pw.println(addedProps);
            }
            pw.println("N:" + g.getVertices().size());
            pw.println("M:" + g.getEdges().size());
            pw.println("================================");


            //Formatting details
            pw.println();
            pw.println("LINKS");

            String lineFormat = "Line Format:V1,V2,COST";
            if (isWindy)
                lineFormat += ",REVERSE COST";
            lineFormat += ",REQUIRED";

            pw.println(lineFormat);

            //edges
            String line;
            int m = g.getEdges().size();
            for (int i = 1; i <= m; i++) {
                E e = g.getEdge(i);
                line = "";
                line += e.getEndpoints().getFirst().getId() + ","
                        + e.getEndpoints().getSecond().getId() + ","
                        + e.getCost();
                if (isWindy)
                    line += "," + ((WindyEdge) e).getReverseCost();
                line += "," + e.isRequired();
                pw.println(line);
            }

            pw.println("===========END LINKS============");

            boolean hasVertexCoords = false;
            for (V v : g.getVertices())
                if (v.hasCoordinates()) {
                    hasVertexCoords = true;
                    break;
                }

            if (hasVertexCoords) {
                pw.println();
                pw.println("VERTICES");

                lineFormat = "Line Format:x,y";
                pw.println(lineFormat);

                //vertices
                int n = g.getVertices().size();
                for (int i = 1; i <= n; i++) {
                    V v = g.getVertex(i);
                    line = v.getX() + "," + v.getY();
                    pw.println(line);
                }
                pw.println("===========END VERTICES============");
            }

            //TODO: Other properties; we have to have an automated way of fetching them

            pw.close();

        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

}