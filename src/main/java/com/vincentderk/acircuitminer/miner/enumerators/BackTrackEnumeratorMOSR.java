package com.vincentderk.acircuitminer.miner.enumerators;

import com.google.common.base.Stopwatch;
import com.vincentderk.acircuitminer.miner.util.ArrayLongHashStrategy;
import com.vincentderk.acircuitminer.miner.Graph;
import com.vincentderk.acircuitminer.miner.StateExpandable;
import com.vincentderk.acircuitminer.miner.StateMultiOutput;
import com.vincentderk.acircuitminer.miner.StateSingleOutput;
import com.vincentderk.acircuitminer.miner.canonical.CodeOccResult;
import com.vincentderk.acircuitminer.miner.canonical.EdgeCanonical;
import com.vincentderk.acircuitminer.miner.util.Misc;
import com.vincentderk.acircuitminer.miner.util.Utils;
import com.vincentderk.acircuitminer.miner.util.comparators.EntryProfitStateMOSRCom;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenCustomHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.concurrent.TimeUnit;

//TODO: Update doc to MOSR.
/**
 * The same as {@link BackTrackEnumerator} but now finding occurrences with
 * Multiple Output but still a Single Root (MOSR). Compared to the occurrences
 * found in {@link BackTrackEnumerator}, some nodes of those found
 * occurrences can now have an additional externally-outgoing edge that provides
 * another output.
 *
 * <p>
 * <b>Beware, these occurrences may not all be valid in that an output can be
 * (indirectly) required as an input of the occurrence (component) itself.</b>
 *
 * @author Vincent Derkinderen
 * @version 2.0
 */
public class BackTrackEnumeratorMOSR implements ExpandableEnumerator {

    /**
     * Creates a new {@link Enumerator} with an empty
     * {@link #getExpandableStates() expandableStates}.
     */
    public BackTrackEnumeratorMOSR() {
        expandableStates = new Object2ObjectOpenCustomHashMap(new ArrayLongHashStrategy());
    }

    @Override
    public Object2ObjectOpenCustomHashMap<long[], ObjectArrayList<int[]>> enumerate(Graph g, int k, int maxPorts) {
        return enumerateComplete(g, k, maxPorts, false);
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
     * @return The states that can still be expanded.
     */
    @Override
    public Object2ObjectOpenCustomHashMap<long[], ObjectArrayList<StateMultiOutput>> getExpandableStates() {
        return this.expandableStates;
    }

    private final Object2ObjectOpenCustomHashMap<long[], ObjectArrayList<StateMultiOutput>> expandableStates;

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
        nextNb = 0;
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

                    if (expanded.outputNodes.length + expanded.expandable.length + expanded.unexpandable.length <= maxPorts) { //Count #Inputs (excl literals)
                        /* Pattern occurrence count */
                        codeOcc = expanded.getCodeOcc(g);
                        if (codeOcc.inputCount + expanded.outputNodes.length <= maxPorts) {
                            ObjectArrayList<int[]> singleObj = new ObjectArrayList<>();
                            singleObj.add(codeOcc.lastVerticesOrdered); //vertices of expanded sorted in assigned order.
                            patterns.merge(codeOcc.code, singleObj, (v1, v2) -> mergeAddFirst(v1, v2));
                        }
                    }

                    /* continue expanding? */
                    if (expanded.vertices.length < k) {
                        stack.push(expanded);
                        indexStack.push(0);
                    } else if (expandAfterFlag && codeOcc != null) {
                        //Note: This excludes current invalid occurrences (intermediate output / maxInputs)
                        expandableStates.merge(codeOcc.code, new ObjectArrayList(new StateMultiOutput[]{expanded}), (v1, v2) -> mergeAddFirst(v1, v2));
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
    private static <T> ObjectArrayList<T> mergeAddFirst(ObjectArrayList<T> old, ObjectArrayList<T> newV) {
        old.add(newV.get(0));
        return old;
    }

    /**
     * Used by {@link #getSingleState(Graph)} to keep track of the next node to
     * expand.
     * <p>
     * Used by
     * {@link #getBaseState(java.util.Map.Entry<long[],it.unimi.dsi.fastutil.objects.ObjectArrayList<StateMultiOutput>>[])}
     * to keep track of the next pattern to expand.
     * <
     * p>
     * <
     * b>Initially, before expanding, this value should/will be set to 0.</b>
     */
    protected int nextNb;

    /**
     * Get the next unexpanded {@link StateMultiOutput}. The resulting state
     * will only have 1 element in {@code vertices} along with the correct
     * {@code (un)expandable} children.
     *
     * <p>
     * This is used during the enumeration to iterate over all possible
     * {@link StateMultiOutput StateMultiOutputs} in {@code g} that consist of
     * only 1 node ({@link StateMultiOutput#vertices vertices}).
     *
     * @param g The graph structure to retrieve the information from. The same
     * {@code g} has to be used for all calls.
     * @return The next State that has to be expanded. null if there is no more
     * next State.
     * @see #nextNb
     */
    protected StateMultiOutput getSingleState(Graph g) {
        int currentNb = nextNb++;
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
        return "BackTrackEnumeratorMOSR";
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
     * ({@link BackTrackEnumerator#enumerate(Graph, int, int)}) upto size
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
     * @param verbose Prints more information
     * @return Mapping of patterns to lists of occurrences of that pattern.
     */
    @Override
    public Object2ObjectOpenCustomHashMap<long[], ObjectArrayList<int[]>> enumerate(Graph g, int[] k, int maxPorts, int xBest, boolean verbose) {
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
            System.out.printf("enumerated and found " + patternsMap.size() + " patterns in %s secs using BackTrackIncompleteEnumerator.\n", stopwatch.elapsed(TimeUnit.SECONDS));
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
     * Extend the patternsMap by extending the occurrences of the baseStates.
     * Both {@link patternsMap} and {@link baseStates} will be modified, as well
     * as class fields {@link nextNb}, {@link nextPattern} and
     * {@link expandableStates}.
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

        nextNb = 0;
        nextPattern = 0;
        expandableStates.clear();

        if (baseStates.length == 0) {
            System.out.println("No base states to expand!");
            return;
        }

        StateMultiOutput base;
        ArrayDeque<StateMultiOutput> stack = new ArrayDeque<>();
        ArrayDeque<Integer> indexStack = new ArrayDeque<>();

        while ((base = getBaseState(baseStates)) != null) {
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

                    if (expanded.vertices.length > prevK &&
                            expanded.outputNodes.length + expanded.expandable.length + expanded.unexpandable.length <= maxPorts) {
                        /* Pattern occurrence count */
                        codeOcc = expanded.getCodeOcc(g);
                        if (codeOcc.inputCount + expanded.outputNodes.length <= maxPorts) { //Count #Inputs (incl literals)
                            ObjectArrayList<int[]> singleObj = new ObjectArrayList<>();
                            singleObj.add(codeOcc.lastVerticesOrdered);
                            patternsMap.merge(codeOcc.code, singleObj, (v1, v2) -> mergeAddFirst(v1, v2)); //patterns.addTo(code, 1);
                        }
                    }

                    /* continue expanding? */
                    if (expanded.vertices.length < k) {
                        stack.push(expanded);
                        indexStack.push(0);
                    }
                    if (expandAfterFlag && codeOcc != null && expanded.vertices.length > prevK) {
                        //Note: This excludes current invalid occurrences (intermediate output / maxInputs)
                        expandableStates.merge(codeOcc.code, new ObjectArrayList(new StateMultiOutput[]{expanded}), (v1, v2) -> mergeAddFirst(v1, v2));
                    }
                }
            }
        }
    }

    private int nextPattern = 0;

    /**
     * Get the next unexpanded base {@link StateMultiOutput} ('base' being a
     * state to start expanding from).
     * <p>
     * This updates {@link nextNb}, {@link nextPattern} and the given
     * {@code baseStates}.
     *
     * @param baseStates The states which we want to expand. Will be modified.
     *
     * @return The next {@link StateMultiOutput} that has to be expanded. null
     * if there is no next {@link StateMultiOutput}.
     */
    private StateMultiOutput getBaseState(Map.Entry<long[], ObjectArrayList<StateMultiOutput>>[] baseStates) {
        ObjectArrayList<StateMultiOutput> currStates = baseStates[nextPattern].getValue();

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

}
