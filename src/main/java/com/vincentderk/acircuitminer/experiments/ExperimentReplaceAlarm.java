package com.vincentderk.acircuitminer.experiments;

import com.vincentderk.acircuitminer.miner.util.Utils;
import com.google.common.base.Stopwatch;
import com.vincentderk.acircuitminer.miner.Graph;
import com.vincentderk.acircuitminer.miner.Miner;
import com.vincentderk.acircuitminer.miner.SOSR;
import com.vincentderk.acircuitminer.miner.canonical.EdgeCanonical;
import com.vincentderk.acircuitminer.miner.util.comparators.EntryProfitCom;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import com.vincentderk.acircuitminer.miner.util.OperationUtils;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Finds the best pattern (not accounting for emulation) of size 2..30, replaces
 * the occurrences and writes both the pattern and the resulting new AC to file.
 * 
 * <p>
 * Focuses on Single Output Single Root ({@link SOSR}) patterns.
 *
 * @author Vincent Derkinderen
 * @version 2.0
 */
public class ExperimentReplaceAlarm {

    /**
     * Experiment Alarm 2..17,25,30.
     *
     * This experiment runs alarm benchmark with k={17,25,30} and xBest=100. For
     * each pattern size (2..30), the best pattern is taken (not accounting for
     * emulation) and its non-overlap occurrences are replaced in the original
     * AC. The replaced AC is written to file as well as the pattern.
     *
     * @param args the command line arguments
     * @throws java.io.FileNotFoundException
     */
    public static void main(String[] args) throws FileNotFoundException, IOException {
        System.out.println("Running ExperimentReplaceAlarm");
        boolean verbose = false;
        String basePath = "D://Thesis//Nets Benchmark//";
        //String basePath = "D://Thesis//Nets//";
        //String ac = "test4";
        //String ac = "asia";
        String ac = "alarm";
        //String ac = "hailfinder";
        //String ac = "munin";
        String path = basePath + ac + ".net.ac";
        //int[] k = {5};
        int[] k = {17, 25, 30};
        int maxPorts = 16;
        int xBest = 100;

        // Load graph
        Stopwatch stopwatch = Stopwatch.createStarted();
        Graph g = Utils.readACStructure(new FileReader(path));
        System.out.printf("Graph loaded in %s msecs.\n", stopwatch.elapsed(TimeUnit.MILLISECONDS));

        // Find patterns
        stopwatch.reset().start();
        Entry<long[], ObjectArrayList<int[]>>[] patterns = Miner.execute(g, k, verbose, maxPorts, xBest);
        System.out.printf("Executed total algorithm in %s secs.\n", stopwatch.elapsed(TimeUnit.SECONDS));

        for (int i = 2; i <= k[k.length - 1]; i++) {
            System.out.println("");
            System.out.println("Handling size " + i);
            final int currSize = i;

            Optional<Entry<long[], ObjectArrayList<int[]>>> bestOptional = Stream.of(patterns).parallel()
                    .filter(x -> x.getValue().get(0).length == currSize)
                    .sorted(new EntryProfitCom(false)).findFirst();
            Entry<long[], ObjectArrayList<int[]>> best;
            if (!bestOptional.isPresent()) {
                System.out.println("No valid pattern of this size found.");
                continue;
            } else {
                best = bestOptional.get();
            }

            //Replace best
            stopwatch.reset().start();
            System.out.println("Replacing " + EdgeCanonical.printCode(best.getKey()) + " with " + best.getValue().size() + " occurrences.");
            Graph gNew = g.clone();
            IntArrayList ignore = OperationUtils.replace(gNew, best.getKey(), best.getValue(), (short) 3);
            System.out.println("Estimated savings of " + SOSR.patternProfit(best.getKey(), best.getValue()));
            System.out.printf("Replaced best in %s secs.\n", stopwatch.elapsed(TimeUnit.SECONDS));

            //Write
            stopwatch.reset().start();
            String outPath = basePath + "alarmResults//" + ac + "New" + i + ".net.ac";
            FileWriter writer = new FileWriter(outPath);
            HashMap<Short, String> symbols = new HashMap();
            symbols.put(Graph.PRODUCT, "*");
            symbols.put(Graph.SUM, "+");
            symbols.put(Graph.INPUT, "l");
            symbols.put((short) 3, "n");
            Int2IntMap literalMap = Utils.getLiteralMap(new FileReader(path));
            OperationUtils.write(gNew, writer, ignore, symbols, literalMap);
            gNew = null;
            System.out.printf("Wrote result in %s secs.\n", stopwatch.elapsed(TimeUnit.SECONDS));

            //Write pattern
            stopwatch.reset().start();
            Graph patternGraph = Graph.codeToGraph(best.getKey());
            String patternPath = basePath + "alarmResults//" + ac + "Pattern" + i + ".net.ac";
            writer = new FileWriter(patternPath);
            OperationUtils.writePatternGraph(writer, patternGraph, new IntArrayList(), symbols);
            System.out.printf("Wrote pattern in %s secs.\n", stopwatch.elapsed(TimeUnit.SECONDS));
        }
    }

}
