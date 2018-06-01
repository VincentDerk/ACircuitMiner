package com.vincentderk.acircuitminer.experiments;

import com.google.common.base.Stopwatch;
import com.vincentderk.acircuitminer.miner.Graph;
import com.vincentderk.acircuitminer.miner.util.Utils;
import com.vincentderk.acircuitminer.miner.emulatable.neutralfinder.EmulatableBlock;
import com.vincentderk.acircuitminer.miner.emulatable.neutralfinder.NeutralFinder;
import com.vincentderk.acircuitminer.miner.emulatable.neutralfinder.canonical.NeutralEdgeCanonical;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenCustomHashMap;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;

/**
 *
 * Reads an AC and performs the {@link NeutralFinder NeutralFinder's} algorithm
 * to find the patterns that are emulatable by using 0 or 1 as input. It prints
 * all the found patters.
 * <p>
 * Since the algorithm is a brute-force approach, using graphs with a high
 * amount of inputs is not advised.
 *
 * @author Vincent Derkinderen
 * @version 1.0
 */
public class ExperimentEmulatable {

    public static void main(String[] args) throws FileNotFoundException, IOException {
        System.out.println("Running ExperimentEmulatable");
        String basePath = "D://Thesis//Nets Benchmark//";
        //String basePath = "D://Thesis//Nets//";
        //String ac = "test6";
        String ac = "alarmResults//alarmPattern8";
        String path = basePath + ac + ".net.ac";

        // Load graph
        Stopwatch stopwatch = Stopwatch.createStarted();
        Graph g = Utils.readACStructure(new FileReader(path));
        System.out.printf("Graph loaded in %s msecs.\n", stopwatch.elapsed(TimeUnit.MILLISECONDS));

        // Finding emulatable
        NeutralFinder finder = new NeutralFinder();
        Object2ObjectOpenCustomHashMap<long[], EmulatableBlock> emulatablePatterns = finder.getEmulatablePatternsMap(g); //beware, emulatablePatterns does not include itself.

        //Printing results
        System.out.println("");
        System.out.println("Printing results...");
        for (Entry<long[], EmulatableBlock> patternEntry : emulatablePatterns.object2ObjectEntrySet()) {
            System.out.println(NeutralEdgeCanonical.printCode(patternEntry.getKey()) + " with input " + Arrays.toString(patternEntry.getValue().input));
        }
        System.out.println("Found " + emulatablePatterns.size() + " patterns");
    }

}
