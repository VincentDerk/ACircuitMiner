package com.vincentderk.acircuitminer.miner.enumerators;

import com.vincentderk.acircuitminer.miner.util.ArrayLongHashStrategy;
import com.vincentderk.acircuitminer.miner.Graph;
import com.vincentderk.acircuitminer.miner.StateSingleOutput;
import com.vincentderk.acircuitminer.miner.canonical.CodeOccResult;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenCustomHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.ArrayDeque;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The {@link ForwardEnumerator} uses a semi-BFS approach. This means it
 * requires more memory. The runtime starts off being very similar to
 * {@link BackTrackEnumerator} but as soon as the graph gets big it gets slower.
 * However, it might be useful when we prune every stage. This would be harder
 * for the DFS approach ({@link BackTrackEnumerator}) as they only have all the
 * pattern counts after performing the whole algorithm (due to DFS).
 * <p>
 * Semi-BFS: It expands a state in all possible ways and then proceeds with one
 * of them in the same way. This is different from {@link BackTrackEnumerator}
 * in that {@link BackTrackEnumerator} keeps track of which ways it has already
 * expanded in. So when a {@link StateSingleOutput} is fully expanded, it
 * backtracks back to the previously expanded {@link StateSingleOutput} and
 * expands it in a different way.
 * <p>
 * For now, it is probably inferior to {@link BackTrackEnumerator} and
 * {@link MultiBackTrackEnumerator}.
 *
 * @author Vincent Derkinderen
 * @version 2.0
 */
public class ForwardEnumerator implements Enumerator {

    public ForwardEnumerator(Graph g) {
        nextNb = new AtomicInteger(0);
    }

    @Override
    public Object2ObjectOpenCustomHashMap<long[], ObjectArrayList<int[]>> enumerate(Graph g, int k, int maxPorts) {
        Object2ObjectOpenCustomHashMap<long[], ObjectArrayList<int[]>> patterns = new Object2ObjectOpenCustomHashMap<>(new ArrayLongHashStrategy());

        final int maxInputs = maxPorts - 1;

        long cTime = System.currentTimeMillis();
        ArrayDeque<StateSingleOutput> deque = getSingleStates(g);
        System.out.println("Retrieved single states in " + (System.currentTimeMillis() - cTime));

        while (!deque.isEmpty()) {
            StateSingleOutput c_state = deque.poll();

            //Iterate over all possible extensions of the popped c_state
            int expandLength = c_state.expandable.length;
            for (int expandIndex = 0; expandIndex < expandLength; expandIndex++) {
                StateSingleOutput expanded = c_state.expand(g, expandIndex);
                CodeOccResult codeOcc = null;

                if (expanded.expandable.length + expanded.unexpandable.length <= maxInputs //Count #Inputs (excl literals)
                        && expanded.interNode == -1) {
                    /* Pattern occurrence count */
                    codeOcc = expanded.getCodeOcc(g);
                    if (codeOcc.inputCount <= maxInputs) { //Count #Inputs (incl literals)
                        ObjectArrayList<int[]> singleObj = new ObjectArrayList<>();
                        singleObj.add(codeOcc.lastVerticesOrdered);
                        patterns.merge(codeOcc.code, singleObj, (v1, v2) -> mergeArray(v1, v2)); //patterns.addTo(code, 1);
                    }
                }

                if (expanded.vertices.length < k) {
                    deque.add(expanded);
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
    private static ObjectArrayList<StateSingleOutput> mergeStateArray(ObjectArrayList<StateSingleOutput> old, ObjectArrayList<StateSingleOutput> newV) {
        old.add(newV.get(0));
        return old;
    }

    private final AtomicInteger nextNb;

    /**
     * Get all the unexpanded single {@link StateSingleOutput states}.
     *
     * @param g The graph structure for context.
     * @return All expandable states with one node.
     */
    public ArrayDeque<StateSingleOutput> getSingleStates(Graph g) {
        ArrayDeque<StateSingleOutput> result = new ArrayDeque<>();

        StateSingleOutput s;
        while ((s = getSingleState(g)) != null) {
            result.add(s);
        }

        return result;
    }

    /**
     * Get the next unexpanded single {@link StateSingleOutput}. This can be
     * called concurrently since it is based on an {@link AtomicInteger}.
     *
     * @param g The graph structure for context.
     * @return The next {@link StateSingleOutput} that has to be expanded. null
     * if there is no more next {@link StateSingleOutput}.
     */
    public StateSingleOutput getSingleState(Graph g) {
        int currentNb = nextNb.getAndIncrement();
        if (currentNb >= g.inc.length) { //Check whether there is still a node.
            return null;
        }

        if (g.inc[currentNb].length == 0) { //in case of literal node
            return getSingleState(g);
        } else {
            int[] vertices = {currentNb};
            int[] expandable = g.expandable_children[currentNb];
            int[] unexpandable = g.unexpandable_children[currentNb];

            return new StateSingleOutput(currentNb, vertices, expandable, unexpandable, -1);
        }
    }

    @Override
    public String toString() {
        return "ForwardEnumerator";
    }

}
