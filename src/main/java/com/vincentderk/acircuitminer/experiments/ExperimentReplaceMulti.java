package com.vincentderk.acircuitminer.experiments;

import com.vincentderk.acircuitminer.miner.util.Utils;
import com.google.common.base.Stopwatch;
import com.vincentderk.acircuitminer.miner.Graph;
import com.vincentderk.acircuitminer.miner.Miner;
import com.vincentderk.acircuitminer.miner.canonical.EdgeCanonical;
import com.vincentderk.acircuitminer.miner.util.comparators.EntryProfitCom;
import static com.vincentderk.acircuitminer.miner.util.Utils.patternProfit;
import com.vincentderk.acircuitminer.miner.util.OperationUtils;
import it.unimi.dsi.fastutil.ints.IntAVLTreeSet;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Performs the enumeration algorithm to find the best pattern (not accounting
 * for emulation). It replaces the occurrences of that pattern and repeats to
 * find the next best pattern until no occurrences are left that do not overlap.
 *
 * @author Vincent Derkinderen
 * @version 2.0
 */
public class ExperimentReplaceMulti {

    /**
     * <ol>
     * <li> Run enumeration algorithm to find patterns and their occurrences
     * </li>
     * <li> Print best pattern</li>
     * <li> Replace occurrences of best pattern in-place </li>
     * <li> Repeat till no more patterns left with non overlapping occurrences
     * or the maximum amount of patterns is reached.
     * <ul>
     * <li> Take best pattern (filtering out overlap with previous pattern
     * occurrences) </li>
     * <li> Save pattern as ...PatternN{@code <i>}.net.ac </li>
     * <li> Replace pattern occurrences in-place </li>
     * </ul>
     * </li>
     * <li> Write replaced AC as ...New.net.ac </li>
     * </ol>
     *
     * @param args the command line arguments
     * @throws java.io.FileNotFoundException
     */
    public static void main(String[] args) throws FileNotFoundException, IOException {
        System.out.println("Running ExperimentReplaceMulti");
        boolean verbose = false;
        String basePath = "D://Thesis//Nets Benchmark//";
        //String basePath = "D://Thesis//Nets//";
        String ac = "munin";
        String path = basePath + ac + ".net.ac";
        int[] k = {6, 10, 14, 20};
        int maxPorts = 16;
        int xBest = 10;

        // Load graph
        Stopwatch stopwatch = Stopwatch.createStarted();
        Graph g = Utils.readACStructure(new FileReader(path));
        System.out.printf("Graph loaded in %s msecs.\n", stopwatch.elapsed(TimeUnit.MILLISECONDS));

        // Find patterns
        stopwatch.reset().start();
        Entry<long[], ObjectArrayList<int[]>>[] patterns = Miner.execute(g, k, verbose, maxPorts, xBest);
        System.out.printf("Executed total algorithm in %s secs.\n", stopwatch.elapsed(TimeUnit.SECONDS));

        // Filter out patterns we want
        /*patterns = Stream.of(patterns).parallel()
                .filter(x -> x.getValue().get(0).length > 2)
                .toArray(Entry[]::new);*/
        // Symbols
        HashMap<Short, String> symbols = new HashMap();
        symbols.put(Graph.PRODUCT, "*");
        symbols.put(Graph.SUM, "+");
        symbols.put(Graph.INPUT, "l");

        // Start choosing patterns
        short nextOpId = Graph.HIGHEST_OP + 1;
        IntArrayList ignoreList = new IntArrayList();
        IntAVLTreeSet replacedNodes = new IntAVLTreeSet();
        long occLeft = Long.MAX_VALUE;

        System.out.println("");
        while (nextOpId <= Graph.HIGHEST_OP + 3) {  //3 = #patterns replaced
        //while (occLeft > 0) {
            Entry<long[], ObjectArrayList<int[]>> best = Stream.of(patterns).parallel()
                    .sorted(new EntryProfitCom(false)).findFirst().get();
            long[] pattern = best.getKey();
            ObjectArrayList<int[]> occurrences = best.getValue();

            symbols.put(nextOpId, "n" + nextOpId);
            nextOpId++;

            //Replace best in-place.
            stopwatch.reset().start();
            System.out.println("Replacing " + EdgeCanonical.printCode(pattern) + " with " + occurrences.size() + " occurrences.");
            IntArrayList ignore = OperationUtils.replace(g, pattern, occurrences, nextOpId);
            System.out.println("Estimated savings of " + patternProfit(best.getKey(), best.getValue()));
            System.out.printf("Replaced in %s secs.\n", stopwatch.elapsed(TimeUnit.SECONDS));
            ignoreList.addAll(ignore);

            //Write pattern
            String patternPath = basePath + ac + "PatternN" + nextOpId + ".net.ac";
            OperationUtils.writePattern(best.getKey(), patternPath, symbols);

            //Fix next pick
            //TODO: We could also restart removing overlap
            //from the big result where overlap is still allowed.
            //This way, we do a simple internal overlap AFTER removing the occurrences
            //that aren't allowed anyway. This prevents a choice of internal occurrence
            //that prevents other occurrences but that will later be filtered out anyway because
            //it was forbidden by another chosen pattern. -> ExperimentReplaceMultiReNO
            Utils.addInvolvedOpNodes(occurrences, replacedNodes);
            System.out.println(replacedNodes.size() + " forbidden Nodes");
            occurrences.clear(); //Remove (empty) chosen pattern occurrences
            patterns = OperationUtils.removeOverlap(patterns, replacedNodes);
            System.out.println("Printing what is left...");
            occLeft = Utils.writeResults(patterns, false);
            System.out.println("");
        }

        //Write
        System.out.println("");
        String outPath = basePath + ac + "New.net.ac";
        OperationUtils.writeAC(g, outPath, path, ignoreList, symbols);
    }

}
