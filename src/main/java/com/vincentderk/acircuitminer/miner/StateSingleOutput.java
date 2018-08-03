package com.vincentderk.acircuitminer.miner;

import com.vincentderk.acircuitminer.miner.canonical.CodeOccResult;
import com.vincentderk.acircuitminer.miner.canonical.EdgeCanonical;
import java.util.Arrays;

/**
 * A data structure to keep track of an occurrence and its information in regard
 * to further expansion.
 *
 * @author Vincent Derkinderen
 * @version 2.0
 */
public class StateSingleOutput extends StateExpandable implements Cloneable {

    /**
     * The tracked internal node that functions as another external output yet
     * to be covered. -1 if there is no such node present.
     */
    public int interNode;

    /**
     * The root node of this State. (Graph numbering)
     */
    public final int root;

    /**
     * Create a state with the given arguments.
     *
     * @param root The root node of this State.
     * @param vertices The vertices of this state.
     * @param expandable The expandable array of this state.
     * @param unexpandable the unexpandable array of this state.
     * @param interNode The interNode of this state. -1 if there is none.
     */
    public StateSingleOutput(int root, int[] vertices, int[] expandable, int[] unexpandable, int interNode) {
        super(vertices, expandable, unexpandable);
        this.root = root;
        this.interNode = interNode;
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
    public StateSingleOutput expand(Graph g, int expandIndex) {
        //Note: The node to expand with musn't be part of unexpandable, vertices and can also
        //not be in expandable on an index lower than expandIndex.
        int expandNode = expandable[expandIndex];
        int vertexInsertIndex = Arrays.binarySearch(vertices, expandNode);
        int[] n_vertices;
        int[] n_expandable;
        int[] n_unexpandable;
        int n_interNode;

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

        //Fix InterNode
        n_interNode = interNode;
        if (interNode == expandNode) {
            n_interNode = -1;

            //First check expandNode, more likely there
            for (int user : g.out[expandNode]) {
                if (Arrays.binarySearch(n_vertices, user) < 0) {
                    n_interNode = user;
                    break;
                }
            }
            if (n_interNode == -1) { //still -1, check all other nodes then
                for (int vertex : n_vertices) {
                    if (vertex != root && vertex != expandNode) {
                        for (int user : g.out[vertex]) {
                            if (Arrays.binarySearch(n_vertices, user) < 0) {
                                n_interNode = user;
                                break;
                            }
                        }
                    }
                }
            }
            //Find new interNode or == -1
        } else if (interNode == -1 && g.out[expandNode].length > 1) {
            for (int user : g.out[expandNode]) {
                if (Arrays.binarySearch(n_vertices, user) < 0) {
                    n_interNode = user;
                    break;
                }
            }
        }

        return new StateSingleOutput(root, n_vertices, n_expandable, n_unexpandable, n_interNode);
    }

    /**
     * Get the CodeOccResult associated with this State. The CodeOccResult
     * contains both the code of this state as well as the vertices of this
     * state ordered according to the assignment in the code.
     *
     * <p>
     * Restriction on this state in g: the incoming edges to each node may only contain
     * distinct values. No node may be directly connected to the same node
     * twice.
     *
     * @param g The graph where this state is an occurrence in.
     * @return The canonical labeling result of this occurrence as determined by
     * {@link EdgeCanonical#minCanonicalPermutation(Graph, StateSingleOutput)}
     * @see EdgeCanonical
     */
    @Override
    public CodeOccResult getCodeOcc(Graph g) {
        return EdgeCanonical.minCanonicalPermutation(g, this);
    }

    /**
     * Get the canonical labeling of this occurrence.
     *
     * @param g The graph where this state is an occurrence in.
     * @return The canonical labeling of this occurrence as determined by
     * {@link EdgeCanonical#minCanonicalPermutation(Graph, StateSingleOutput)}
     * @see EdgeCanonical
     */
    @Override
    public long[] getCode(Graph g) {
        return EdgeCanonical.minCanonicalPermutation(g, this).code;
    }

}
