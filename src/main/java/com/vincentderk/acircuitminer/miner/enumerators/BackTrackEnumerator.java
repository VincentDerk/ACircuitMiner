package com.vincentderk.acircuitminer.miner.enumerators;

import com.vincentderk.acircuitminer.miner.util.ArrayLongHashStrategy;
import com.vincentderk.acircuitminer.miner.Graph;
import com.vincentderk.acircuitminer.miner.State;
import com.vincentderk.acircuitminer.miner.canonical.CodeOccResult;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenCustomHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.ArrayDeque;

/**
 *
 * An enumerator that finds occurrences with only one output and a maximum
 * amount of inputs. It does this by finding all occurrences that have a certain
 * node as root. This is then repeated for each node in the given graph. When
 * expanding an occurrence into a bigger occurrence, we only need to consider
 * the children. This is due to the fact that we only search for induced
 * subgraphs. An occurrence might have multiple children and thus multiple ways
 * to be extended.
 *
 * <p>
 * The extending of an occurrence into another occurrence is done by a
 * depth-first approach. At each point, it keeps track of which node it
 * expanded. When later a backtrack occurs, another index (node) is expanded
 * instead. To avoid finding the same occurrence twice (by extending nodes in a
 * different order), an unexpandable list is used to store nodes that should not
 * be expanded anymore. This is based on "Automatic enumeration of all connected
 * subgraphs" of Rücker et al.
 *
 * <p>
 * The expandable states that are tracked are those that were occurrences of
 * valid patterns (satisfied input and output restrictions) but reached the
 * maximum amount of internal nodes. This is incomplete since there might be
 * occurrences that only temporarily not satisfy a restriction.
 *
 * @author Vincent Derkinderen
 * @version 1.0
 */
public class BackTrackEnumerator implements PrimaryEnumerator {

    /**
     * Create an enumerator instance that can enumerate over the given
     * {@link Graph}.
     *
     * @param g The graph where to enumerate over by using
     * {@link #enumerate(Graph, int, int, boolean)}.
     */
    public BackTrackEnumerator(Graph g) {
        nextNb = 0;
        expandableStates = new Object2ObjectOpenCustomHashMap(new ArrayLongHashStrategy());
    }

    /**
     * Get the encountered states that can still be expanded.
     *
     * @return The states that can still be expanded.
     */
    @Override
    public Object2ObjectOpenCustomHashMap<long[], ObjectArrayList<State>> getExpandableStates() {
        return expandableStates;
    }

    private final Object2ObjectOpenCustomHashMap<long[], ObjectArrayList<State>> expandableStates;

    @Override
    public Object2ObjectOpenCustomHashMap<long[], ObjectArrayList<int[]>> enumerate(Graph g, int k, int maxInputs, boolean expandAfterFlag) {
        Object2ObjectOpenCustomHashMap<long[], ObjectArrayList<int[]>> patterns = new Object2ObjectOpenCustomHashMap<>(new ArrayLongHashStrategy());
        State base;
        ArrayDeque<State> stack = new ArrayDeque<>();
        ArrayDeque<Integer> indexStack = new ArrayDeque<>();

        while ((base = getSingleState(g)) != null) {
            stack.push(base);
            indexStack.push(0);
            while (!stack.isEmpty()) {
                State c_state = stack.peek();
                int expandIndex = indexStack.pop();

                if (c_state.expandable.length <= expandIndex) {
                    /* finished expanding this state */
                    stack.pop();
                } else {
                    /* Continue expanding */
                    indexStack.push(expandIndex + 1);
                    State expanded = c_state.expand(g, expandIndex);
                    CodeOccResult codeOcc = null;

                    if (expanded.interNode == -1
                            && expanded.expandable.length + expanded.unexpandable.length <= maxInputs) { //Count #Inputs (excl literals)
                        /* Pattern occurrence count */
                        codeOcc = expanded.getCodeOcc(g);
                        if (codeOcc.inputCount <= maxInputs) { //Count #Inputs (incl literals)
                            ObjectArrayList<int[]> singleObj = new ObjectArrayList<>();
                            singleObj.add(codeOcc.lastVerticesOrdered);
                            patterns.merge(codeOcc.code, singleObj, (v1, v2) -> mergeArray(v1, v2)); //patterns.addTo(code, 1);
                        }
                    }

                    /* continue expanding? */
                    if (expanded.vertices.length < k) {
                        stack.push(expanded);
                        indexStack.push(0);
                    } else if (expandAfterFlag && codeOcc != null) {
                        //Note: This excludes current invalid occurrences (intermediate output / maxInputs)
                        expandableStates.merge(codeOcc.code, new ObjectArrayList(new com.vincentderk.acircuitminer.miner.State[]{expanded}), (v1, v2) -> mergeStateArray(v1, v2));
                    }
                }
            }
        }

        return patterns;
    }

    /**
     * Adds the first element of newV to old.
     *
     * @param old The list to add an element to.
     * @param newV A list of which to get the first element.
     * @return {@code old}
     */
    private static ObjectArrayList<int[]> mergeArray(ObjectArrayList<int[]> old, ObjectArrayList<int[]> newV) {
        old.add(newV.get(0));
        return old;
    }

    /**
     * Adds the first element of newV to old.
     *
     * @param old The list to add an element to.
     * @param newV A list of which to get the first element.
     * @return {@code old}
     */
    private static ObjectArrayList<State> mergeStateArray(ObjectArrayList<State> old, ObjectArrayList<State> newV) {
        old.add(newV.get(0));
        return old;
    }

    private int nextNb;

    /**
     * Get the next unexpanded single node {@link State}.
     *
     * @param g The graph structure for context.
     * @return The next State that has to be expanded. null if there is no more
     * next State.
     */
    public State getSingleState(Graph g) {
        int currentNb = nextNb++;
        if (currentNb >= g.inc.length) { //Check whether there is still a node.
            return null;
        }
        if (g.inc[currentNb].length == 0) { //in case of literal node
            return getSingleState(g);
        } else {
            int[] vertices = {currentNb};
            int[] expandable = g.expandable_children[currentNb];
            int[] unexpandable = g.unexpandable_children[currentNb];

            return new State(currentNb, vertices, expandable, unexpandable, -1);
        }
    }

    @Override
    public String toString() {
        return "BackTrackEnumerator";
    }

}
