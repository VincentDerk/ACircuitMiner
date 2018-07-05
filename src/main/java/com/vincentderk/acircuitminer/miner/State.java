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
public abstract class State implements Cloneable {

    /**
     * The vertices that are part of this state. These are in a sorted order so
     * binary search can be used.
     */
    public int[] vertices;

    /**
     * The vertices that are expandable. These are the inputs of the
     * {@link vertices} that can still be expanded in this state.
     */
    public int[] expandable;

    /**
     * The vertices that are unexpandable. These are the inputs of the
     * {@link vertices} that can not be expanded in this state. Either because
     * they do not have any incomings or because of choices made. This does not
     * include literals.
     */
    public int[] unexpandable;



    /**
     * Create a state with the given arguments.
     *
     * @param vertices The vertices of this state.
     * @param expandable The expandable array of this state.
     * @param unexpandable the unexpandable array of this state.
     */
    public State(int[] vertices, int[] expandable, int[] unexpandable) {
        this.vertices = vertices;
        this.expandable = expandable;
        this.unexpandable = unexpandable;
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
    public abstract State expand(Graph g, int expandIndex);

    /**
     * Creates n_unexpandable from the given unexpandable and expandable each
     * being individually sorted and distinct.
     *
     * @param unexpandable unexpandable, sorted and distinct
     * @param expandable expandable, sorted and distinct
     * @param expandIndex the index upto which we need to insert the elements of
     * expandable into the new n_unexpandable
     * @return n_unexpandable, formed by merging all 3 inputs (unexpandable,
     * expandable upto expandIndex and unexp_children). Besides merged, the
     * result is sorted and distinct.
     */
    protected int[] createN_unexpandable(int[] unexpandable, int[] expandable, int expandIndex) {
        /*
            We want to merge all inputs (unexpandable, expandable upto expandIndex).
            Besides merging we want the result to be sorted and distinct.
            Since all inputs are sorted themselves we can do this by an iterative merging algorithm. O(n)
         */
        int[] n_unexpandable = new int[unexpandable.length + expandIndex];
        int unexpLength = unexpandable.length;

        int count = 0; //Counts total amount of elements handled.
        int prev = -1; //The previously stored value
        int index = 0; //The next index of n_unexpandable to store a value in.
        int unexpIndex = 0; //The next index of unexpandable to get an element from.
        int expIndex = 0; //The next index of expandable to get an element from.

        int el_unexp = (unexpIndex < unexpLength) ? unexpandable[unexpIndex] : Integer.MAX_VALUE;
        int el_exp = (expIndex < expandIndex) ? expandable[expIndex] : Integer.MAX_VALUE;

        while (count < n_unexpandable.length) {
            boolean skip = false;

            /* Skip same values - distinct */
            if (el_unexp == prev) {
                unexpIndex++;
                el_unexp = (unexpIndex < unexpLength) ? unexpandable[unexpIndex] : Integer.MAX_VALUE;
                count++;
                skip = true;
            }
            if (el_exp == prev) {
                expIndex++;
                el_exp = (expIndex < expandIndex) ? expandable[expIndex] : Integer.MAX_VALUE;
                count++;
                skip = true;
            }
            /* Insert minimum value */
            if (!skip) {
                count++;
                //1. Select lowest value, increase its index and update prev
                if (el_unexp <= el_exp) { //el_unexp
                    n_unexpandable[index++] = el_unexp;
                    prev = el_unexp;
                    unexpIndex++;
                    el_unexp = (unexpIndex < unexpLength) ? unexpandable[unexpIndex] : Integer.MAX_VALUE;
                } else { //el_exp
                    n_unexpandable[index++] = el_exp;
                    prev = el_exp;
                    expIndex++;
                    el_exp = (expIndex < expandIndex) ? expandable[expIndex] : Integer.MAX_VALUE;
                }
            }
        }

        /* The equivalent code with streams - slower */
 /*
        int[] n_unexpandable = Arrays.copyOf(unexpandable, unexpandable.length + expandIndex);
        System.arraycopy(expandable, 0, n_unexpandable, unexpandable.length, expandIndex);
        n_unexpandable = IntStream.of(n_unexpandable)
                .sorted()
                .distinct()
                .toArray();
         */
        return Arrays.copyOf(n_unexpandable, index);
    }

    /**
     * Creates n_expandable from the given expandable, children, n_unexpandable
     * and n_vertices. All sorted and distinct arrays.
     *
     * @param expandable expandable, sorted and distinct
     * @param expandIndex the index from which (+1) to insert the elements of
     * expandable into the new n_expandable. So all elements of expandIndex+1..
     * will be eligible for n_expandable.
     * @param children The expandable_children, sorted and distinct.
     * @param n_unexpandable n_unexpandable, sorted and distinct.
     * @param n_vertices n_vertices, sorted and distinct.
     * @return n_expandable formed by merging children and the elements of
     * expandable starting from index expandIndex+1. Besides merged, the result
     * is made distinct, sorted and elements of n_vertices and n_unexpandable
     * are filtered out.
     */
    protected int[] createN_expandable(int[] expandable, int expandIndex, int[] children, int[] n_unexpandable, int[] n_vertices) {
        /*
            We want to merge 2 inputs (children, expandable starting from expandIndex+1.
            Besides merging we want the result to be sorted and distinct and the elements
            of n_vertices and n_unexpandable have to be filtered out.
            Since both inputs are sorted themselves we can do this by an iterative merging algorithm. O(n)
         */
        int[] n_expandable = new int[children.length + expandable.length - expandIndex - 1];

        int prev = -1; //The previously stored value
        int index = 0; //The next index of n_expandable to store a value in.

        int expIndex = expandIndex + 1; //The next index of expandable to get an element from.
        int childIndex = 0; //The next index of children to get an element from.
        int el_exp = (expIndex < expandable.length) ? expandable[expIndex] : Integer.MAX_VALUE;
        int el_child = (childIndex < children.length) ? children[childIndex] : Integer.MAX_VALUE;

        /* Init filter arrays */
        int smallest = (el_exp <= el_child) ? el_exp : el_child;
        int i = Arrays.binarySearch(n_unexpandable, smallest);
        if (i < 0) {
            i++;
            i *= -1;
        }
        int unexpIndex = i; //The next index of n_unexp //Try to keep the valuer higher
        i = Arrays.binarySearch(n_vertices, smallest);
        if (i < 0) {
            i++;
            i *= -1;
        }
        int vIndex = i; //The next index of n_vertices //Try to keep the valuer higher
        int el_unexp = (unexpIndex < n_unexpandable.length) ? n_unexpandable[unexpIndex] : Integer.MAX_VALUE;
        int el_vert = (vIndex < n_vertices.length) ? n_vertices[vIndex] : Integer.MAX_VALUE;

        while (expIndex < expandable.length || childIndex < children.length) {
            boolean skip = false;
            /* Skip same values - distinct */
            if (el_exp == prev) {
                expIndex++;
                el_exp = (expIndex < expandable.length) ? expandable[expIndex] : Integer.MAX_VALUE;
                skip = true;
            }
            if (el_child == prev) {
                childIndex++;
                el_child = (childIndex < children.length) ? children[childIndex] : Integer.MAX_VALUE;
                skip = true;
            }

            /* Insert minimum value */
            if (!skip) { //Invariant: distinct. smallest element not equal to previous.
                //Select lowest value
                if (el_exp <= el_child) { //el_exp
                    //Make sure el_unexp and el_vert are >=
                    while (el_unexp < el_exp) {
                        unexpIndex++;
                        el_unexp = (unexpIndex < n_unexpandable.length) ? n_unexpandable[unexpIndex] : Integer.MAX_VALUE;
                    }
                    while (el_vert < el_exp) {
                        vIndex++;
                        el_vert = (vIndex < n_vertices.length) ? n_vertices[vIndex] : Integer.MAX_VALUE;
                    }

                    //Check if ==; if not, insert else ignore
                    if (el_exp != el_vert && el_exp != el_unexp) { //Filter
                        //Invariant: distinct, smallest and filtered
                        prev = el_exp;
                        n_expandable[index++] = el_exp;
                    }
                    expIndex++;
                    el_exp = (expIndex < expandable.length) ? expandable[expIndex] : Integer.MAX_VALUE;
                } else { //el_child
                    //Make sure el_unexp and el_vert are >=
                    while (el_unexp < el_child) {
                        unexpIndex++;
                        el_unexp = (unexpIndex < n_unexpandable.length) ? n_unexpandable[unexpIndex] : Integer.MAX_VALUE;
                    }
                    while (el_vert < el_child) {
                        vIndex++;
                        el_vert = (vIndex < n_vertices.length) ? n_vertices[vIndex] : Integer.MAX_VALUE;
                    }
                    //Check if ==; if not, insert else ignore
                    if (el_child != el_vert && el_child != el_unexp) { //Filter
                        //Invariant: distinct, smallest and filtered
                        prev = el_child;
                        n_expandable[index++] = el_child;
                    }

                    childIndex++;
                    el_child = (childIndex < children.length) ? children[childIndex] : Integer.MAX_VALUE;
                }
            }
        }

        /* The equivalent code with streams - slower */
 /*
        int forbiddenLength = expandable.length - expandIndex - 1;
        int[] children = g.expandable_children[expandNode];
        n_expandable = Arrays.copyOf(children, children.length + forbiddenLength);
        System.arraycopy(expandable, expandIndex + 1, n_expandable, children.length, forbiddenLength);
        n_expandable = IntStream.of(n_expandable)
                .sorted()
                .distinct()
                .filter(x -> Arrays.binarySearch(n_unexpandable, x) < 0
                && Arrays.binarySearch(n_vertices, x) < 0) //excl n_unexpandable && vertices
                .toArray();
         */
        return Arrays.copyOf(n_expandable, index);
    }

    /**
     * Get the CodeOcc associated with this State. The CodeOcc contains both the
     * code of this state as well as the vertices of this state ordered
     * according to the assignment in the code.
     *
     * @param g
     * @return
     */
    public abstract CodeOccResult getCodeOcc(Graph g);

    /**
     * Get the canonical labeling of this occurrence.
     *
     * @param g The graph where this state is an occurrence in.
     * @return The canonical labeling of this occurrence as determined by
     * {@link com.vincentderk.acircuitminer.miner.canonical.EdgeCanonical.minCanonicalPermutation(Graph, State)}
     * @see EdgeCanonical
     */
    public abstract long[] getCode(Graph g);

}
