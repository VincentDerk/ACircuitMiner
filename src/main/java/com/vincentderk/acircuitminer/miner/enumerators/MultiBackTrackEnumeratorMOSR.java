package com.vincentderk.acircuitminer.miner.enumerators;

import com.google.common.base.Stopwatch;
import com.vincentderk.acircuitminer.miner.util.ArrayLongHashStrategy;
import com.vincentderk.acircuitminer.miner.Graph;
import com.vincentderk.acircuitminer.miner.StateMultiOutput;
import com.vincentderk.acircuitminer.miner.canonical.CodeOccResult;
import com.vincentderk.acircuitminer.miner.canonical.EdgeCanonical;
import com.vincentderk.acircuitminer.miner.util.Misc;
import com.vincentderk.acircuitminer.miner.util.Utils;
import com.vincentderk.acircuitminer.miner.util.comparators.EntryProfitStateMOSRCom;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMaps;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenCustomHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The same as {@link MultiBackTrackEnumerator} but now finding occurrences with
 * Multiple Output but still a Single Root (MOSR). Compared to the occurrences
 * found in {@link MultiBackTrackEnumerator}, some nodes of those found
 * occurrences can now have an additional externally-outgoing edge that provides
 * another output.
 *
 * <p>
 * <b>Beware, these occurrences may not all be valid in that an output can be
 * (indirectly) required as an input of itself.</b>
 *
 * @author Vincent Derkinderen
 * @version 2.0
 */
public class MultiBackTrackEnumeratorMOSR implements ExpandableEnumerator {

    /**
     * Creates a new {@link Enumerator} with an empty
     * {@link #getExpandableStates() expandableStates}.
     *
     * @param threadCount The amount of threads this enumerator should use.
     */
    public MultiBackTrackEnumeratorMOSR(int threadCount) {
        THREAD_COUNT = threadCount;
        nextNb = new AtomicInteger(0);
        expandableStates = new Object2ObjectOpenCustomHashMap(new ArrayLongHashStrategy());
    }

    /**
     * Create an enumerator instance that can enumerate over the given graph.
     * The amount of threads used is determined by the amount of cores using
     * {@link java.lang.Runtime#availableProcessors()}.
     *
     */
    public MultiBackTrackEnumeratorMOSR() {
        THREAD_COUNT = Runtime.getRuntime().availableProcessors();
        nextNb = new AtomicInteger(0);
        expandableStates = new Object2ObjectOpenCustomHashMap(new ArrayLongHashStrategy());
    }

    /**
     * Get the encountered states that can still be expanded. This is only
     * correct right after calling {@link #enumerate(Graph, int, int)} or after
     * calling {@link #enumerate(Graph, int[], boolean, int, int)}.
     * <p>
     * Beware, clone the result when modifications are required. Modifying the
     * returned object can affect further calls. Further calls to this
     * Enumerator object can also affect the object that this method returned.
     *
     * @return The states that can still be expanded if more ports are allowed.
     */
    @Override
    public Object2ObjectOpenCustomHashMap<long[], ObjectArrayList<StateMultiOutput>> getExpandableStates() {
        return expandableStates;
    }

    /**
     * The amount of threads this enumerator should use.
     */
    private final int THREAD_COUNT;
    private final Object2ObjectOpenCustomHashMap<long[], ObjectArrayList<StateMultiOutput>> expandableStates;

    @Override
    public Object2ObjectOpenCustomHashMap<long[], ObjectArrayList<int[]>> enumerate(Graph g, int k, int maxPorts) {
        return enumerateComplete(g, k, maxPorts, false);
    }

    /**
     * A thread that can perform the complete enumeration.
     */
    private class EnumThread extends Thread {

        EnumThread(int thread_id, AtomicInteger live_threads, Graph g, int k, int maxPorts, boolean expandAfterFlag) {
            this.thread_id = thread_id;
            this.live_threads = live_threads;
            this.g = g;
            this.k = k;
            this.maxPorts = maxPorts;
            this.expandAfterFlag = expandAfterFlag;
        }

        final int thread_id;
        final AtomicInteger live_threads;
        Object2ObjectOpenCustomHashMap<long[], ObjectArrayList<int[]>> subPatterns;
        Object2ObjectOpenCustomHashMap<long[], ObjectArrayList<StateMultiOutput>> subExpandableStates;

        final Graph g;
        final int k;
        final int maxPorts;
        final boolean expandAfterFlag;

        @Override
        public void run() {
            subPatterns = enumerate_aux(g, k, maxPorts, expandAfterFlag);
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
         * @param maxPorts The maximum amount of ports that an occurrence ({link
         * State}) may have.
         * @param expandAfterFlag Denotes whether the enumerator should keep
         * track of the expandableStates. When set to true, a list of expandable
         * occurrences is kept such that they can later be retrieved
         * ({@link #getExpandableStates()}) and expanded further. If this is the
         * last enumeration of the process and you do not want to keep track of
         * the further expandable occurrences, set to false. Otherwise use true.
         * @return Mapping of pattern -> list of the roots of the found
         * occurrences of that pattern.
         */
        private Object2ObjectOpenCustomHashMap<long[], ObjectArrayList<int[]>> enumerate_aux(Graph g, int k, int maxPorts, boolean expandAfterFlag) {
            this.subExpandableStates = new Object2ObjectOpenCustomHashMap(new ArrayLongHashStrategy());
            Object2ObjectOpenCustomHashMap<long[], ObjectArrayList<int[]>> patterns = new Object2ObjectOpenCustomHashMap<>(new ArrayLongHashStrategy());
            StateMultiOutput base;

            ArrayDeque<StateMultiOutput> stack = new ArrayDeque<>();
            ArrayDeque<Integer> indexStack = new ArrayDeque<>();
            while ((base = getSingleState(g)) != null) {
                stack.push(base);
                indexStack.push(0);
                while (!stack.isEmpty()) {
                    StateMultiOutput c_state = stack.peek();
                    int expandIndex = indexStack.pop();

                    if (c_state.expandable.length <= expandIndex) {
                        /* finished expanding this state */
                        stack.pop();
                    } else {
                        /* Continue expanding */
                        indexStack.push(expandIndex + 1);
                        StateMultiOutput expanded = c_state.expand(g, expandIndex);
                        CodeOccResult codeOcc = null;

                        if (expanded.outputNodes.length + expanded.expandable.length + expanded.unexpandable.length <= maxPorts) { //Count #Inputs
                            /* Pattern occurrence count */
                            codeOcc = expanded.getCodeOcc(g);
                            if (codeOcc.inputCount + expanded.outputNodes.length <= maxPorts) {
                                //System.out.println("Found " + EdgeCanonical.printCode(codeOcc.code) + " and codeOcc count " + codeOcc.inputCount + " and other " + (expanded.expandable.length + expanded.unexpandable.length));
                                ObjectArrayList<int[]> singleObj = new ObjectArrayList<>();
                                singleObj.add(codeOcc.lastVerticesOrdered); //vertices of expanded sorted in assigned order.
                                patterns.merge(codeOcc.code, singleObj, (v1, v2) -> merge2AddAll(v1, v2));
                            }
                        }

                        /* continue expanding? */
                        if (expanded.vertices.length < k) {
                            stack.push(expanded);
                            indexStack.push(0);
                        } else if (expandAfterFlag && codeOcc != null) {
                            //Note: This excludes current invalid occurrences (intermediate output / maxInputs)
                            subExpandableStates.merge(codeOcc.code, new ObjectArrayList(new StateMultiOutput[]{expanded}), (v1, v2) -> mergeAddFirst(v1, v2));
                        }

                    }
                }
            }
            return patterns;
        }
    }

    /**
     * Performs a complete enumeration to find all occurrences (induced
     * connected subgraphs) with one output (root), a maximum of
     * {@code maxPorts} ports (amount of input + output nodes) and a maximum
     * size of k (amount of internal nodes).
     *
     * @param g The graph to search in.
     * @param k The maximum pattern size (amount of internal nodes).
     * @param maxPorts The maximum amount of ports the occurrence has. This is
     * the maximum amount of in- and output nodes the found occurrence may have
     * in g.
     * @param expandAfterFlag Whether to keep track of the expandable states.
     * True will result in a valid {@link #getExpandableStates()}.
     * @return A Mapping from the found patterns to a list of their occurrences.
     * An occurrence is represented by an array of node id's (indices in the
     * graph ({@code g}) structure.
     */
    protected Object2ObjectOpenCustomHashMap<long[], ObjectArrayList<int[]>> enumerateComplete(Graph g, int k, int maxPorts, boolean expandAfterFlag) {
        expandableStates.clear();
        nextNb.set(0);

        Object2ObjectOpenCustomHashMap<long[], ObjectArrayList<int[]>> patterns = new Object2ObjectOpenCustomHashMap<>(new ArrayLongHashStrategy());
        EnumThread[] threads = new EnumThread[THREAD_COUNT];
        final AtomicInteger live_threads = new AtomicInteger(THREAD_COUNT);

        for (int i = 0; i < THREAD_COUNT; i++) {
            final int thread_id = i;
            threads[i] = new EnumThread(thread_id, live_threads, g, k, maxPorts, expandAfterFlag);
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
        for (EnumThread thread : threads) {
            for (Object2ObjectMap.Entry<long[], ObjectArrayList<int[]>> p : Object2ObjectMaps.fastIterable(thread.subPatterns)) {
                patterns.merge(p.getKey(), p.getValue(), (v1, v2) -> merge2AddAll(v1, v2));
            }
        }

        for (EnumThread thread : threads) {
            for (Object2ObjectMap.Entry<long[], ObjectArrayList<StateMultiOutput>> p : Object2ObjectMaps.fastIterable(thread.subExpandableStates)) {
                expandableStates.merge(p.getKey(), p.getValue(), (v1, v2) -> merge2AddAll(v1, v2));
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
    private static <T> ObjectArrayList<T> mergeAddFirst(ObjectArrayList<T> old, ObjectArrayList<T> newV) {
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
    private static <T> ObjectArrayList<T> merge2AddAll(ObjectArrayList<T> old, ObjectArrayList<T> newV) {
        old.addAll(newV);
        return old;
    }

    private final AtomicInteger nextNb;

    /**
     * Get the next unexpanded single node {@link StateMultiOutput}. This can be
     * called concurrently since it is based on an {@link AtomicInteger}.
     *
     * @param g The graph structure for context.
     * @return The next {@link StateMultiOutput} that has to be expanded. null
     * if there is no next {@link StateMultiOutput}.
     */
    protected StateMultiOutput getSingleState(Graph g) {
        int currentNb = nextNb.getAndIncrement();
        if (currentNb >= g.inc.length) { //Check whether there is still a node.
            return null;
        }
        if (g.inc[currentNb].length == 0) { //in case of literal node
            return getSingleState(g);
        } else {
            int[] outputNodes = {currentNb};
            int[] vertices = {currentNb};
            int[] expandable = g.expandable_children[currentNb];
            int[] unexpandable = g.unexpandable_children[currentNb];

            return new StateMultiOutput(vertices, expandable, unexpandable, outputNodes);
        }
    }

    @Override
    public String toString() {
        return "MultiBackTrackEnumeratorMOSR";
    }

    // --------------------------------------------------------------------------
    // Start of 'deepening methods' that continue the (incomplete search) to bigger patterns.
    // --------------------------------------------------------------------------
    /**
     * Performs an (in)complete enumeration by first performing a complete
     * enumeration upto the first k value and then heuristically expanding upon
     * these results.
     *
     * <ul>
     * <li>Execute the algorithm
     * ({@link MultiBackTrackEnumerator#enumerate(Graph, int, int)}) upto size
     * {@code k[0]}.</li>
     * <li>pick the {@code xBest} that had not been extended due to size and
     * expand these till size {@code k[1]}.</li>
     * <li>For the iteration upto size {@code k[2]}, the {@code xBest} from the
     * previous iteration are used. The difference with the previous iteration
     * is that the previous iteration extends the {@code xBest} patterns of the
     * occurrences that were not expanded in the first iteration due to size.
     * While for this iteration, and the ones after, the {@code xBest} of all
     * valid occurrences of the previous iteration are considered regardless of
     * whether they are maximum size. This is the case for
     * {@code k[2],k[3],...}</li>
     * <li>Repeat upto {@code k[max]}.
     * </ul>
     *
     * @param g The graph to mine the occurrences in.
     * @param k The sizes to mine upto. Size of a pattern is determined by the
     * amount of operations in it.
     * @param maxPorts The maximum amount of input+output ports. Note, all found
     * patterns will only have 1 output port.
     * @param xBest The x best patterns to select for the next iteration.
     * @param verbose Prints more information.
     * @return Mapping of patterns to lists of occurrences of that pattern.
     */
    @Override
    public Object2ObjectOpenCustomHashMap<long[], ObjectArrayList<int[]>> enumerate(Graph g, int[] k, int maxPorts, int xBest, final boolean verbose) {
        if (k.length == 1) {
            return enumerate(g, k[0], maxPorts);
        }

        Stopwatch stopwatch = null;
        expandableStates.clear();
        EntryProfitStateMOSRCom comparator = new EntryProfitStateMOSRCom(false);

        /* First enumeration */
        if (verbose) {
            stopwatch = Stopwatch.createStarted();
            System.out.println("--------------------------------------------------------");
            System.out.println("First iteration k=" + k[0]);
            stopwatch.reset().start();
        }

        Object2ObjectOpenCustomHashMap<long[], ObjectArrayList<int[]>> patternsMap = enumerateComplete(g, k[0], maxPorts, true);

        if (verbose && stopwatch != null) {
            System.out.printf("enumerated and found " + patternsMap.size() + " patterns in %s secs using MultiBackTrackEnumerator.\n", stopwatch.elapsed(TimeUnit.SECONDS));
            System.out.println("");
            Misc.printMemory();
            System.out.println("");
            System.out.println("Printing results...");
            Utils.writeResults(patternsMap, true);
        }

        /* Second enumeration */
        Map.Entry<long[], ObjectArrayList<StateMultiOutput>>[] interestingStates = null;
        if (k.length > 1) {
            interestingStates = Utils.filter(expandableStates, comparator, xBest);
        }

        for (int i = 1; i < k.length; i++) {
            boolean expandAfterFlag = (i + 1 != k.length);

            /* Previously filtered */
            if (verbose && stopwatch != null) {
                System.out.println("--------------------------------------------------------");
                System.out.println("Iteration " + (i + 1) + " k=" + k[i]);
                System.out.println("Expanding (occurrenceCount) code:");
                for (Map.Entry<long[], ObjectArrayList<StateMultiOutput>> entry : interestingStates) {
                    System.out.println("(" + entry.getValue().size() + ") " + EdgeCanonical.printCode(entry.getKey()));
                }
                System.out.println("");
                stopwatch.reset().start();
            }

            //Expands and adds result to patternsMap
            expandSelectedPatterns(g, patternsMap, interestingStates, k[i], maxPorts, expandAfterFlag, k[i - 1]);

            if (verbose && stopwatch != null) {
                System.out.printf("enumerated and found " + patternsMap.size() + " patterns in an additional %s secs using BackTrackEnumerator.\n", stopwatch.elapsed(TimeUnit.SECONDS));
                System.out.println("");
                Misc.printMemory();
            }

            if (i + 1 < k.length) {
                interestingStates = Utils.filter(getExpandableStates(), comparator, xBest);
            }
        }

        return patternsMap;
    }

    /**
     * Used by several {@link EnumSecondaryThread threads} during the heuristic
     * deepening to keep track of the states (xBest patterns) to extend.
     */
    private Map.Entry<long[], ObjectArrayList<StateMultiOutput>>[] baseStates;

    /**
     * A thread that can perform the 'deepening' part of the enumeration. This
     * means it continues the enumeration based on existing expandable states.
     * <p>
     * Each thread will process a chunk of the (baseStates). baseStates is an
     * array where each array element (indirectly) contains a list of states
     * that are further expandable. All threads iterate over all the array
     * elements but each threads will only take a specific chunk of the list
     * associated with an array element. The chunk is calculated based on the
     * thread_id, the size of the list and the
     * {@link #THREAD_COUNT amount of threads}.
     *
     */
    private class EnumSecondaryThread extends Thread {

        EnumSecondaryThread(int thread_id, AtomicInteger live_threads, Graph g, int k,
                int maxPorts, boolean expandAfterFlag, int prevK) {
            this.thread_id = thread_id;
            this.live_threads = live_threads;
            this.g = g;
            this.k = k;
            this.maxPorts = maxPorts;
            this.expandAfterFlag = expandAfterFlag;
            this.prevK = prevK;
        }

        final int thread_id;
        final AtomicInteger live_threads;
        Object2ObjectOpenCustomHashMap<long[], ObjectArrayList<int[]>> subPatterns;
        Object2ObjectOpenCustomHashMap<long[], ObjectArrayList<StateMultiOutput>> subExpandableStates;
        final boolean expandAfterFlag;
        final int prevK;

        // The start index for each entry in baseStates.
        private int[] startIndex;
        // The chunk size of the given chunk of entry in baseStates. (chunk start given by startIndices).
        private int[] chunkSize;

        Graph g;
        int k;
        int maxPorts;

        @Override
        public void run() {
            /* Calculate the chunk startIndex and size for each list (array element) */
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
            subPatterns = enumerate_aux(g, maxPorts);

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
         * @param maxPorts The maximum amount of ports that an occurrence ({link
         * State}) may have.
         * @param expandAfterFlag Denotes whether the enumerator should keep
         * track of the expandableStates. When set to true, a list of expandable
         * occurrences is kept such that they can later be retrieved
         * ({@link #getExpandableStates()}) and expanded further. If this is the
         * last enumeration of the process and you do not want to keep track of
         * the further expandable occurrences, set to false. Otherwise use true.
         * @return Mapping of pattern -> list of the roots of the found
         * occurrences of that pattern.
         */
        public Object2ObjectOpenCustomHashMap<long[], ObjectArrayList<int[]>> enumerate_aux(Graph g, int maxPorts) {
            entryIndex = 0;
            currChunkIndex = 0;
            chunkEndIndex = 0;
            this.subExpandableStates = new Object2ObjectOpenCustomHashMap(new ArrayLongHashStrategy());

            Object2ObjectOpenCustomHashMap<long[], ObjectArrayList<int[]>> patterns = new Object2ObjectOpenCustomHashMap<>(new ArrayLongHashStrategy());
            StateMultiOutput base;

            ArrayDeque<StateMultiOutput> stack = new ArrayDeque<>();
            ArrayDeque<Integer> indexStack = new ArrayDeque<>();
            while ((base = getBaseState()) != null) {
                stack.push(base);
                indexStack.push(0);
                while (!stack.isEmpty()) {
                    StateMultiOutput c_state = stack.peek();
                    int expandIndex = indexStack.pop();

                    if (c_state.expandable.length <= expandIndex) {
                        /* finished expanding this state */
                        stack.pop();
                    } else {
                        /* Continue expanding */
                        indexStack.push(expandIndex + 1);
                        StateMultiOutput expanded = c_state.expand(g, expandIndex);
                        CodeOccResult codeOcc = null;

                        if (expanded.vertices.length > prevK
                                && expanded.outputNodes.length + expanded.expandable.length + expanded.unexpandable.length <= maxPorts) { //Count #Inputs (excl literals)
                            /* Pattern occurrence count */
                            codeOcc = expanded.getCodeOcc(g);
                            if (codeOcc.inputCount + expanded.outputNodes.length <= maxPorts) { //Count #Inputs (incl literals) + #outputs
                                ObjectArrayList<int[]> singleObj = new ObjectArrayList<>();
                                singleObj.add(codeOcc.lastVerticesOrdered); //vertices of expanded sorted in assigned order.
                                patterns.merge(codeOcc.code, singleObj, (v1, v2) -> mergeAddFirst(v1, v2));
                            }
                        }

                        /* continue expanding? */
                        if (expanded.vertices.length < k) {
                            stack.push(expanded);
                            indexStack.push(0);
                        }
                        if (expandAfterFlag && codeOcc != null && expanded.vertices.length > prevK) {
                            //Note: This excludes current invalid occurrences (intermediate output / maxInputs)
                            subExpandableStates.merge(codeOcc.code, new ObjectArrayList(new StateMultiOutput[]{expanded}), (v1, v2) -> mergeAddFirst(v1, v2));
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
         * @return The next {@link State} that has to be expanded. null if there
         * is no more next {@link State}.
         */
        public StateMultiOutput getBaseState() {
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

    /**
     * Extend the patternsMap by extending the occurrences of the baseStates.
     * Both {@link patternsMap} and {@link baseStates} will be modified.
     *
     * @param g The backend graph to expand in.
     * @param patternsMap The map of patterns to extend to. Will be modified.
     * @param baseStates The states which we want to expand. Will be modified.
     * @param k The maximum pattern size, equal to the amount of internal nodes
     * (so excluding input nodes).
     * @param maxPorts The maximum amount of input+output ports an occurrence
     * may have. Note, all found patterns will only have 1 output port.
     * @param expandAfterFlag Denotes whether the enumerator should keep track
     * of the expandableStates. If this is the last enumeration of the process,
     * set to false. Otherwise use true.
     * @param prevK The k (maximum pattern size) from which to start adding the
     * found occurrences to the patternsMap.
     */
    protected void expandSelectedPatterns(Graph g, Object2ObjectOpenCustomHashMap<long[], ObjectArrayList<int[]>> patternsMap,
            Map.Entry<long[], ObjectArrayList<StateMultiOutput>>[] baseStates, int k,
            int maxPorts, boolean expandAfterFlag, int prevK) {
        this.baseStates = baseStates;

        if (baseStates.length == 0) {
            System.out.println("No base states to expand!");
        } else {
            MultiBackTrackEnumeratorMOSR.EnumSecondaryThread[] threads = new EnumSecondaryThread[THREAD_COUNT];
            final AtomicInteger live_threads = new AtomicInteger(THREAD_COUNT);

            for (int i = 0; i < THREAD_COUNT; i++) {
                final int thread_id = i + 1;
                threads[i] = new EnumSecondaryThread(thread_id, live_threads, g, k, maxPorts, expandAfterFlag, prevK);
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

    //TODO: Can be done in O(log(n)) steps if it is a significant cost. (by combining 2 subPatterns, in log(n) steps by /2 threads each iteration)
    private void merge(EnumSecondaryThread[] threads, Object2ObjectOpenCustomHashMap<long[], ObjectArrayList<int[]>> patterns) {
        for (EnumSecondaryThread thread : threads) {
            for (Object2ObjectMap.Entry<long[], ObjectArrayList<int[]>> p : Object2ObjectMaps.fastIterable(thread.subPatterns)) {
                patterns.merge(p.getKey(), p.getValue(), (v1, v2) -> merge2AddAll(v1, v2));
            }
        }

        for (EnumSecondaryThread thread : threads) {
            for (Object2ObjectMap.Entry<long[], ObjectArrayList<StateMultiOutput>> p : Object2ObjectMaps.fastIterable(thread.subExpandableStates)) {
                expandableStates.merge(p.getKey(), p.getValue(), (v1, v2) -> merge2AddAll(v1, v2));
            }
        }
    }

}
