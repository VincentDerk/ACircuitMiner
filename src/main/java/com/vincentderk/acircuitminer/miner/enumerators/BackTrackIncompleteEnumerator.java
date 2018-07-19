package com.vincentderk.acircuitminer.miner.enumerators;

import com.google.common.base.Stopwatch;
import com.vincentderk.acircuitminer.miner.util.ArrayLongHashStrategy;
import com.vincentderk.acircuitminer.miner.Graph;
import com.vincentderk.acircuitminer.miner.StateExpandable;
import com.vincentderk.acircuitminer.miner.StateSingleOutput;
import com.vincentderk.acircuitminer.miner.canonical.CodeOccResult;
import com.vincentderk.acircuitminer.miner.canonical.EdgeCanonical;
import com.vincentderk.acircuitminer.miner.util.Utils;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenCustomHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryUsage;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 *
 * An enumerator that finds {@link StateSingleOutput} occurrences with only one
 * output and a maximum amount of inputs. (<b>Enumeration,
 * {@link #enumerate(com.vincentderk.acircuitminer.miner.Graph, int, int) enumerate}</b>)
 * It does this by finding all occurrences that have a certain node as root.
 * This is then repeated for each node in the given graph. (<b>Expansion,
 * {@link StateSingleOutput#expand(Graph, int) expand}</b>) When expanding an
 * occurrence into a bigger occurrence, we only need to consider the children.
 * This is due to the fact that we only search for induced, connected subgraphs.
 *
 * <p>
 * The expanding of an occurrence into another occurrence is done by a
 * depth-first approach. At each point, it keeps track of which node it
 * expanded. When later a backtrack occurs, another index (node) is expanded
 * instead. To prevent finding the same occurrence twice (by expanding in a
 * different order), the {@link StateExpandable} object stores a list of
 * {@link StateExpandable#unexpandable unexpandable nodes} that should not be
 * expanded anymore in the current decision path. This is based on the work of
 * "Automatic enumeration of all connected subgraphs" by Rücker et al.
 *
 * <p>
 * <b>Incomplete enumeration: heuristic deepening</b>
 * This class also provides
 * {@link #enumerate(Graph, int[], boolean, int, int) a method} that, after the
 * complete search, continues the search in a heuristic manner to also find some
 * occurrences of larger patterns. First, the complete search is executed. That
 * is the first iteration. While doing this, it also keeps track of the states
 * that it can further expand in the next iteration. This is an incomplete
 * search as it does not consider all states found so far.
 *
 * <p>
 * The expandable states that are tracked are those that were occurrences of
 * valid patterns (satisfied input and output restrictions) but reached the
 * maximum amount of internal nodes. This is incomplete since there might be
 * occurrences that only temporarily not satisfy a restriction.
 *
 * <p>
 * <b>Occurrences found: induced, connected subgraphs with one root and a
 * maximum of k nodes.</b>
 *
 * <p>
 * <b>Restrictions:</b>
 * <ul>
 * <li>No node has more than one connection to another node.</li>
 * <li>The labels (operations) of the used {@link Graph graphs} are restricted
 * to {@link Graph#SUM},{@link Graph#PRODUCT} and {@link Graph#INPUT}.</li>
 * <li>k (maximum pattern/occurrence size) has a highest possible value defined
 * by {@code (k,k-1) <} {@link Long#MAX_VALUE} - {@link Graph#HIGHEST_OP} where
 * (k,k-1) represents a Long value with the highest 32 bits as k and the
 * lowest 32 bits as k-1.</li>
 * <li>For each found occurrence: {@code nb_of_edges + nb_of_nodes <} {@link Integer#MAX_VALUE}.</li>
 * </ul>
 *
 * @author Vincent Derkinderen
 * @version 2.0
 */
public class BackTrackIncompleteEnumerator implements Enumerator {

    /**
     * Creates a new {@link Enumerator} with an empty
     * {@link #getExpandableStates() expandableStates}.
     */
    public BackTrackIncompleteEnumerator() {
        expandableStates = new Object2ObjectOpenCustomHashMap(new ArrayLongHashStrategy());
    }

    @Override
    public Object2ObjectOpenCustomHashMap<long[], ObjectArrayList<int[]>> enumerate(Graph g, int k, int maxPorts) {
        return enumerateComplete(g, k, maxPorts, false);
    }

    /**
     * Get the encountered states that can still be expanded. This is only
     * correct right after calling
     * {@link #enumerateComplete(com.vincentderk.acircuitminer.miner.Graph, int, int, boolean)}
     * with {@code expandAfterFlag = true} or after calling
     * {@link #expandSelectedPatterns(it.unimi.dsi.fastutil.objects.Object2ObjectOpenCustomHashMap, java.util.Map.Entry<long[],it.unimi.dsi.fastutil.objects.ObjectArrayList<com.vincentderk.acircuitminer.miner.StateSingleOutput>>[],
     * int, int, boolean, int)}.
     * <p>
     * Beware, clone the result when modifications are required. Modifying the
     * returned object can affect further calls. Further calls to this
     * Enumerator object can also affect the object that this method returned.
     *
     * @return The states that can still be expanded.
     */
    public Object2ObjectOpenCustomHashMap<long[], ObjectArrayList<StateSingleOutput>> getExpandableStates() {
        return this.expandableStates;
    }

    private final Object2ObjectOpenCustomHashMap<long[], ObjectArrayList<StateSingleOutput>> expandableStates;

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
        final int maxInputs = maxPorts - 1;
        expandableStates.clear();
        nextNb = 0;
        Object2ObjectOpenCustomHashMap<long[], ObjectArrayList<int[]>> patterns = new Object2ObjectOpenCustomHashMap<>(new ArrayLongHashStrategy());
        StateSingleOutput base;
        ArrayDeque<StateSingleOutput> stack = new ArrayDeque<>();
        ArrayDeque<Integer> indexStack = new ArrayDeque<>();

        while ((base = getSingleState(g)) != null) {
            stack.push(base);
            indexStack.push(0);
            while (!stack.isEmpty()) {
                StateSingleOutput c_state = stack.peek();
                int expandIndex = indexStack.pop();

                if (c_state.expandable.length <= expandIndex) {
                    /* finished expanding this state */
                    stack.pop();
                } else {
                    /* Continue expanding */
                    indexStack.push(expandIndex + 1);
                    StateSingleOutput expanded = c_state.expand(g, expandIndex);
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
                        expandableStates.merge(codeOcc.code, new ObjectArrayList(new com.vincentderk.acircuitminer.miner.StateSingleOutput[]{expanded}), (v1, v2) -> mergeStateArray(v1, v2));
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
    private static ObjectArrayList<StateSingleOutput> mergeStateArray(ObjectArrayList<StateSingleOutput> old, ObjectArrayList<StateSingleOutput> newV) {
        old.add(newV.get(0));
        return old;
    }

    /**
     * Used by {@link #getSingleState(Graph)} to keep track of the next node to
     * expand. Initially, before expanding, this value should/will be set to 0.
     */
    protected int nextNb;

    /**
     * Get the next unexpanded {@link StateSingleOutput}. The resulting state
     * will only have 1 element in {@code vertices} along with the correct
     * {@code (un)expandable} children.
     *
     * <p>
     * This is used during the enumeration to iterate over all possible
     * {@link StateSingleOutput StateSingleOutputs} in {@code g} that consist of
     * only 1 node ({@link StateSingleOutput#vertices vertices}).
     *
     * @param g The graph structure to retrieve the information from. The same
     * {@code g} has to be used for all calls.
     * @return The next State that has to be expanded. null if there is no more
     * next State.
     * @see #nextNb
     */
    protected StateSingleOutput getSingleState(Graph g) {
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

            return new StateSingleOutput(currentNb, vertices, expandable, unexpandable, -1);
        }
    }

    @Override
    public String toString() {
        return "BackTrackEnumerator";
    }

    // --------------------------------------------------------------------------
    // Start of 'deepening methods' that continue the (incomplete search) to bigger patterns.
    // --------------------------------------------------------------------------
    /**
     * Performs an (in)complete enumeration.
     * <ul>
     * <li>Execute the algorithm ({@link BackTrackEnumerator}) upto size
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
     * @param verbose Prints more
     * @param maxPorts The maximum amount of input+output ports. Note, all found
     * patterns will only have 1 output port.
     * @param xBest The x best patterns to select for the next iteration.
     * @return Mapping of patterns to lists of occurrences of that pattern.
     */
    public Object2ObjectOpenCustomHashMap<long[], ObjectArrayList<int[]>> enumerate(Graph g, int[] k, boolean verbose, int maxPorts, int xBest) {
        if (k.length == 1) {
            return enumerate(g, k[0], maxPorts);
        }

        Stopwatch stopwatch = null;
        expandableStates.clear();

        /* First enumeration */
        if (verbose) {
            stopwatch = Stopwatch.createStarted();
            System.out.println("--------------------------------------------------------");
            System.out.println("First iteration k=" + k[0]);
            stopwatch.reset().start();
        }

        Object2ObjectOpenCustomHashMap<long[], ObjectArrayList<int[]>> patternsMap = enumerateComplete(g, k[0], maxPorts, true);

        if (verbose) {
            System.out.printf("enumerated and found " + patternsMap.size() + " patterns in %s secs using BackTrackIncompleteEnumerator.\n", stopwatch.elapsed(TimeUnit.SECONDS));
            System.out.println("");
            printMemory();
            System.out.println("");
            System.out.println("Printing results...");
            Utils.writeResults(patternsMap, true);
        }

        /* Second enumeration */
        Map.Entry<long[], ObjectArrayList<StateSingleOutput>>[] interestingStates = null;
        if (k.length > 1) {
            interestingStates = Utils.filter(expandableStates, xBest);
        }

        for (int i = 1; i < k.length; i++) {
            boolean expandAfterFlag = (i + 1 != k.length);

            /* Previously filtered */
            if (verbose) {
                System.out.println("--------------------------------------------------------");
                System.out.println("Iteration " + (i + 1) + " k=" + k[i]);
                System.out.println("Expanding (occurrenceCount) code:");
                for (Map.Entry<long[], ObjectArrayList<StateSingleOutput>> entry : interestingStates) {
                    System.out.println("(" + entry.getValue().size() + ") " + EdgeCanonical.printCode(entry.getKey()));
                }
                System.out.println("");
                stopwatch.reset().start();
            }

            //Expands and adds result to patternsMap
            expandSelectedPatterns(g, patternsMap, interestingStates, k[i], maxPorts, expandAfterFlag, k[i - 1]);

            if (verbose) {
                System.out.printf("enumerated and found " + patternsMap.size() + " patterns in an additional %s secs using BackTrackEnumerator.\n", stopwatch.elapsed(TimeUnit.SECONDS));
                System.out.println("");
                printMemory();
            }

            if (i + 1 < k.length) {
                interestingStates = Utils.filter(getExpandableStates(), xBest);
            }
        }

        return patternsMap;
    }

    /**
     * Extend the patternsMap by extending the occurrences of the
     * expandablePatterns in the patternsMap. Both patternsMap and
     * expandablePatterns will be modified.
     *
     * @param g The backend graph to expand in.
     * @param patternsMap The map of patterns to extend to. Will be modified.
     * @param baseStates The states which we want to expand. Will be modified.
     * @param k The maximum pattern size, equal to the amount of internal nodes
     * (so excluding input nodes).
     * @param maxPorts The maximum amount of ports that an occurrence
     * ({@link State}) may have.
     * @param expandAfterFlag Denotes whether the enumerator should keep track
     * of the expandableStates. If this is the last enumeration of the process,
     * set to false. Otherwise use true.
     * @param prevK The k (maximum pattern size) from which to start adding the
     * found occurrences to the patternsMap.
     */
    protected void expandSelectedPatterns(Graph g, Object2ObjectOpenCustomHashMap<long[], ObjectArrayList<int[]>> patternsMap,
            Map.Entry<long[], ObjectArrayList<StateSingleOutput>>[] baseStates, int k,
            int maxPorts, boolean expandAfterFlag, int prevK) {

        final int maxInputs = maxPorts;
        nextNb = 0;
        nextPattern = 0;
        expandableStates.clear();

        if (baseStates.length == 0) {
            System.out.println("No base states to expand!");
            return;
        }

        StateSingleOutput base;
        ArrayDeque<StateSingleOutput> stack = new ArrayDeque<>();
        ArrayDeque<Integer> indexStack = new ArrayDeque<>();

        while ((base = getBaseState(baseStates)) != null) {
            stack.push(base);
            indexStack.push(0);
            while (!stack.isEmpty()) {
                StateSingleOutput c_state = stack.peek();
                int expandIndex = indexStack.pop();

                if (c_state.expandable.length <= expandIndex) {
                    /* finished expanding this state */
                    stack.pop();
                } else {
                    /* Continue expanding */
                    indexStack.push(expandIndex + 1);
                    StateSingleOutput expanded = c_state.expand(g, expandIndex);
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
                            expandableStates.merge(codeOcc.code, new ObjectArrayList(new StateSingleOutput[]{expanded}), (v1, v2) -> mergeStateArray(v1, v2));
                        }

                    } else if (expandAfterFlag && codeOcc != null) {
                        //Note: This excludes current invalid occurrences (intermediate output / maxInputs)
                        expandableStates.merge(codeOcc.code, new ObjectArrayList(new StateSingleOutput[]{expanded}), (v1, v2) -> mergeStateArray(v1, v2));
                    }
                }
            }
        }
    }

    private int nextPattern = 0;

    /**
     * Get the next unexpanded base {@link StateSingleOutput} ('base' being a
     * state to start expanding from).
     *
     * @param baseStates The states which we want to expand. Will be modified.
     *
     * @return The next {@link StateSingleOutput} that has to be expanded. null
     * if there is no more next {@link StateSingleOutput}.
     */
    private StateSingleOutput getBaseState(Map.Entry<long[], ObjectArrayList<StateSingleOutput>>[] baseStates) {
        ObjectArrayList<StateSingleOutput> currStates = baseStates[nextPattern].getValue();

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

    /**
     * Prints some information about the used memory.
     */
    protected void printMemory() {
        List<MemoryPoolMXBean> pools = ManagementFactory.getMemoryPoolMXBeans();
        pools.forEach((pool) -> {
            MemoryUsage peak = pool.getPeakUsage();
            System.out.println(String.format("Peak %s memory used: %,d", pool.getName(), peak.getUsed()));
            System.out.println(String.format("Peak %s memory reserved: %,d", pool.getName(), peak.getCommitted()));
        });
    }
}
