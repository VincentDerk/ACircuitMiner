package com.vincentderk.acircuitminer.miner.canonical;

import com.vincentderk.acircuitminer.miner.Graph;
import com.vincentderk.acircuitminer.miner.StateSingleOutput;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import java.util.ArrayDeque;
import java.util.Arrays;

/**
 * Class that can be used to retrieve the canonical labeling of a rooted DAG.
 * <p>
 * It does this by converting the graph to a different representation:
 * (0,1)(0,2)A(1,3)(1,4)B... Where (0,1) stands for a connection between node 0
 * and node 1, 0 being the node higher in the hierarchy. A and B are the labels
 * of the node. The idea is that the label follows after enumerating all
 * connections coming into the node (e.g 0 and 1 respectively for A and B).
 * <p>
 * To get a canonical representation, there are certain restrictions such as the
 * least numbers first. This is shown in the example where first the connections
 * of 0 are enumerated, then the label (operation) of 0 followed by the
 * connections of node 1 and its label (operation). To remove the dependency of
 * the representation on the node identifiers, every possible combination of
 * assignments (id to node) is looked at and again in a certain order the best
 * ('smallest') is picked. This means that, two isomorphic graphs will have the
 * same canonical labeling returned.
 * <p>
 * The possible assignments are performed in a certain way to get a fast
 * assignment that adheres to the rule that isomorphic graphs have the canonical
 * labeling and non-isomorphic graphs have a different. There are two versions
 * of the algorithm which are similar in the assignment but differ in the way
 * the assignments are checked.
 * <p>
 * The first algorithm is a breadth-first approach and is thought (not checked)
 * to be the fastest for small patterns. It performs an extra assignment for
 * each case and checks whether it can already throw away cases (due to 'larger'
 * canonical labeling so far).
 * <p>
 * The second algorithm is a depth-first approach that first completes one case
 * (all nodes have an assignment). Then the other cases (other full
 * assignments)are checked in a depth-first manner where in-between the
 * assignment of another node we check if the code is 'smaller'/'equal' to the
 * current 'smallest' canonical labeling. A branch in the search tree where we
 * find a 'larger' canonical labeling can be stopped as soon as it is found to
 * be 'larger'. This likely requires less memory than the first algorithm but
 * might be slower for small patterns where less memory (and thus IO) was
 * required anyway. ({@link #minCanonicalPermutationDFS(dagfsm.Graph)}.
 * <p>
 * <b>Notable methods:</b>
 * <ul>
 * <li> {@link #printCode(long[])} Convert the given canonical labeling to a
 * more readable (*,+,0,1,2,..) version.</li>
 * <li> {@link #minCanonicalPermutation(dagfsm.Graph)} </li> Breadth-first
 * approach (likely fastest for small graphs)
 * <li> {@link #minCanonicalPermutation(dagfsm.Graph, dagfsm.State)} </li>
 * Breadth-first approach (likely fastest for small graphs)
 * <li> {@link #minCanonicalPermutationDFS(dagfsm.Graph)} </li> Depth-first
 * approach (likely fastest for larger graphs)
 * <li> {@link #minCanonicalPermutationDFS(dagfsm.Graph, dagfsm.State)} </li>
 * Depth-first approach (likely fastest for larger graphs)
 * </ul>
 *
 * @author Vincent Derkinderen
 * @version 2.0
 */
public class EdgeCanonical {

    /**
     * Get the minimal canonical code. This creates a {@link StateSingleOutput}
     * of the given Graph and calls
     * {@link #minCanonicalPermutation(Graph, State)}. During conversion, all
     * nodes with the special marker {@link Graph#MARKER} are excluded.
     *
     * @param g The graph to get the code of.
     * @return The canonical code result of the given graph g while excluding
     * the nodes with the special marker as label.
     *
     * @see #minCanonicalPermutation(Graph, State)
     */
    public static CodeOccResult minCanonicalPermutation(Graph g) {
        int root = g.getFirstNonOutputNode();
        int[] vertices;
        int[] expandable = new int[0];
        int[] unexpandable;
        int interNode = -1;

        IntArrayList verticesList = new IntArrayList();
        IntArrayList unexpandableList = new IntArrayList();

        for (int i = 0; i < g.label.length; i++) {
            if (g.label[i] == Graph.INPUT) {
                unexpandableList.add(i);
            } else if (g.label[i] != Graph.MARKER) {
                verticesList.add(i);
            }
        }

        vertices = verticesList.toIntArray();
        verticesList = null;
        unexpandable = unexpandableList.toIntArray();
        unexpandableList = null;

        StateSingleOutput state = new StateSingleOutput(root, vertices, expandable, unexpandable, interNode);
        return minCanonicalPermutation(g, state);
    }

    /**
     * Get the minimal canonical code. It is assumed that the root node is
     * already correct.
     * <p>
     * Beware, the incoming edges to each node may only contain distinct values.
     * No node may be directly connected to the same node twice.
     *
     * @param g The graph
     * @param state The state of which the code is returned.
     * @return Information regarding the minimal canonical code of the given
     * state. Canonical code: Each (e1,e2) is a long and every set of edges
     * starting from a certain node is followed by the label (operation) of that
     * node. e.g. {@code (0,1)(0,2)+(1,3)(1,4)*} where + and * are also encoded
     * as a long value.
     */
    public static CodeOccResult minCanonicalPermutation(Graph g, StateSingleOutput state) {
        int nbVertex = state.vertices.length;

        CanonicalState root = new CanonicalState(state);
        ArrayDeque<CanonicalState> queue = new ArrayDeque<>();
        ArrayDeque<CanonicalState> list = new ArrayDeque<>();
        queue.add(root);
        //Encoding of the edges
        long[] code = new long[0];

        for (int i = 0; i < nbVertex; i++) {

            while (!queue.isEmpty()) {
                CanonicalState c_state = queue.poll();
                list.addAll(c_state.expand(g));
            }
            queue = prune(list);
            list = new ArrayDeque<>();

            //Update current code
            long[] extra = queue.peek().code;
            int oldSize = code.length;
            code = Arrays.copyOf(code, code.length + extra.length);
            System.arraycopy(extra, 0, code, oldSize, extra.length);
        }

        CodeOccResult result = new CodeOccResult(code, queue.getFirst().getVerticesInOrder(), queue.getFirst().inputCount);

        return result;
    }

    /**
     * Convert the given code to a more readable version (+,*,0,1,2,...)
     *
     * @param code The canonical labeling, as is stored in
     * {@link CodeOccResult}.
     * @return The readable version of the given code. Each element in code
     * represents either a (x,y) or A where A is an operation.
     * <ul>
     * <li>If it is an operation, it is converted to *,+ or i (input).</li>
     * <li>If it an edge, it is converted to the (x,y) representation.</li>
     * </ul>
     * This is done for each element and appended in that order.
     *
     * @throws IllegalArgumentException if there is an operation that it does
     * not cover. Only
     * {@link Graph#SUM}, {@link Graph#PRODUCT}, {@link Graph#INPUT} are
     * allowed.
     */
    public static String printCode(long[] code) {
        StringBuilder builder = new StringBuilder();
        long MASK = ((long) 1 << 32) - 1; //first 32 bits
        for (long l : code) {
            if (l < Long.MAX_VALUE - Graph.HIGHEST_OP) {
                builder.append("(");
                builder.append(l >> 32);
                builder.append(",");
                builder.append(l & MASK);
                builder.append(")");
            } else {
                short label = (short) (Long.MAX_VALUE - l);

                switch (label) {
                    case Graph.PRODUCT:
                        builder.append("*");
                        break;
                    case Graph.SUM:
                        builder.append("+");
                        break;
                    case Graph.INPUT:
                        builder.append("i");
                        break;
                    default:
                        throw new IllegalArgumentException("Error in edge code for l: " + l);
                }
            }
        }

        return builder.toString();
    }

    /**
     * Iterates over the given list and returns the
     * {@link CanonicalState CanonicalStates} with the 'smallest' code.
     *
     * @param list The list of CanonicalStates to check. This list is modified.
     * @return A new list of all the CanonicalStates that have the 'smallest'
     * code as defined by {@link CanonicalState#compareTo(CanonicalState)}.
     */
    private static ArrayDeque<CanonicalState> prune(ArrayDeque<CanonicalState> list) {
        ArrayDeque<CanonicalState> result = new ArrayDeque<>();
        CanonicalState min = list.poll();

        while (!list.isEmpty()) {
            CanonicalState state = list.poll();
            int compare = min.compareTo(state);

            if (compare == 0) {
                result.add(state);
            } else if (compare > 0) { //new minimum
                min = state;
                result.clear();

            }/* else {
                System.out.println("pruned " + printCode(state.code) + " by " + printCode(min.code) + " with prunedRev: " + state.revAllocation);
            }*/
        }

        result.add(min);
        return result;
    }

    /**
     * Get the minimal canonical code using a Depth first search. This creates a
     * {@link StateSingleOutput} of the given Graph and calls
     * {@link #minCanonicalPermutationDFS(Graph, State)}. During conversion, all
     * nodes with the special marker {@link Graph#MARKER} are excluded.
     *
     * @param g The graph to get the code of.
     * @return The canonical code result of the given graph g while excluding
     * the nodes with the special marker as label.
     *
     * @see #minCanonicalPermutationDFS(Graph, State)
     */
    public static CodeOccResult minCanonicalPermutationDFS(Graph g) {
        int root = g.getFirstNonOutputNode();
        int[] vertices;
        int[] expandable = new int[0];
        int[] unexpandable;
        int interNode = -1;

        IntArrayList verticesList = new IntArrayList();
        IntArrayList unexpandableList = new IntArrayList();

        for (int i = 0; i < g.label.length; i++) {
            if (g.label[i] == Graph.INPUT) {
                unexpandableList.add(i);
            } else if (g.label[i] != Graph.MARKER) {
                verticesList.add(i);
            }
        }

        vertices = verticesList.toIntArray();
        verticesList = null;
        unexpandable = unexpandableList.toIntArray();
        unexpandableList = null;

        StateSingleOutput state = new StateSingleOutput(root, vertices, expandable, unexpandable, interNode);
        return minCanonicalPermutationDFS(g, state);
    }

    /**
     * Get the minimal canonical code using an Depth First approach. It is
     * assumed that the root node is already correct.
     *
     * @param g The graph
     * @param state The state of which the code is returned.
     * @return The minimal canonical code of the given state. Each (e1,e2) is a
     * long. Every set of edges starting from a certain node is followed by the
     * label of that node.
     */
    public static CodeOccResult minCanonicalPermutationDFS(Graph g, StateSingleOutput state) {
        /**
         * Since we perform a depth-first approach we only need one
         * CanonicalState (c_state) to keep track of the ids assigned to the
         * nodes and which node to assign next.
         *
         * We use DecisionPointInfo objects (returned by c_state.expandDFS) that
         * store the information on the decision made (branch). We store these
         * objects on a stack and when we backtrack to choose a next branch, we
         * use that information object to rollback the c_state. After that,
         * still using the same decision object, we can go into the next branch
         * (expandDFS(g, dpI)).
         */
        ArrayDeque<DecisionPointInfo> stack = new ArrayDeque<>();

        CanonicalState c_state = new CanonicalState(state);
        DecisionPointInfo root = c_state.expandDFS(g);
        stack.add(root);

        long[] best_code = c_state.code;
        Int2IntOpenHashMap best_revAllocation = null;

        //init - full_forward
        while (c_state.extensions.length > 0) {
            stack.push(c_state.expandDFS(g));
            //add code
            int oldLength = best_code.length;
            best_code = Arrays.copyOf(best_code, best_code.length + c_state.code.length);
            System.arraycopy(c_state.code, 0, best_code, oldLength, c_state.code.length);
        }
        best_revAllocation = c_state.revAllocation.clone();
        int inputCount = c_state.inputCount; //Stays the same
        int currentCodeCompareIndex = best_code.length;

        // Go back and forth in search tree
        while (!stack.isEmpty()) {
            //backwards
            DecisionPointInfo lastDpI = stack.peek();
            c_state.rollBack(lastDpI);

            if (c_state.expandDFS(g, lastDpI) == null) {
                stack.pop();
                currentCodeCompareIndex -= (g.inc[lastDpI.extension].length + 1);
                continue; //back to while to continue going backwards
            }

            //forwards
            boolean stop = false;
            boolean replacing = false;
            while (c_state.extensions.length > 0 && !stop) {
                stack.push(c_state.expandDFS(g));

                if (replacing) {
                    //fill in best
                    System.arraycopy(c_state.code, 0, best_code, currentCodeCompareIndex, c_state.code.length);
                } else {
                    int c = compareSubcode(c_state.code, 0, best_code, currentCodeCompareIndex);

                    if (c > 0) {
                        stop = true;
                        lastDpI = stack.pop();
                        c_state.rollBack(lastDpI);
                        currentCodeCompareIndex -= (g.inc[lastDpI.extension].length + 1);
                    } else if (c < 0) {
                        replacing = true;
                        System.arraycopy(c_state.code, 0, best_code, currentCodeCompareIndex, c_state.code.length);
                    }
                }
                currentCodeCompareIndex += c_state.code.length;
            }

            if (replacing) {
                best_revAllocation = c_state.revAllocation.clone();
            }
        }
        CodeOccResult result = new CodeOccResult(best_code, c_state.getVerticesInOrder(best_revAllocation), inputCount);

        return result;
    }

    /**
     * Check how arr1 compares to arr2 starting from startIndex1 and startIndex2
     * respectively.
     *
     * @param arr1 The first array
     * @param startIndex1 The index to start comparing from, of the first array.
     * @param arr2 The second array
     * @param startIndex2 The index to start comparing with, of the second
     * array.
     * @return The elements of arr1 and arr2 are compared starting from
     * startIndex1 and startIndex2 respectively.
     * <ul>
     * <li>value less than 0 is returned if arr1 has an element smaller than
     * that of arr2.</li>
     * <li>0 is returned if arr1 and arr2 are equal in the compared subset.</li>
     * <li>value greater than 0 is returned if arr1 has an alement larger than
     * that of arr2.</li>
     * </ul>
     *
     * When one array is longer than the other but they are equal up to that
     * point, only up to that point is checked.
     */
    private static int compareSubcode(long[] arr1, int startIndex1, long[] arr2, int startIndex2) {
        for (int i = startIndex1, j = startIndex2; i < arr1.length && j < arr2.length; i++, j++) {
            int r = Long.compare(arr1[i], arr2[j]);

            if (r != 0) {
                return r;
            }
        }

        //Equal so far.
        return 0;
    }

}
