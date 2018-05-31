package com.vincentderk.acircuitminer.miner.emulatable.neutralfinder.canonical;

import com.vincentderk.acircuitminer.miner.Graph;
import com.vincentderk.acircuitminer.miner.emulatable.neutralfinder.NeutralState;
import java.util.ArrayDeque;
import java.util.Arrays;

/**
 * Supports the canonical labeling of {@link NeutralState NeutralStates}. Format
 * example: (0,1)(0,2)*(1,x)(1,y)...
 * <p>
 * This is the same concept as
 * {@link com.vincentderk.acircuitminer.miner.canonical.EdgeCanonical EdgeCanonical}
 * but using a {@link CanonicalNeutralState} object. The procedure will use the
 * inputs of the nodes ({@link NeutralState}) to determine which nodes are still
 * present in the emulation and which are not.
 *
 * @author Vincent Derkinderen
 * @version 1.0
 */
public class NeutralEdgeCanonical {

    /**
     * Get the canonical code of the given state. It is assumed that the root
     * node ({@link NeutralState#root}) is already correct.
     *
     * @param g The graph
     * @param state The state of which the code is returned.
     * @return The canonical code of the given state. Each (e1,e2) is a long.
     * Every set of edges starting from a certain node is followed by the label
     * of that node. This code is paired with the reverse allocation of the
     * nodes in the graph to the nodeId in the code. The procedure will use the
     * inputs of the nodes ({@link NeutralState}) to determine which nodes are
     * still present in the emulation and which are not.
     */
    public static CodeStatePair minCanonicalPermutation(Graph g, NeutralState state) {
        CanonicalNeutralState root = new CanonicalNeutralState(state);
        ArrayDeque<CanonicalNeutralState> queue = new ArrayDeque<>();
        ArrayDeque<CanonicalNeutralState> list = new ArrayDeque<>();
        queue.add(root);
        //Encoding of the edges
        long[] code = new long[0];

        while (queue.peek().extensions.length != 0) {
            while (!queue.isEmpty()) {
                CanonicalNeutralState c_state = queue.poll();
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

        CodeStatePair pair = new CodeStatePair();
        pair.code = code;
        pair.revAllocation = queue.peek().revAllocation;

        return pair;
    }

    /**
     * Convert the given code to a more readable version (+,*,0,1,2,...)
     *
     * @param code The canonical labeling, as is stored in
     * {@link CodeOccResult}.
     * @return The readable version of the given code. Each element in code
     * represents either a (x,y) or A where A is an operation. If it is an
     * operation, it is converted to *,+ or i (input). If it an edge, it is
     * converted to the (x,y) representation. This is done for each element and
     * appended in that order.
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
     * {@link CanonicalNeutralState CanonicalNeutralStates} with the 'smallest'
     * code.
     *
     * @param list The list of CanonicalNeutralStates to check. This list is
     * modified.
     * @return A new list of all the CanonicalNeutralStates that have the
     * 'smallest' code as defined by
     * {@link CanonicalNeutralState#compareTo(CanonicalNeutralState)}.
     */
    private static ArrayDeque<CanonicalNeutralState> prune(ArrayDeque<CanonicalNeutralState> list) {
        ArrayDeque<CanonicalNeutralState> result = new ArrayDeque<>();
        CanonicalNeutralState min = list.poll();

        while (!list.isEmpty()) {
            CanonicalNeutralState state = list.poll();
            int compare = min.compareTo(state);

            if (compare == 0) {
                result.add(state);
            } else if (compare > 0) { //new minimum
                min = state;
                result.clear();
            }
        }

        result.add(min);
        return result;
    }
}
