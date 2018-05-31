package com.vincentderk.acircuitminer.experiments;

import com.vincentderk.acircuitminer.miner.util.Utils;
import com.google.common.base.Stopwatch;
import com.vincentderk.acircuitminer.miner.Graph;
import com.vincentderk.acircuitminer.miner.Miner;
import com.vincentderk.acircuitminer.miner.canonical.EdgeCanonical;
import com.vincentderk.acircuitminer.miner.util.comparators.EntryProfitCom;
import static com.vincentderk.acircuitminer.miner.util.Utils.patternProfit;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import com.vincentderk.acircuitminer.miner.util.OperationUtils;
import it.unimi.dsi.fastutil.ints.IntAVLTreeSet;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenCustomHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * @author Vincent Derkinderen
 */
public class ExperimentReplaceMultiReNO {

    /**
     * 1. Run alg, 2. take best pattern, 3. print pattern, 4. replace pattern
     * in-place,
     * <>5. repeat (- below) till no more patterns left with non overlapping
     * occurrences.
     * <>- take best pattern (filtering out overlap with previous pattern
     * occurrences)
     * <>- print pattern as ...PatternN &lt i &gt .net.ac
     * <>- replace pattern in-place,
     * <>6. write replaced AC as ...New.net.ac
     *
     * <>
     * <
     * b> The difference with ExperimentReplaceMulti is that this experiment
     * re-performs an internal overlap filter before choosing patterns. So the
     * other Multi continues working on the remaining occurrences, that were
     * first filtered out for internal overlap and then for overlapping with
     * replaced patterns. While this version, before choosing each pattern, it
     * re-runs the overlap by first filtering on overlap with already picked
     * patterns AND THEN check for overlap internally with the remaining
     * occurrences.
     *
     * @param args the command line arguments
     * @throws java.io.FileNotFoundException
     */
    public static void main(String[] args) throws FileNotFoundException, IOException {
        System.out.println("Running DAGFSM_NewCost version - experimentReplaceMultiReNO");
        boolean verbose = false;
        String basePath = "D://Thesis//Nets Benchmark//";
        //String basePath = "D://Thesis//Nets//";
        String ac = "alarm";
        String path = basePath + ac + ".net.ac";
        int[] k = {10};
        int maxInputs = 15; // Default: 16 minus 1 for the output.
        int xBest = 10;

        // Load graph
        Stopwatch stopwatch = Stopwatch.createStarted();
        Graph g = Utils.readACStructure(new FileReader(path));
        System.out.printf("Graph loaded in %s msecs.\n", stopwatch.elapsed(TimeUnit.MILLISECONDS));

        // Find patterns
        stopwatch.reset().start();
        Object2ObjectOpenCustomHashMap<long[], ObjectArrayList<int[]>> patternsAll = Miner.executeRaw(g, k, verbose, maxInputs, xBest);
        Entry<long[], ObjectArrayList<int[]>>[] patterns = OperationUtils.removeOverlap(patternsAll, null);
        System.out.println("");
        System.out.println("Printing results (Overlap still present) ...");
        Utils.writeResults(patternsAll, true);
        System.out.println("");
        System.out.println("Printing results (No overlap present) ...");
        Utils.writeResults(patterns, true);
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
        IntArrayList ignoreList = new IntArrayList(); //Nodes to ignore (this excludes the root node for each replacement)
        IntAVLTreeSet replacedNodes = new IntAVLTreeSet(); //Nodes that are replaceed
        long occLeft = Long.MAX_VALUE;

        System.out.println("");
        //while (nextOpId <= Graph.HIGHEST_OP + 3) {  //3 = #patterns vervangen

        while (occLeft > 0) {
            Entry<long[], ObjectArrayList<int[]>> best = Stream.of(patterns).parallel()
                    .sorted(new EntryProfitCom(false)).findFirst().get();
            long[] pattern = best.getKey();
            ObjectArrayList<int[]> occurrences = best.getValue();

            symbols.put(nextOpId, "n" + nextOpId);

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
            nextOpId++;

            //Fix next pick
            Utils.addInvolvedOpNodes(occurrences, replacedNodes);
            System.out.println(replacedNodes.size() + " forbidden Nodes");
            occurrences.clear(); //Remove (empty) chosen pattern occurrences
            patterns = OperationUtils.removeOverlap(patternsAll, replacedNodes);
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
