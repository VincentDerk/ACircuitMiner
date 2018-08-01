package com.vincentderk.acircuitminer.miner;

import com.google.common.base.Stopwatch;
import com.vincentderk.acircuitminer.miner.enumerators.ExpandableEnumerator;
import com.vincentderk.acircuitminer.miner.enumerators.MultiBackTrackEnumerator;
import static com.vincentderk.acircuitminer.miner.util.OperationUtils.removeOverlap;
import com.vincentderk.acircuitminer.miner.util.Utils;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenCustomHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 *
 *
 * Uses the {@link ExpandableEnumerator Enumerators} to find patterns and their
 * occurrences in an arithmetic circuit ({@link Graph}).
 * <p>
 * Note: The current search implementations do not allow a node to be directly
 * connected to another node twice. (This is caused by the EdgeCanonical
 * choices).
 *
 * @author Vincent Derkinderen
 * @version 2.0
 */
public class Miner {

    /**
     * Execute an enumeration algorithm to find patterns and their occurrences.
     * The patterns found are induced connected subgraphs with one output and a
     * maximum of {@code maxPorts-1} input ports.
     * <p>
     * First, a complete search is performed for patterns upto size
     * {@code k[0]}. If {@code k} has more than one element, the search
     * continues heuristically (incomplete) in the second step to bigger sizes
     * {@code k[1],k[2],...}. Every iteration, the xBest expandable patterns are
     * further expanded.
     * <p>
     * In the end, a first-come, first-served heuristic is used to remove
     * overlapping occurrences within each pattern. The results are internally
     * filtered so for each pattern there are no overlapping occurrences. After
     * that, all results are printed from low to high according to
     * {@link Utils#writeResults(java.util.Map.Entry<long[],it.unimi.dsi.fastutil.objects.ObjectArrayList<int[]>>[],
     * boolean)}.
     *
     * @param g The graph to mine in.
     * @param k The sizes to mine upto. Size of a pattern is determined by the
     * amount of operations in it.
     * @param verbose Prints more information.
     * @param maxPorts The maximum amount of ports. Note, all found patterns
     * only have 1 output port.
     * @param xBest The x best patterns to select for the next iteration.
     * @return An array of entries from pattern to its occurrences. An
     * occurrence is in this case given by the nodes that represent an
     * operation. The operation nodes are in order of appearance in the pattern
     * code.
     */
    public static Map.Entry<long[], ObjectArrayList<int[]>>[] execute(Graph g, int[] k, boolean verbose, int maxPorts, int xBest) {
        ExpandableEnumerator enumerator = new MultiBackTrackEnumerator();
        Object2ObjectOpenCustomHashMap<long[], ObjectArrayList<int[]>> patternsMap = enumerator.enumerate(g, k, maxPorts, xBest, verbose);

        Stopwatch stopwatch = Stopwatch.createStarted();
        Map.Entry<long[], ObjectArrayList<int[]>>[] subset = removeOverlap(patternsMap, null);
        System.out.printf("Removed overlap in %s secs.\n", stopwatch.elapsed(TimeUnit.SECONDS));

        System.out.println("");
        System.out.println("Printing results...");
        Utils.writeResults(subset, true);

        return subset;
    }

    /**
     * Execute an enumeration algorithm to find patterns and their occurrences.
     * The patterns found are induced connected subgraphs with one output and a
     * maximum of {@code maxPorts-1} input ports.
     * <p>
     * First, a complete search is performed for patterns upto size
     * {@code k[0]}. If {@code k} has more than one element, the search
     * continues heuristically (incomplete) in the second step to bigger sizes
     * {@code k[1],k[2],...}. Every iteration, the xBest expandable patterns are
     * further expanded.
     * <p>
     * In the end, all results are printed from low to high according to
     * {@link Utils#writeResults(java.util.Map.Entry<long[],it.unimi.dsi.fastutil.objects.ObjectArrayList<int[]>>[],
     * boolean)}.
     * <p>
     * The difference with {@link #execute(Graph, int[], boolean, int, int)} is
     * that this method does not filter the overlap.
     *
     * @param g The graph to mine in.
     * @param k The sizes to mine upto. Size of a pattern is determined by the
     * amount of operations in it.
     * @param verbose Prints more information.
     * @param maxPorts The maximum amount of ports. Note, all found patterns
     * only have 1 output port.
     * @param xBest The x best patterns to select for the next iteration.
     * @return An array of entries from pattern to its occurrences. An
     * occurrence is in this case given by the nodes that represent an
     * operation. The operation nodes are in order of appearance in the pattern
     * code.
     */
    public static Object2ObjectOpenCustomHashMap<long[], ObjectArrayList<int[]>> executeRaw(Graph g, int[] k, boolean verbose, int maxPorts, int xBest) {
        ExpandableEnumerator enumerator = new MultiBackTrackEnumerator();
        Object2ObjectOpenCustomHashMap<long[], ObjectArrayList<int[]>> patternsMap = enumerator.enumerate(g, k, maxPorts, xBest, verbose);

        System.out.println("");
        System.out.println("Printing results...");
        Utils.writeResults(patternsMap, true);

        return patternsMap;
    }

}
