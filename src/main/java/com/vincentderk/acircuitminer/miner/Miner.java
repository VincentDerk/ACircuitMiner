package com.vincentderk.acircuitminer.miner;

import com.google.common.base.Stopwatch;
import com.vincentderk.acircuitminer.miner.canonical.EdgeCanonical;
import com.vincentderk.acircuitminer.miner.enumerators.MultiBackTrackEnumerator;
import com.vincentderk.acircuitminer.miner.enumerators.SecondaryEnumerator;
import com.vincentderk.acircuitminer.miner.enumerators.SecondaryMultiBackTrackEnumerator;
import static com.vincentderk.acircuitminer.miner.util.OperationUtils.removeOverlap;
import com.vincentderk.acircuitminer.miner.util.Utils;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenCustomHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryUsage;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import com.vincentderk.acircuitminer.miner.enumerators.PrimaryEnumerator;

/**
 * Uses the {@link ExpandableEnumerator Enumerators} to find patterns and their
 * occurrences in an arithmetic circuit ({@link Graph}). It uses a
 * {@link SecondaryEnumerator} to heuristically continue the search after having
 * used the {@link PrimaryEnumerator} for a complete search.
 * <p>
 * Note: The current search implementations do not allow a node to be directly
 * connected to another node twice.
 *
 * @author Vincent Derkinderen
 * @version 2.0
 */
public class Miner {

    /**
     * Execute the enumeration algorithm ({@link MultiBackTrackEnumerator}, max
     * threads) upto size k[0]. From then, pick the xBest that hadn't been
     * extended due to size and expand these till k[1]. For the iteration upto
     * size k[2], the xBest from the previous iteration are used. The difference
     * with the second iteration is that the second iteration extends the xBest
     * patterns of the occurrences that weren't expanded in the first iteration
     * due to size. While after that, the xBest of all valid occurrences of the
     * previous iteration are considered. This is the case for
     * k[2],k[3],...(Note, valid patterns are induced subgraphs with only 1
     * output and max {@code maxInputs} inputs.) This means that only for the
     * first iteration upto size k[0], all patterns and their occurrences are
     * found. In the end, a first-come, first-served heuristic is used to remove
     * overlapping occurrences within each pattern.
     *
     * <p>
     * The results are internally filtered so for each pattern there are no
     * overlapping occurrences. After that, all results are printed from low to
     * high.
     *
     * @param g The graph to mine in
     * @param k The sizes to mine upto. Size of a pattern is determined by the
     * amount of operations in it.
     * @param verbose Prints more
     * @param maxPorts The maximum amount of ports. Note all found
     * patterns only have 1 output port.
     * @param xBest The x best patterns to select for the next iteration.
     * @return An array of entries from pattern to its occurrences. An
     * occurrence is in this case given by the nodes that represent an
     * operation. The operation nodes are in order of appearance in the pattern
     * code.
     * @throws FileNotFoundException
     * @throws IOException
     */
    public static Map.Entry<long[], ObjectArrayList<int[]>>[] execute(Graph g, int[] k, boolean verbose, int maxPorts, int xBest) throws FileNotFoundException, IOException {
        Stopwatch stopwatch = Stopwatch.createStarted();
        /* First enumeration */
        System.out.println("--------------------------------------------------------");
        System.out.println("First iteration k=" + k[0]);
        //Enumerator enumerator = new ForwardEnumerator(g);
        //Enumerator enumerator = new BackTrackEnumerator(g);
        PrimaryEnumerator enumerator = new MultiBackTrackEnumerator(g);
        boolean expandAfterFlag = k.length != 1;
        stopwatch.reset().start();
        Object2ObjectOpenCustomHashMap<long[], ObjectArrayList<int[]>> patternsMap = enumerator.enumerate(g, k[0], maxPorts, expandAfterFlag);
        System.out.printf("enumerated and found " + patternsMap.size() + " patterns in %s secs using %s.\n", stopwatch.elapsed(TimeUnit.SECONDS), enumerator);

        if (verbose) {
            System.out.println("");
            printMemory();
            System.out.println("");
            System.out.println("Printing results...");
            Utils.writeResults(patternsMap, true);
        }

        /* Second enumeration */
        Map.Entry<long[], ObjectArrayList<StateSingleOutput>>[] interestingStates = null;
        if (k.length > 1) {
            //TODO: Base second enumeration on |k| best or on current global best?
            interestingStates = Utils.filter(enumerator.getExpandableStates(), xBest);
        }

        for (int i = 1; i < k.length; i++) {
            expandAfterFlag = (i + 1 != k.length);
            System.out.println("--------------------------------------------------------");
            System.out.println("Iteration " + (i + 1) + " k=" + k[i]);
            /* Filter previous*/

            System.out.println("Expanding (occurrenceCount) code:");
            for (Map.Entry<long[], ObjectArrayList<StateSingleOutput>> entry : interestingStates) {
                System.out.println("(" + entry.getValue().size() + ") " + EdgeCanonical.printCode(entry.getKey()));
            }
            System.out.println("");

            stopwatch.reset().start();
            //SecondaryEnumerator secondaryEnumerator = new SecondaryBackTrackEnumerator(g);
            SecondaryEnumerator secondaryEnumerator = new SecondaryMultiBackTrackEnumerator(g);
            secondaryEnumerator.expandSelectedPatterns(patternsMap, interestingStates, k[i], maxPorts, expandAfterFlag, k[i - 1]);
            System.out.printf("enumerated and found " + patternsMap.size() + " patterns in %s secs using %s.\n", stopwatch.elapsed(TimeUnit.SECONDS), secondaryEnumerator);

            if (verbose) {
                System.out.println("");
                printMemory();
            }

            if (i + 1 < k.length) {
                interestingStates = Utils.filter(secondaryEnumerator.getExpandableStates(), xBest);
            }
        }

        stopwatch.reset().start();
        Map.Entry<long[], ObjectArrayList<int[]>>[] subset = removeOverlap(patternsMap, null);
        //Object2ObjectOpenCustomHashMap<long[], ObjectArrayList<int[]>> subset = removeOverlapSingle(patternsMap); // Singlethreaded version
        System.out.printf("Removed overlap in %s secs.\n", stopwatch.elapsed(TimeUnit.SECONDS));

        System.out.println("");
        System.out.println("Printing results...");
        Utils.writeResults(subset, true);

        return subset;
    }

    /**
     * Execute the algorithm ({@link MultiBackTrackEnumerator}, max threads)
     * upto size k[0]. From then, pick the xBest that hadn't been extended due
     * to size and expand these till k[1]. For the iteration upto size k[2], the
     * xBest from the previous iteration are used. The difference with the
     * second iteration is that the second iteration extends the xBest patterns
     * of the occurrences that weren't expanded in the first iteration due to
     * size. While after that, the xBest of all valid occurrences of the
     * previous iteration are considered. This is the case for
     * k[2],k[3],...(Note, valid patterns are induced subgraphs with only 1
     * output and max <code>maxInputs</code> inputs.) This means that only for
     * the first iteration upto size k[0], all patterns and their occurrences
     * are found.
     *
     * <p>
     * <b>The difference with
     * {@link #execute(dagfsm.Graph, int[], boolean, int, int)} is that this
     * method does not perform an overlap filter. The result of this method is
     * therefore the raw data, for each pattern all its occurrences (including
     * overlapping).</b>
     *
     * @param g The graph to mine in
     * @param k The sizes to mine upto. Size of a pattern is determined by the
     * amount of operations in it.
     * @param verbose Prints more
     * @param maxInputs The maximum amount of input ports. Note all found
     * patterns only have 1 output port.
     * @param xBest The x best patterns to select for the next iteration.
     * @return An array of entries from pattern to its occurrences. An
     * occurrence is in this case given by the nodes that represent an
     * operation. The operation nodes are in order of appearance in the pattern
     * code.
     * @throws FileNotFoundException
     * @throws IOException
     */
    public static Object2ObjectOpenCustomHashMap<long[], ObjectArrayList<int[]>> executeRaw(Graph g, int[] k, boolean verbose, int maxInputs, int xBest) throws FileNotFoundException, IOException {
        Stopwatch stopwatch = Stopwatch.createStarted();
        /* First enumeration */
        System.out.println("--------------------------------------------------------");
        System.out.println("First iteration k=" + k[0]);
        //Enumerator enumerator = new ForwardEnumerator(g);
        //Enumerator enumerator = new BackTrackEnumerator(g);
        PrimaryEnumerator enumerator = new MultiBackTrackEnumerator(g);
        boolean expandAfterFlag = k.length != 1;
        stopwatch.reset().start();
        Object2ObjectOpenCustomHashMap<long[], ObjectArrayList<int[]>> patternsMap = enumerator.enumerate(g, k[0], maxInputs, expandAfterFlag);
        System.out.printf("Enumerated and found " + patternsMap.size() + " patterns in %s secs using %s.\n", stopwatch.elapsed(TimeUnit.SECONDS), enumerator);

        if (verbose) {
            System.out.println("");
            printMemory();
            System.out.println("");
            System.out.println("Printing results...");
            Utils.writeResults(patternsMap, true);
        }

        /* Second enumeration */
        Map.Entry<long[], ObjectArrayList<StateSingleOutput>>[] interestingStates = null;
        if (k.length > 1) {
            //TODO: Base second enumeration on |k| best or on current global best?
            interestingStates = Utils.filter(enumerator.getExpandableStates(), xBest);
        }

        for (int i = 1; i < k.length; i++) {
            expandAfterFlag = (i + 1 != k.length);
            System.out.println("--------------------------------------------------------");
            System.out.println("Iteration " + (i + 1) + " k=" + k[i]);
            /* Filter previous*/

            System.out.println("Expanding (occurrenceCount) code:");
            for (Map.Entry<long[], ObjectArrayList<StateSingleOutput>> entry : interestingStates) {
                System.out.println("(" + entry.getValue().size() + ") " + EdgeCanonical.printCode(entry.getKey()));
            }
            System.out.println("");

            stopwatch.reset().start();
            SecondaryEnumerator secondaryEnumerator = new SecondaryMultiBackTrackEnumerator(g);
            secondaryEnumerator.expandSelectedPatterns(patternsMap, interestingStates, k[i], maxInputs, expandAfterFlag, k[i - 1]);
            System.out.printf("enumerated and found " + patternsMap.size() + " patterns in %s secs using %s.\n", stopwatch.elapsed(TimeUnit.SECONDS), secondaryEnumerator);

            if (verbose) {
                System.out.println("");
                printMemory();
            }

            if (i + 1 < k.length) {
                interestingStates = Utils.filter(secondaryEnumerator.getExpandableStates(), xBest);
            }
        }

        return patternsMap;
    }

    private static void printMemory() {
        List<MemoryPoolMXBean> pools = ManagementFactory.getMemoryPoolMXBeans();
        pools.forEach((pool) -> {
            MemoryUsage peak = pool.getPeakUsage();
            System.out.println(String.format("Peak %s memory used: %,d", pool.getName(), peak.getUsed()));
            System.out.println(String.format("Peak %s memory reserved: %,d", pool.getName(), peak.getCommitted()));
        });
    }
}
