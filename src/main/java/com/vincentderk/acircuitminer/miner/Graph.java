package com.vincentderk.acircuitminer.miner;

import java.util.Arrays;

/**
 * Represents a directed graph using an adjacency list representation for both
 * the {@link #inc incoming} and {@link #out outgoing} connections.
 * <br>Usage: use constructor, {@link #addEdge(int, int)} and
 * {@link #addVertex(int, short)} to build the graph. Call
 * {@link #finishBuild()} afterwards.
 *
 * @author Vincent Derkinderen
 * @version 1.0
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
     * children ({@link inc}) that are not input nodes (literals).
     * Combined with {@link unexpandable_children} this is equal to {@link inc}.
     */
    public int[][] expandable_children;
    
    /**
     * Data structure used in state expansion. unexpandable_children are all
     * children ({@link inc}) that are input nodes (literals).
     * Combined with {@link expandable_children} this is equal to {@link inc}.
     */
    public int[][] unexpandable_children;

    //Labels are used in the canonical code.
    //The label in the code should be greater than (node1 << 32 | node2).
    //nodes depend on k and average inc, it will usually be maximum 10 * k.
    /**
     * Holds the labels for AC nodes. * = PRODUCT, + = SUM, input = INPUT.
     */
    public short[] label;

    //The labels are used to compare in a canonical code.
    //The label codes (Long.MAX_VALUE - LABEL) should be greater than any (node1 << 32 | node2)
    //Operations are encoded in the code as Long.MAX_VALUE - LABEL.
    public final static short HIGHEST_OP = 3;
    public final static short MARKER = 3; //Can be used to mark a node for some special reason.
    public final static short PRODUCT = 2;
    public final static short SUM = 1;
    public final static short INPUT = 0;

    /**
     * Create a Graph which will consist of {@code vertexNb} vertices.
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
     * <>When a vertex has null as {@link #inc}, it gets set to
     * {@code new int[0]}.
     */
    public void finishBuild() {
        for (int i = 0; i < inc.length; i++) {
            if (inc[i] == null) {
                inc[i] = new int[0];
            }
        }
    }

    /**
     * Get the amount of edges in this graph.
     * <>This iterates over the incoming structure and sums up the amount of
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
}
