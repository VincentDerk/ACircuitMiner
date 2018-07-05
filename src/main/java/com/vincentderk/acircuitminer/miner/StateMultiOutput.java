package com.vincentderk.acircuitminer.miner;

import com.vincentderk.acircuitminer.miner.canonical.CodeOccResult;
import com.vincentderk.acircuitminer.miner.canonical.EdgeCanonicalMultiOutput;
import java.util.Arrays;

/**
 * A data structure to keep track of an occurrence and its information in regard
 * to further expansion.
 *
 * @author Vincent Derkinderen
 * @version 2.0
 */
public class StateMultiOutput extends State implements Cloneable {

    /**
     * Represents the vertices that provide an external output. These values are
     * sorted.
     */
    public int[] outputNodes;

    /**
     * Create a state with the given arguments.
     *
     * @param vertices The vertices of this state.
     * @param expandable The expandable array of this state.
     * @param unexpandable the unexpandable array of this state.
     * @param outputNodes The nodes in vertices that output.
     */
    public StateMultiOutput(int[] vertices, int[] expandable, int[] unexpandable, int[] outputNodes) {
        super(vertices, expandable, unexpandable);
        this.outputNodes = outputNodes;
    }

    /**
     * The state that results after expanding this state with the node given by
     * {@code expandable[expandIndex]}.
     *
     * @param g The graph structure
     * @param expandIndex The node that will be used for expanding this state is
     * the node in this state's expandable array on index expandIndex. Thus,
     * expandIndex must be between the boundaries of the expandable array.
     *
     * @return The state resulting from expanding this state with the given
     * choice.
     */
    @Override
    public StateMultiOutput expand(Graph g, int expandIndex) {
        //Note: The node to expand with musn't be part of unexpandable, vertices and can also
        //not be in expandable on an index lower than expandIndex.
        int expandNode = expandable[expandIndex];
        int vertexInsertIndex = Arrays.binarySearch(vertices, expandNode);
        int[] n_vertices;
        int[] n_expandable;
        int[] n_unexpandable;

        //Inserted in the sorted vertices
        vertexInsertIndex = Math.abs(++vertexInsertIndex);
        n_vertices = new int[vertices.length + 1];
        System.arraycopy(vertices, 0, n_vertices, 0, vertexInsertIndex);
        n_vertices[vertexInsertIndex] = expandNode;
        System.arraycopy(vertices, vertexInsertIndex, n_vertices, vertexInsertIndex + 1, vertices.length - vertexInsertIndex);

        //Fix unexpandable (unexpandable + expandable upto expandIndex)
        // unexpandable (sorted), expandable (sorted)
        //Distinct, sorted
        n_unexpandable = createN_unexpandable(unexpandable, expandable, expandIndex);

        //Fix expandable (expandable children + expandable from expandIndex+1 + excl. n_unexpandable en n_vertices.)
        //Distinct, sorted, filtered.
        n_expandable = createN_expandable(expandable, expandIndex, g.expandable_children[expandNode], n_unexpandable, n_vertices);

        //fix outputNodes
        int[] n_outputNodes = createN_outputNodes(expandNode, g, outputNodes, vertices);

        return new StateMultiOutput(n_vertices, n_expandable, n_unexpandable, n_outputNodes);
    }

    /**
     * Create the new n_outputNodes array formed by the given arguments.
     * <p>
     * The result is the same as the given outputNodes except that:
     * <lu>
     * <li>If any parent of expandNode (in g) is not in vertices, add expandNode to outputNodes (sorted).</li>
     * <li>For each child of expandNode (in g) that is present in outputNodes: remove from outputNodes if all its parents are in vertices. </li>
     * </lu>
     * @param expandNode The newly added node of which to check the parents and children
     * @param g The graph structure in which this occurs and of which this is a state.
     * @param outputNodes The nodes that have an external output in this state. n_outputNodes is based on outputNodes.
     * @param vertices The vertices of this state. 
     * @return the new outputNodes that results from the given arguments. The result is outputNodes (same reference) unless
     * one of the two operations is applied:
     * <lu>
     * <li>If any parent of expandNode (in g) is not in vertices, add expandNode to outputNodes (sorted).</li>
     * <li>For each child of expandNode (in g) that is present in outputNodes: remove from outputNodes if all its parents are in vertices. </li>
     * </lu>
     */
    private int[] createN_outputNodes(int expandNode, Graph g, int[] outputNodes, int[] vertices) {
        int[] n_outputNodes = outputNodes;

        //if node has a parent not present in vertices, add as output
        int outputIndex = Arrays.binarySearch(outputNodes, expandNode);
        for (int parent : g.out[expandNode]) {
            if (Arrays.binarySearch(vertices, parent) < 0) {
                //add output to outputs als hij er ng niet is
                n_outputNodes = new int[outputNodes.length + 1];
                System.arraycopy(outputNodes, 0, n_outputNodes, 0, outputIndex);
                n_outputNodes[outputIndex] = expandNode;
                System.arraycopy(outputNodes, outputIndex, n_outputNodes, outputIndex + 1, outputNodes.length - outputIndex - 1);
                break;
            }
        }

        //If child of new node was output. Maybe not anymore.
        for (int child : g.inc[expandNode]) {
            int childIndex = Arrays.binarySearch(outputNodes, child);
            if (childIndex >= 0 && g.out[child].length > 1) {
                if (allPresent(g.out[child])) {
                    //all parents of child present, remove child from outputs
                    int[] n_outputNodes_t = Arrays.copyOf(n_outputNodes, n_outputNodes.length-1);
                    System.arraycopy(n_outputNodes, childIndex+1, n_outputNodes_t, childIndex, n_outputNodes.length-childIndex-1);
                    n_outputNodes = n_outputNodes_t;
                }
            }
        }
        
        return n_outputNodes;
    }

    /**
     * Whether all the given nodes are present in vertices.
     *
     * @param nodes The nodes to check. Must not be null.
     * @return False when there is a node in nodes that is not present in
     * vertices. True otherwise.
     */
    private boolean allPresent(int[] nodes) {
        for (int node : nodes) {
            if (Arrays.binarySearch(vertices, node) < 0) {
                return false;
            }
        }

        return true;
    }

    /**
     * Get the CodeOccResult associated with this State. The CodeOccResult
     * contains both the code of this state as well as the vertices of this
     * state ordered according to the assignment in the code.
     *
     * @param g The graph where this state is an occurrence in.
     * @return The canonical labeling result of this occurrence as determined by
     * {@link com.vincentderk.acircuitminer.miner.canonical.EdgeCanonicalMultiOutput.minCanonicalPermutation(Graph, State)}
     * @see EdgeCanonicalMultiOutput
     */
    @Override
    public CodeOccResult getCodeOcc(Graph g) {
        /*
        long[] code = EdgeCanonical.minCanonicalPermutation(g, this);
        if(EdgeCanonical.printCode(code).contains("59")) { //DEBUG - PRINTING A SELECT code
            System.out.println("Found code " + EdgeCanonical.printCode(code) + " for " + vertices.length + "," + expandable.length + "," + unexpandable.length);
            System.out.println("root: " + root);
            System.out.println("vertices: " + Arrays.toString(vertices));
            System.out.println("expandable: " + Arrays.toString(expandable));
            System.out.println("unexpandable: " + Arrays.toString(unexpandable));
        }*/
        return EdgeCanonicalMultiOutput.minCanonicalPermutation(g, this);
    }

    /**
     * Get the canonical labeling of this occurrence.
     *
     * @param g The graph where this state is an occurrence in.
     * @return The canonical labeling of this occurrence as determined by
     * {@link com.vincentderk.acircuitminer.miner.canonical.EdgeCanonicalMultiOutput.minCanonicalPermutation(Graph, State)}
     * @see EdgeCanonicalMultiOutput
     */
    @Override
    public long[] getCode(Graph g) {
        return EdgeCanonicalMultiOutput.minCanonicalPermutation(g, this).code;
    }

}
