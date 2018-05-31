package com.vincentderk.acircuitminer.experiments;

import com.vincentderk.acircuitminer.miner.util.Utils;
import com.google.common.base.Stopwatch;
import com.vincentderk.acircuitminer.miner.Graph;
import com.vincentderk.acircuitminer.miner.Miner;
import com.vincentderk.acircuitminer.miner.util.comparators.EntryProfitCom;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * @author Vincent Derkinderen
 */
public class Main {

    /**
     * Run alg, take best pattern, replace pattern in-place, write replaced AC
     * as ..New.net.ac and write pattern as ...Pattern.net.ac
     *
     * @param args the command line arguments
     * @throws java.io.FileNotFoundException
     */
    public static void main(String[] args) throws FileNotFoundException, IOException {
        System.out.println("Running DAGFSM_NewCost version");
        boolean verbose = false;
        String basePath = "D://Thesis//Nets Benchmark//";
        //String basePath = "D://Thesis//Nets//";
        String ac = "alarm";
        String path = basePath + ac + ".net.ac";
        int[] k = {12};
        int maxInputs = 15; // Default: 16 minus 1 for the output.
        int xBest = 10;

        // Load graph
        Stopwatch stopwatch = Stopwatch.createStarted();
        Graph g = Utils.readACStructure(new FileReader(path));
        System.out.printf("Graph loaded in %s msecs.\n", stopwatch.elapsed(TimeUnit.MILLISECONDS));

        // Find patterns
        stopwatch.reset().start();
        Entry<long[], ObjectArrayList<int[]>>[] patterns = Miner.execute(g, k, verbose, maxInputs, xBest);
        System.out.printf("Executed total algorithm in %s secs.\n", stopwatch.elapsed(TimeUnit.SECONDS));

        Entry<long[], ObjectArrayList<int[]>> best = Stream.of(patterns).parallel()
                .sorted(new EntryProfitCom(false)).findFirst().get();

        
        //Replace best in-place.
        /*stopwatch.reset().start();
        System.out.println("Replacing " + EdgeCanonical.printCode(best.getKey()) + " with " + best.getValue().size() + " occurrences.");
        IntArrayList ignore = OperationUtils.replace(g, best.getKey(), best.getValue(), (short) (Graph.HIGHEST_OP+1));
        System.out.println("Estimated savings of " + patternProfit(best.getKey(), best.getValue()));
        System.out.printf("Replaced best in %s secs.\n", stopwatch.elapsed(TimeUnit.SECONDS));*/

        //Write
        /*stopwatch.reset().start();
        String outPath = basePath + ac + "New.net.ac";
        FileWriter writer = new FileWriter(outPath);
        HashMap<Short, String> symbols = new HashMap();
        symbols.put(Graph.PRODUCT, "*");
        symbols.put(Graph.SUM, "+");
        symbols.put(Graph.INPUT, "l");
        symbols.put((short) 3, "n");
        Int2IntMap literalMap = Utils.getLiteralMap(new FileReader(path));
        OperationUtils.write(g, writer, ignore, symbols, literalMap);
        System.out.printf("Wrote result in %s secs.\n", stopwatch.elapsed(TimeUnit.SECONDS));*/

        //Write pattern
        /*stopwatch.reset().start();
        Graph patternGraph = OperationUtils.codeToGraph(best.getKey());
        String patternPath = basePath + ac + "Pattern.net.ac";
        writer = new FileWriter(patternPath);
        OperationUtils.writePatternGraph(writer, patternGraph, new IntArrayList(), symbols);
        System.out.printf("Wrote pattern in %s secs.\n", stopwatch.elapsed(TimeUnit.SECONDS));*/
    }

}
