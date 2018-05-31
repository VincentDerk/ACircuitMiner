package com.vincentderk.acircuitminer.miner.enumerators;

import com.vincentderk.acircuitminer.miner.util.ArrayLongHashStrategy;
import com.vincentderk.acircuitminer.miner.State;
import com.vincentderk.acircuitminer.miner.Graph;
import com.vincentderk.acircuitminer.miner.canonical.CodeOccResult;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMaps;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenCustomHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.ArrayDeque;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The same as {@link MultiBackTrackEnumerator} but as a
 * {@link SecondaryEnumerator} it expands a given collection of
 * {@link State states} instead of expanding all nodes in the {@link Graph}.
 * <p>
 * Of the given {@link State states} that it has to expand, it only adds those
 * that are larger than {@code prevK}. In regards to the next expandable
 * {@link State states}: It stores any found {@link State} with a valid pattern
 * that is larger than {@code prevK}.
 *
 * @author Vincent Derkinderen
 * @version 1.0
 *
 */
public class SecondaryMultiBackTrackEnumerator implements SecondaryEnumerator {

    /**
     * Create an enumerator instance that can enumerate over the given
     * {@link Graph}.
     *
     * @param g The graph where to enumerate over by using.
     * @param threadCount The amount of threads to use.
     * {@link #expandSelectedPatterns(Object2ObjectOpenCustomHashMap, java.util.Map.Entry<long[],ObjectArrayList<State>>[],
     * int, int, boolean, int)}.
     */
    public SecondaryMultiBackTrackEnumerator(Graph g, int threadCount) {
        THREAD_COUNT = threadCount;
        this.g = g;
        expandableStates = new Object2ObjectOpenCustomHashMap(new ArrayLongHashStrategy());
    }

    /**
     * Create an enumerator instance that can enumerate over the given graph.
     *
     * @param g The graph where to enumerate over by using
     * {@link #expandSelectedPatterns(Object2ObjectOpenCustomHashMap, java.util.Map.Entry<long[],ObjectArrayList<State>>[],
     * int, int, boolean, int)}.
     */
    public SecondaryMultiBackTrackEnumerator(Graph g) {
        THREAD_COUNT = Runtime.getRuntime().availableProcessors();
        this.g = g;
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
    private Entry<long[], ObjectArrayList<State>>[] baseStates;
    private final Graph g;
    private final int THREAD_COUNT;

    private class EnumThread extends Thread {

        EnumThread(int thread_id, AtomicInteger live_threads, Graph g, int k,
                int maxInputs, boolean expandAfterFlag, int prevK) {
            this.thread_id = thread_id;
            this.live_threads = live_threads;
            this.g = g;
            this.k = k;
            this.maxInputs = maxInputs;
            this.expandAfterFlag = expandAfterFlag;
            this.prevK = prevK;
        }

        final int thread_id;
        final AtomicInteger live_threads;
        Object2ObjectOpenCustomHashMap<long[], ObjectArrayList<int[]>> subPatterns;
        Object2ObjectOpenCustomHashMap<long[], ObjectArrayList<com.vincentderk.acircuitminer.miner.State>> subExpandableStates;
        final boolean expandAfterFlag;
        final int prevK;

        // The start index for each entry in baseStates.
        private int[] startIndex;
        // The chunk size of the given chunk of entry in baseStates. (chunk start given by startIndices).
        private int[] chunkSize;

        Graph g;
        int k;
        int maxInputs;

        @Override
        public void run() {

            chunkSize = new int[baseStates.length];
            startIndex = new int[baseStates.length];

            for (int i = 0; i < baseStates.length; i++) {
                int size = baseStates[i].getValue().size();
                int roundedChunkSize = size / THREAD_COUNT;

                if (thread_id != THREAD_COUNT) {
                    chunkSize[i] = roundedChunkSize;
                    startIndex[i] = roundedChunkSize * (thread_id - 1) - 1;
                } else {
                    chunkSize[i] = size - roundedChunkSize * (THREAD_COUNT - 1);
                    startIndex[i] = roundedChunkSize * (THREAD_COUNT - 1) - 1;
                }

            }

            currChunkIndex = startIndex[0];
            chunkEndIndex = startIndex[0] + chunkSize[0] + 1;
            subPatterns = enumerate_aux(g, maxInputs);

            System.out.printf("Thread %d finished. Found patterns: %d. %d threads remaining.\n", thread_id, subPatterns.size(), live_threads.decrementAndGet());
        }

        /**
         * Finds patterns and their occurrences in the given graph g by starting
         * from the given expandable {@link State states}. Each thread is
         * assigned a chunk of occurrences of each
         * {@link #baseStates base pattern}.
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
        public Object2ObjectOpenCustomHashMap<long[], ObjectArrayList<int[]>> enumerate_aux(Graph g, int maxInputs) {
            this.subExpandableStates = new Object2ObjectOpenCustomHashMap(new ArrayLongHashStrategy());
            Object2ObjectOpenCustomHashMap<long[], ObjectArrayList<int[]>> patterns = new Object2ObjectOpenCustomHashMap<>(new ArrayLongHashStrategy());
            com.vincentderk.acircuitminer.miner.State base;

            ArrayDeque<com.vincentderk.acircuitminer.miner.State> stack = new ArrayDeque<>();
            ArrayDeque<Integer> indexStack = new ArrayDeque<>();
            while ((base = getBaseState()) != null) {
                stack.push(base);
                indexStack.push(0);
                while (!stack.isEmpty()) {
                    com.vincentderk.acircuitminer.miner.State c_state = stack.peek();
                    int expandIndex = indexStack.pop();

                    if (c_state.expandable.length <= expandIndex) {
                        /* finished expanding this state */
                        stack.pop();
                    } else {
                        /* Continue expanding */
                        indexStack.push(expandIndex + 1);
                        com.vincentderk.acircuitminer.miner.State expanded = c_state.expand(g, expandIndex);
                        CodeOccResult codeOcc = null;

                        if (expanded.vertices.length > prevK && expanded.interNode == -1
                                && expanded.expandable.length + expanded.unexpandable.length <= maxInputs) { //Count #Inputs (excl literals)
                            /* Pattern occurrence count */
                            codeOcc = expanded.getCodeOcc(g);
                            if (codeOcc.inputCount <= maxInputs) { //Count #Inputs (incl literals)
                                ObjectArrayList<int[]> singleObj = new ObjectArrayList<>();
                                singleObj.add(codeOcc.lastVerticesOrdered); //vertices of expanded sorted in assigned order.
                                patterns.merge(codeOcc.code, singleObj, (v1, v2) -> mergeArray(v1, v2));
                            }
                        }

                        /* continue expanding? */
                        if (expanded.vertices.length < k) {
                            stack.push(expanded);
                            indexStack.push(0);
                        }
                        if (expandAfterFlag && codeOcc != null && expanded.vertices.length > prevK) {
                            //Note: This excludes current invalid occurrences (intermediate output / maxInputs)
                            subExpandableStates.merge(codeOcc.code, new ObjectArrayList(new com.vincentderk.acircuitminer.miner.State[]{expanded}), (v1, v2) -> mergeStateArrays(v1, v2));
                        }

                    }
                }
            }
            return patterns;
        }

        private int entryIndex; //Index of entry to retrieve next task.
        private int currChunkIndex; //Index of current task
        private int chunkEndIndex; //The last index of the current chunk + 1.

        /**
         * Get the next unexpanded base state.
         *
         * @return The next State that has to be expanded. null if there is no
         * more next State.
         */
        public com.vincentderk.acircuitminer.miner.State getBaseState() {
            currChunkIndex++;

            if (currChunkIndex >= chunkEndIndex) {
                entryIndex++;
                if (entryIndex >= baseStates.length) {
                    return null;
                }
                currChunkIndex = startIndex[entryIndex] + 1;
                chunkEndIndex = startIndex[entryIndex] + chunkSize[entryIndex] + 1;
            }

            return baseStates[entryIndex].getValue().get(currChunkIndex);
        }
    }

    @Override
    public void expandSelectedPatterns(Object2ObjectOpenCustomHashMap<long[], ObjectArrayList<int[]>> patternsMap,
            Entry<long[], ObjectArrayList<State>>[] baseStates, int k,
            int maxInputs, boolean expandAfterFlag, int prevK) {
        this.baseStates = baseStates;

        if (baseStates.length == 0) {
            System.out.println("No base states to expand!");
        } else {
            EnumThread[] threads = new EnumThread[THREAD_COUNT];
            final AtomicInteger live_threads = new AtomicInteger(THREAD_COUNT);

            for (int i = 0; i < THREAD_COUNT; i++) {
                final int thread_id = i + 1;
                threads[i] = new EnumThread(thread_id, live_threads, g, k, maxInputs, expandAfterFlag, prevK);
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
            merge(threads, patternsMap);
        }
    }

    //TODO: Can be done in log(n) steps if it is a significant cost. (by combining 2 subPatterns, in log(n) steps by /2 threads each iteration)
    private void merge(EnumThread[] threads, Object2ObjectOpenCustomHashMap<long[], ObjectArrayList<int[]>> patterns) {
        for (int i = 0; i < threads.length; i++) {
            for (Object2ObjectMap.Entry<long[], ObjectArrayList<int[]>> p : Object2ObjectMaps.fastIterable(threads[i].subPatterns)) {
                patterns.merge(p.getKey(), p.getValue(), (v1, v2) -> merge2Arrays(v1, v2));
            }
        }

        for (int i = 0; i < threads.length; i++) {
            for (Object2ObjectMap.Entry<long[], ObjectArrayList<State>> p : Object2ObjectMaps.fastIterable(threads[i].subExpandableStates)) {
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
     * Add all elements in newV to old.
     *
     * @param old The list to add to.
     * @param newV The list of elements to add to {@code old}.
     * @return {@code old}
     */
    private static ObjectArrayList<State> merge2StateArrays(ObjectArrayList<State> old, ObjectArrayList<State> newV) {
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
    private static ObjectArrayList<State> mergeStateArrays(ObjectArrayList<State> old, ObjectArrayList<State> newV) {
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
    private static ObjectArrayList<int[]> mergeArray(ObjectArrayList<int[]> old, ObjectArrayList<int[]> newV) {
        old.add(newV.get(0));
        return old;
    }

    @Override
    public String toString() {
        return "SecondaryMultiBackTrackEnumerator";
    }

}
