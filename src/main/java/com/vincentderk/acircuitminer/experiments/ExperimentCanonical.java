package com.vincentderk.acircuitminer.experiments;

import com.vincentderk.acircuitminer.miner.util.Utils;
import com.google.common.base.Stopwatch;
import com.vincentderk.acircuitminer.miner.Graph;
import com.vincentderk.acircuitminer.miner.canonical.CodeOccResult;
import com.vincentderk.acircuitminer.miner.canonical.EdgeCanonical;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryUsage;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Experiment that reads in an AC and runs the canonical labeling method. The
 * current {@link EdgeCanonical implementations} can not efficiently (couple minutes) handle
 * graphs with more than 100 nodes.
 *
 * @author Vincent Derkinderen
 * @version 1.0
 */
public class ExperimentCanonical {

    /**
     *
     * @param args the command line arguments
     * @throws java.io.FileNotFoundException
     */
    public static void main(String[] args) throws FileNotFoundException, IOException {
        System.out.println("Running ExperimentCanonical");
        String basePath = "D://Thesis//Nets Benchmark/";
        //String basePath = "D://Thesis//Nets//";
        String ac = "alarm";
        String path = basePath + ac + ".net.ac";

        // Load graph
        Stopwatch stopwatch = Stopwatch.createStarted();
        Graph g = Utils.readACStructure(new FileReader(path));
        System.out.printf("Graph loaded in %s msecs.\n", stopwatch.elapsed(TimeUnit.MILLISECONDS));

        // -- Canonical Labeling  --
        stopwatch.reset().start();
        //CodeOccResult codeResult = EdgeCanonical.minCanonicalPermutation(g);
        CodeOccResult codeResult = EdgeCanonical.minCanonicalPermutationDFS(g);
        System.out.printf("Canonical code in %s secs.\n", stopwatch.elapsed(TimeUnit.SECONDS));
        System.out.println("code: " + EdgeCanonical.printCode(codeResult.code));

        List<MemoryPoolMXBean> pools = ManagementFactory.getMemoryPoolMXBeans();
        pools.forEach((pool) -> {
            MemoryUsage peak = pool.getPeakUsage();
            System.out.println(String.format("Peak %s memory used: %,d", pool.getName(), peak.getUsed()));
            System.out.println(String.format("Peak %s memory reserved: %,d", pool.getName(), peak.getCommitted()));
        });
    }

}
