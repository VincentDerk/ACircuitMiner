package com.vincentderk.acircuitminer.miner.enumerators;

import com.vincentderk.acircuitminer.miner.util.ArrayLongHashStrategy;
import com.vincentderk.acircuitminer.miner.Graph;
import com.vincentderk.acircuitminer.miner.State;
import com.vincentderk.acircuitminer.miner.canonical.CodeOccResult;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenCustomHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.ArrayDeque;
import java.util.Map.Entry;

/**
 * The same as {@link BackTrackEnumerator} but as a {@link SecondaryEnumerator}
 * it expands a given collection of {@link State states} instead of expanding
 * all nodes in the {@link Graph}.
 *
 * <p>
 * Of the given {@link State states} that it has to expand, it only adds those
 * that are larger than {@code prevK}. In regards to the next expandable
 * {@link State states}: It stores any found {@link State} with a valid pattern
 * that is larger than {@code prevK}.
 *
 * @author Vincent Derkinderen
 * @version 1.0
 */
public class SecondaryBackTrackEnumerator implements SecondaryEnumerator {

    /**
     * Create an enumerator instance that can enumerate over the given
     * {@link Graph}.
     *
     * @param g The graph where to enumerate over by using
     * {@link #expandSelectedPatterns(Object2ObjectOpenCustomHashMap, java.util.Map.Entry<long[],ObjectArrayList<State>>[],
     * int, int, boolean, int)}.
     */
    public SecondaryBackTrackEnumerator(Graph g) {
        this.g = g;
        this.expandableStates = new Object2ObjectOpenCustomHashMap(new ArrayLongHashStrategy());
    }

    private final Graph g;
    private final Object2ObjectOpenCustomHashMap<long[], ObjectArrayList<State>> expandableStates;

    /**
     * Get the encountered states that can still be expanded.
     *
     * @return The states that can still be expanded.
     */
    @Override
    public Object2ObjectOpenCustomHashMap<long[], ObjectArrayList<State>> getExpandableStates() {
        return this.expandableStates;
    }

    @Override
    public void expandSelectedPatterns(Object2ObjectOpenCustomHashMap<long[], ObjectArrayList<int[]>> patternsMap,
            Entry<long[], ObjectArrayList<State>>[] baseStates, int k,
            int maxInputs, boolean expandAfterFlag, int prevK) {
        this.baseStates = baseStates;
        if (this.baseStates.length == 0) {
            System.out.println("No base states to expand!");
            return;
        }

        State base;
        ArrayDeque<State> stack = new ArrayDeque<>();
        ArrayDeque<Integer> indexStack = new ArrayDeque<>();

        while ((base = getBaseState()) != null) {
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

                    if (expanded.vertices.length > prevK && expanded.interNode == -1
                            && expanded.expandable.length + expanded.unexpandable.length <= maxInputs) { //Count #Inputs (excl literals)
                        /* Pattern occurrence count */
                        codeOcc = expanded.getCodeOcc(g);
                        if (codeOcc.inputCount <= maxInputs) { //Count #Inputs (incl literals)
                            ObjectArrayList<int[]> singleObj = new ObjectArrayList<>();
                            singleObj.add(codeOcc.lastVerticesOrdered);
                            patternsMap.merge(codeOcc.code, singleObj, (v1, v2) -> mergeArray(v1, v2)); //patterns.addTo(code, 1);
                        }
                    }

                    /* continue expanding? */
                    if (expanded.vertices.length < k) {
                        stack.push(expanded);
                        indexStack.push(0);

                        if (expandAfterFlag && codeOcc != null && expanded.vertices.length > prevK) {
                            expandableStates.merge(codeOcc.code, new ObjectArrayList(new com.vincentderk.acircuitminer.miner.State[]{expanded}), (v1, v2) -> mergeStateArray(v1, v2));
                        }

                    } else if (expandAfterFlag && codeOcc != null) {
                        //Note: This excludes current invalid occurrences (intermediate output / maxInputs)
                        expandableStates.merge(codeOcc.code, new ObjectArrayList(new com.vincentderk.acircuitminer.miner.State[]{expanded}), (v1, v2) -> mergeStateArray(v1, v2));
                    }
                }
            }
        }
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
    private int nextPattern;
    private Entry<long[], ObjectArrayList<State>>[] baseStates;

    /**
     * Get the next unexpanded base state
     *
     * @return The next State that has to be expanded. null if there is no more
     * next State.
     */
    public State getBaseState() {
        ObjectArrayList<State> currStates = baseStates[nextPattern].getValue();

        if (nextNb >= currStates.size()) {
            baseStates[nextPattern++] = null;
            if (nextPattern >= baseStates.length) {
                return null;
            } else {
                currStates = baseStates[nextPattern].getValue();
            }
            nextNb = 0;
        }

        return currStates.get(nextNb++);
    }

    @Override
    public String toString() {
        return "SecondaryBackTrackEnumerator";
    }

}
