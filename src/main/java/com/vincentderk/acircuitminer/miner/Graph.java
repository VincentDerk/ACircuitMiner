package com.vincentderk.acircuitminer.miner;

import com.vincentderk.acircuitminer.miner.util.Utils;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.util.Arrays;

/**
 * Represents a directed graph using an adjacency list representation for both
 * the {@link #inc incoming} and {@link #out outgoing} connections.
 * <br>Usage: use constructor, {@link #addEdge(int, int)} and
 * {@link #addVertex(int, short)} to build the graph. Call
 * {@link #finishBuild()} afterwards.
 *
 * @author Vincent Derkinderen
 * @version 2.0
 */
public class Graph {

    //TODO: Investigate if we can keep the inc/out non-ordered. This allows a port-order for non associative operations.
    /**
     * The incoming structure of the graph.
     * <> Each node x, has incoming edges of vertices: inc[x]
     * <br> The resulting arrays are sorted. This means that only
     * non-commutative operations are supported as there is no notion of ports.
     */
    public int[][] inc;

    /**
     * The outgoing structure of the graph.
     * <> Each node x, has outgoing edges to vertices: out[x].
     * <br> The resulting arrays are sorted.
     */
    public int[][] out;

    /**
     * Data structure used in state expansion. expandable_children are all
     * children ({@link inc}) that are not input nodes (literals). Combined with
     * {@link unexpandable_children} this is equal to {@link inc}.
     */
    public int[][] expandable_children;

    /**
     * Data structure used in state expansion. unexpandable_children are all
     * children ({@link inc}) that are input nodes (literals). Combined with
     * {@link expandable_children} this is equal to {@link inc}.
     */
    public int[][] unexpandable_children;

    //Labels are used in the canonical code.
    //The label in the code should be greater than (node1 << 32 | node2).
    //nodes depend on k, we could take a maximum of 10 * k.
    /**
     * Holds the labels for AC nodes. * = PRODUCT, + = SUM, input = INPUT.
     */
    public short[] label;

    //The labels are used to compare in a canonical code.
    //The label codes (Long.MAX_VALUE - LABEL) should be greater than any (node1 << 32 | node2)
    //Operations are encoded in the code as Long.MAX_VALUE - LABEL.
    public final static short HIGHEST_OP = 5;
    public final static short PRODUCT_OUTPUT = 5; // OUTPUT denotes that the operation has an additional external output
    public final static short SUM_OUTPUT = 4; // OUTPUT denotes that the operation has an additional external output
    public final static short MARKER = 3; //Can be used to mark a node for some special reason.
    public final static short PRODUCT = 2;
    public final static short SUM = 1;
    public final static short INPUT = 0;

    /**
     * Create a Graph which will consist of {@code vertexNb} vertices.
     *
     * @param vertexNb The amount of vertices this must have.
     */
    public Graph(int vertexNb) {
        this.inc = new int[vertexNb][0];
        this.out = new int[vertexNb][0];
        this.label = new short[vertexNb];
        this.expandable_children = new int[vertexNb][0];
        this.unexpandable_children = new int[vertexNb][0];
    }

    /**
     * Add the vertex to the graph structure.
     *
     * @param vertex The ID of the vertex
     * @param label The label of the vertex.
     * @see #PRODUCT
     * @see #SUM
     * @see #INPUT
     */
    public void addVertex(int vertex, short label) {
        this.label[vertex] = label;
        if (label == Graph.INPUT) {
            this.inc[vertex] = null; //Used as indication of non_expandable
        }
    }

    /**
     * Add the edge going from x to y (x is input of y).
     * <p>
     * Both nodes must be added as vertices first.
     *
     * @param x The starting node.
     * @param y The ending node.
     */
    public void addEdge(int x, int y) {
        //Add x side
        int[] current = out[x];
        if (current.length == 0) {
            out[x] = new int[]{y};
        } else {
            int index = Arrays.binarySearch(current, y);

            if (index < 0) {
                index = Math.abs(index + 1);
                int[] newArr = new int[current.length + 1];
                System.arraycopy(current, 0, newArr, 0, index);
                System.arraycopy(current, index, newArr, index + 1, current.length - index);
                newArr[index] = y;
                out[x] = newArr;
            }
        }

        //Add y side
        current = inc[y];
        if (current.length == 0) {
            inc[y] = new int[]{x};
        } else {
            int index = Arrays.binarySearch(current, x);

            if (index < 0) {
                index = Math.abs(index + 1);
                int[] newArr = new int[current.length + 1];
                System.arraycopy(current, 0, newArr, 0, index);
                System.arraycopy(current, index, newArr, index + 1, current.length - index);
                newArr[index] = x;
                inc[y] = newArr;
            }
        }

        //Fix expandable_children/unexpandable_children
        if (inc[x] != null) { //expandable_children
            current = expandable_children[y];
            if (current.length == 0) {
                expandable_children[y] = new int[]{x};
            } else {
                int index = Arrays.binarySearch(current, x);

                if (index < 0) {
                    index = Math.abs(index + 1);
                    int[] newArr = new int[current.length + 1];
                    System.arraycopy(current, 0, newArr, 0, index);
                    System.arraycopy(current, index, newArr, index + 1, current.length - index);
                    newArr[index] = x;
                    expandable_children[y] = newArr;
                }
            }
        } else { //Unexpandable_children
            current = unexpandable_children[y];
            if (current.length == 0) {
                unexpandable_children[y] = new int[]{x};
            } else {
                int index = Arrays.binarySearch(current, x);

                if (index < 0) {
                    index = Math.abs(index + 1);
                    int[] newArr = new int[current.length + 1];
                    System.arraycopy(current, 0, newArr, 0, index);
                    System.arraycopy(current, index, newArr, index + 1, current.length - index);
                    newArr[index] = x;
                    unexpandable_children[y] = newArr;
                }
            }
        }
    }

    /**
     * Finish the building process of this {@link Graph}.
     * <p>
     * When a vertex has null as {@link #inc}, it gets set to
     * {@code new int[0]}.
     */
    public void finishBuild() {
        for (int i = 0; i < inc.length; i++) {
            if (inc[i] == null) {
                inc[i] = new int[0];
            }
        }
    }

    @Override
    public Graph clone() {
        Graph newGraph = new Graph(0);
        newGraph.inc = deepClone(this.inc); // new int[vertexNb][0];
        newGraph.out = deepClone(this.out); // new int[vertexNb][0];
        newGraph.label = this.label.clone();
        newGraph.expandable_children = deepClone(this.expandable_children); // new int[vertexNb][0];
        newGraph.unexpandable_children = deepClone(this.unexpandable_children); // new int[vertexNb][0];

        return newGraph;
    }

    private int[][] deepClone(int[][] arr) {
        if (arr == null) {
            return null;
        }

        int[][] r = new int[arr.length][];
        for (int i = 0; i < arr.length; i++) {
            r[i] = (arr[i] != null) ? arr[i].clone() : null;
        }

        return r;
    }

    /**
     * --------------- Additional Graph Creation ------------------
     */
    /**
     * Convert a code to a {@link Graph}.
     * <p>
     * This method currently uses {@link MOSR#getNodeCount(long[])} and
     * therefore only supports a {@link SOSR} or {@link MOSR} type of {@code code}.
     *
     * @param code The code to convert into a graph.
     * @return The graph associated with the code
     */
    public static Graph codeToGraph(long[] code) {
        int nodeCount = MOSR.getNodeCount(code);
        Graph g = new Graph(nodeCount);

        addVertices(g, code);
        addEdges(g, code);
        g.finishBuild();

        return g;
    }

    /**
     * Add all the vertices in {@code code}, with their respective label, to
     * graph {@code g}.
     * <p>
     * The required {@code code} format is a sequence of chunks where a chunk
     * is: {@code (a,b)(a,c)(a,d)...A} with A the label (operation) of a.
     *
     * @param g The graph to add the vertices to.
     * @param code The code to get the vertices of {@code g} from.
     */
    private static void addVertices(Graph g, long[] code) {
        //Internal:
        int index = 0;
        IntSet nodes = new IntOpenHashSet();
        while (index < code.length) {
            //Get left element of group
            int left = (int) (code[index] >> 32);
            nodes.add(left);
            //Skip to the label
            while (code[index] < Long.MAX_VALUE - Graph.HIGHEST_OP) {
                index++;
            }

            //Get label
            short label = (short) (Long.MAX_VALUE - code[index]);
            g.addVertex(left, label);
            index++;
        }

        //External:
        for (long l : code) {
            if (l < Long.MAX_VALUE - Graph.HIGHEST_OP) {
                long mask = ((long) 1 << 32) - 1; //32 1 bits: 0..0111..111
                int right = (int) (mask & l);

                if (!nodes.contains(right)) {
                    nodes.add(right);
                    g.addVertex(right, Graph.INPUT);
                }
            }
        }
    }

    /**
     * Add the edges in the code to the given graph
     * <p>
     * All vertices must be added before this is called.
     *
     * @param g The graph to add edges to
     * @param code The code to get the edges of
     * @see #addVertices(com.vincentderk.acircuitminer.miner.Graph, long[])
     */
    private static void addEdges(Graph g, long[] code) {
        for (long l : code) {
            if (l < Long.MAX_VALUE - Graph.HIGHEST_OP) {
                int left = (int) (l >> 32);
                long mask = ((long) 1 << 32) - 1; //32 1 bits: 0..0111..111
                int right = (int) (mask & l);

                g.addEdge(right, left);
            }
        }
    }

    /**
     * --------------- Some Get methods ------------------
     */
    /**
     * Get the amount of edges in this graph.
     * <p>
     * This iterates over the incoming structure and sums up the amount of
     * incoming for each node.
     *
     * @return The sum of all incoming edges for each node.
     */
    public int getEdgeCount() {
        int count = 0;
        for (int[] incs : this.inc) {
            count = (incs != null) ? (count + incs.length) : count;
        }
        return count;
    }

    /**
     * Count the amount of operation nodes (so only internal nodes) present in
     * the given graph.
     *
     * @return The amount of labels in g that are not equal to
     * {@link Graph#INPUT} or {@link Graph#MARKER}.
     */
    public int getOperationNodeCount() {
        int opCount = 0;

        for (short s : label) {
            if (s != Graph.INPUT && s != Graph.MARKER) {
                opCount++;
            }
        }
        return opCount;
    }

    /**
     * The first node (0...) that has no output and a label different from
     * {@link #MARKER}.
     *
     * @return The index of the first node (smallest index) in this graph that
     * has no output and has a label not equal to {@link #MARKER}. -1 if there
     * is no such node.
     */
    public int getFirstNonOutputNode() {
        for (int i = 0; i < out.length; i++) {
            if (out[i] != null && out[i].length == 0 && label[i] != MARKER) {
                return i;
            }
        }

        return -1;
    }

    /**
     * Get the input nodes (indices) in order of appearance in the graph.
     * <br>This iterates over all labels and returns the indices which have an
     * input label, in that order.
     *
     * @return The nodes (indices) that are input nodes in the given graph. The
     * order is from small to high index.
     */
    public int[] getInputNodes() {
        IntArrayList list = new IntArrayList();

        for (int i = 0; i < label.length; i++) {
            if (label[i] == Graph.INPUT) {
                list.add(i);
            }
        }

        return list.toIntArray();
    }

    /**
     * Get a map of nodeId's (in this graph) to their appropriate input index.
     * For example if node 8 in the graph is the 3rd input (order given by
     * {@link #getInputNodes()}) then the returned map maps 8 to 3.
     *
     * @return A mapping of codeId (id of nodes in this graph) to the
     * appropriate input index of that codeId.
     * @see #getInputNodes()
     */
    public Int2IntMap getInputNodesMap() {
        int[] orderedInput = getInputNodes();
        Int2IntMap map = new Int2IntOpenHashMap();

        for (int i = 0; i < orderedInput.length; i++) {
            map.put(orderedInput[i], i);
        }

        return map;
    }

    /**
     * Get the costs associated with evaluating this {@link Graph}.
     * <br>This can only handle * ({@link #PRODUCT},{@link #PRODUCT_OUTPUT}) and
     * + ({@link #SUM},{@link #SUM_OUTPUT}).
     *
     * @return The costs of this graph in the form of {@code long[] {instructionCost, ioCost,
     * *-cost, +-cost}}.
     */
    public long[] getCosts() {
        long[] costs = new long[4];
        final int INSTR_INDEX = 0;
        final int IO_INDEX = 1;
        final int SUM_INDEX = 2;
        final int MULT_INDEX = 3;


        /* INSTR,*,+,output,input Cost */
        for (short l : label) {
            switch (l) {
                case SUM_OUTPUT:
                case SUM:
                    costs[SUM_INDEX]++;
                    costs[IO_INDEX]++; //output
                    break;
                case PRODUCT_OUTPUT:
                case PRODUCT:
                    costs[MULT_INDEX]++;
                    costs[IO_INDEX]++; //output
                    break;
                default:
                    break;
            }
        }
        costs[INSTR_INDEX] = costs[MULT_INDEX] + costs[SUM_INDEX]; //#*, #+

        //input        
        int activeInputs = 0;
        for (int[] incPorts : inc) {
            activeInputs += incPorts.length;
        }
        costs[IO_INDEX] += activeInputs;

        /* Multiply weight */
        costs[INSTR_INDEX] = costs[INSTR_INDEX] * Utils.INSTRUCTION_COST_BASE + activeInputs * Utils.INSTRUCTION_COST_EXTRA;
        costs[SUM_INDEX] *= Utils.SUM_COST;
        costs[MULT_INDEX] *= Utils.MULTIPLICATION_COST;
        costs[IO_INDEX] *= Utils.IO_COST;

        return costs;
    }

    /**
     * Get the total cost associated with evaluating this {@link Graph}.
     * <br>This can only handle * ({@link #PRODUCT},{@link #PRODUCT_OUTPUT}) and
     * + ({@link #SUM},{@link #SUM_OUTPUT}).
     *
     * @return The costs of evaluating this graph. It accounts for the
     * instruction cost, the cost of performing the + and * operations and the
     * IO cost.
     * @see #getCosts
     */
    public long getTotalCosts() {
        long[] costs = getCosts();
        long instrCost = costs[0];
        long ioCost = costs[1];
        long sumCost = costs[2];
        long prodCost = costs[3];
        long totalCost = instrCost + ioCost + sumCost + prodCost;

        return totalCost;
    }

}
