package com.vincentderk.acircuitminer.miner.enumerators;

import com.vincentderk.acircuitminer.miner.util.ArrayLongHashStrategy;
import com.vincentderk.acircuitminer.miner.StateSingleOutput;
import com.vincentderk.acircuitminer.miner.Graph;
import com.vincentderk.acircuitminer.miner.canonical.CodeOccResult;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMaps;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenCustomHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.ArrayDeque;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The same as {@link MultiBackTrackEnumerator} but now finding occurrences with
 * more than one output but still a single root. Compared to the occurrences
 * found in {@link MultiBackTrackEnumerator}, some nodes of those found
 * occurrences can now have an additional externally-outgoing edge that provides
 * another output.
 *
 * <P>
 * <b>Beware, these occurrences may not all be valid in that an output can be
 * (indirectly) required as an input of itself.</b>
 *
 * @author Vincent Derkinderen
 * @version 2.0
 */
public class MultiBTMultiOutputSingleRootEnumerator implements PrimaryEnumerator {

    /**
     * Create an enumerator instance that can enumerate over the given
     * {@link Graph}.
     *
     * @param g The graph where to enumerate over by using
     * {@link #enumerate(Graph, int, int, boolean)}.
     * @param threadCount The amount of threads to use.
     */
    public MultiBTMultiOutputSingleRootEnumerator(Graph g, int threadCount) {
        THREAD_COUNT = threadCount;
        nextNb = new AtomicInteger(0);
        expandableStates = new Object2ObjectOpenCustomHashMap(new ArrayLongHashStrategy());
    }

    /**
     * Create an enumerator instance that can enumerate over the given graph.
     * The amount of threads used is determined by the amount of cores using
     * {@link java.lang.Runtime#availableProcessors()}.
     *
     * @param g The graph where to enumerate over by using
     * {@link #enumerate(Graph, int, int, boolean)}.
     */
    public MultiBTMultiOutputSingleRootEnumerator(Graph g) {
        THREAD_COUNT = Runtime.getRuntime().availableProcessors();
        nextNb = new AtomicInteger(0);
        expandableStates = new Object2ObjectOpenCustomHashMap(new ArrayLongHashStrategy());
    }

    /**
     * Get the encountered states that can still be expanded.
     *
     * @return The states that can still be expanded.
     */
    @Override
    public Object2ObjectOpenCustomHashMap<long[], ObjectArrayList<com.vincentderk.acircuitminer.miner.StateSingleOutput>> getExpandableStates() {
        return expandableStates;
    }

    private final Object2ObjectOpenCustomHashMap<long[], ObjectArrayList<com.vincentderk.acircuitminer.miner.StateSingleOutput>> expandableStates;
    private final int THREAD_COUNT;

    /**
     * A thread that can perform the enumeration.
     */
    private class EnumThread extends Thread {

        EnumThread(int thread_id, AtomicInteger live_threads, Graph g, int k, int maxInputs, boolean expandAfterFlag) {
            this.thread_id = thread_id;
            this.live_threads = live_threads;
            this.g = g;
            this.k = k;
            this.maxInputs = maxInputs;
            this.expandAfterFlag = expandAfterFlag;
        }

        final int thread_id;
        final AtomicInteger live_threads;
        Object2ObjectOpenCustomHashMap<long[], ObjectArrayList<int[]>> subPatterns;
        Object2ObjectOpenCustomHashMap<long[], ObjectArrayList<com.vincentderk.acircuitminer.miner.StateSingleOutput>> subExpandableStates;

        final Graph g;
        final int k;
        final int maxInputs;
        final boolean expandAfterFlag;

        @Override
        public void run() {
            subPatterns = enumerate_aux(g, k, maxInputs, expandAfterFlag);
            System.out.printf("Thread %d finished. Found patterns: %d. %d threads remaining.\n", thread_id, subPatterns.size(), live_threads.decrementAndGet());
        }

        /**
         * Finds patterns and their occurrences in the given graph g by
         * splitting the problem into find occurrences with one node and then
         * determining their pattern. This is repeated for each node in the
         * graph. To achieve this with multiple thread, there is an
         * AtomicInteger that is used to retrieve the next node to check.
         *
         * @param g The backend graph to search in.
         * @param k The maximum pattern size, equal to the amount of internal
         * nodes. When this number is reached, extension is stopped.
         * @param maxInputs The maximum amount of inputs that an occurrence
         * ({link State}) may have.
         * @param expandAfterFlag Denotes whether the enumerator should keep
         * track of the expandableStates. When set to true, a list of expandable
         * occurrences is kept such that they can later be retrieved
         * ({@link #getExpandableStates()}) and expanded further. If this is the
         * last enumeration of the process and you do not want to keep track of
         * the further expandable occurrences, set to false. Otherwise use true.
         * @return Mapping of pattern -> list of the roots of the found
         * occurrences of that pattern.
         */
        private Object2ObjectOpenCustomHashMap<long[], ObjectArrayList<int[]>> enumerate_aux(Graph g, int k, int maxInputs, boolean expandAfterFlag) {
            this.subExpandableStates = new Object2ObjectOpenCustomHashMap(new ArrayLongHashStrategy());
            Object2ObjectOpenCustomHashMap<long[], ObjectArrayList<int[]>> patterns = new Object2ObjectOpenCustomHashMap<>(new ArrayLongHashStrategy());
            com.vincentderk.acircuitminer.miner.StateSingleOutput base;

            ArrayDeque<com.vincentderk.acircuitminer.miner.StateSingleOutput> stack = new ArrayDeque<>();
            ArrayDeque<Integer> indexStack = new ArrayDeque<>();
            while ((base = getSingleState(g)) != null) {
                stack.push(base);
                indexStack.push(0);
                while (!stack.isEmpty()) {
                    com.vincentderk.acircuitminer.miner.StateSingleOutput c_state = stack.peek();
                    int expandIndex = indexStack.pop();

                    if (c_state.expandable.length <= expandIndex) {
                        /* finished expanding this state */
                        stack.pop();
                    } else {
                        /* Continue expanding */
                        indexStack.push(expandIndex + 1);
                        com.vincentderk.acircuitminer.miner.StateSingleOutput expanded = c_state.expand(g, expandIndex);
                        CodeOccResult codeOcc = null;

                        if (expanded.interNode == -1
                                && expanded.expandable.length + expanded.unexpandable.length <= maxInputs) { //Count #Inputs
                            /* Pattern occurrence count */
                            codeOcc = expanded.getCodeOcc(g);
                            if (codeOcc.inputCount <= maxInputs) {
                                //System.out.println("Found " + EdgeCanonical.printCode(codeOcc.code) + " and codeOcc count " + codeOcc.inputCount + " and other " + (expanded.expandable.length + expanded.unexpandable.length));
                                ObjectArrayList<int[]> singleObj = new ObjectArrayList<>();
                                singleObj.add(codeOcc.lastVerticesOrdered); //vertices of expanded sorted in assigned order.
                                patterns.merge(codeOcc.code, singleObj, (v1, v2) -> merge2Arrays(v1, v2));
                            }
                        }

                        /* continue expanding? */
                        if (expanded.vertices.length < k) {
                            stack.push(expanded);
                            indexStack.push(0);
                        } else if (expandAfterFlag && codeOcc != null) {
                            //Note: This excludes current invalid occurrences (intermediate output / maxInputs)
                            subExpandableStates.merge(codeOcc.code, new ObjectArrayList(new com.vincentderk.acircuitminer.miner.StateSingleOutput[]{expanded}), (v1, v2) -> mergeStateArrays(v1, v2));
                        }

                    }
                }
            }
            return patterns;
        }
    }

    @Override
    public Object2ObjectOpenCustomHashMap<long[], ObjectArrayList<int[]>> enumerate(Graph g, int k, int maxPorts, boolean expandAfterFlag) {
        Object2ObjectOpenCustomHashMap<long[], ObjectArrayList<int[]>> patterns = new Object2ObjectOpenCustomHashMap<>(new ArrayLongHashStrategy());

        final int maxInputs = maxPorts - 1;
        EnumThread[] threads = new EnumThread[THREAD_COUNT];
        final AtomicInteger live_threads = new AtomicInteger(THREAD_COUNT);

        for (int i = 0; i < THREAD_COUNT; i++) {
            final int thread_id = i;
            threads[i] = new EnumThread(thread_id, live_threads, g, k, maxInputs, expandAfterFlag);
        }

        for (int i = 0; i < THREAD_COUNT; i++) {
            threads[i].start();
        }

        for (int i = 0; i < THREAD_COUNT; i++) {
            try {
                threads[i].join();
            } catch (InterruptedException ex) {
                Logger.getLogger(this.getClass().getName()).log(Level.SEVERE, null, ex);
            }
        }

        //Merge results
        //(we can safely merge since we joined with each other thread)
        //(So no volatile/sync required on those fields)
        merge(threads, patterns);

        return patterns;
    }

    //TODO: Can be done in O(log(n)) steps if it is a significant cost. (by combining 2 subPatterns, in log(n) steps by /2 threads each iteration)
    private void merge(EnumThread[] threads, Object2ObjectOpenCustomHashMap<long[], ObjectArrayList<int[]>> patterns) {
        for (int i = 0; i < threads.length; i++) {
            for (Object2ObjectMap.Entry<long[], ObjectArrayList<int[]>> p : Object2ObjectMaps.fastIterable(threads[i].subPatterns)) {
                patterns.merge(p.getKey(), p.getValue(), (v1, v2) -> merge2Arrays(v1, v2));
            }
        }

        for (int i = 0; i < threads.length; i++) {
            for (Object2ObjectMap.Entry<long[], ObjectArrayList<StateSingleOutput>> p : Object2ObjectMaps.fastIterable(threads[i].subExpandableStates)) {
                expandableStates.merge(p.getKey(), p.getValue(), (v1, v2) -> merge2StateArrays(v1, v2));
            }
        }
    }

    /**
     * Add all elements in newV to old.
     *
     * @param old The list to add to.
     * @param newV The list of elements to add to {@code old}.
     * @return {@code old}
     */
    private static ObjectArrayList<int[]> merge2Arrays(ObjectArrayList<int[]> old, ObjectArrayList<int[]> newV) {
        old.addAll(newV);
        return old;
    }

    /**
     * Adds the first element of newV to old.
     *
     * @param old The list to add an element to.
     * @param newV A list of which to get the first element.
     * @return {@code old}
     */
    private static ObjectArrayList<StateSingleOutput> mergeStateArrays(ObjectArrayList<StateSingleOutput> old, ObjectArrayList<StateSingleOutput> newV) {
        old.add(newV.get(0));
        return old;
    }

    /**
     * Add all elements in newV to old.
     *
     * @param old The list to add to.
     * @param newV The list of elements to add to {@code old}.
     * @return {@code old}
     */
    private static ObjectArrayList<StateSingleOutput> merge2StateArrays(ObjectArrayList<StateSingleOutput> old, ObjectArrayList<StateSingleOutput> newV) {
        old.addAll(newV);
        return old;
    }

    private final AtomicInteger nextNb;

    /**
     * Get the next unexpanded single node {@link StateSingleOutput}. This can
     * be called concurrently since it is based on an {@link AtomicInteger}.
     *
     * @param g The graph structure for context.
     * @return The next {@link StateSingleOutput} that has to be expanded. null
     * if there is no next {@link StateSingleOutput}.
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
        return "MultiBackTrackEnumerator";
    }

}
