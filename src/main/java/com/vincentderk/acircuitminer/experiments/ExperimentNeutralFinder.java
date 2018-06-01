package com.vincentderk.acircuitminer.experiments;

import com.vincentderk.acircuitminer.miner.util.Utils;
import com.google.common.base.Stopwatch;
import com.vincentderk.acircuitminer.miner.Graph;
import com.vincentderk.acircuitminer.miner.Miner;
import com.vincentderk.acircuitminer.miner.canonical.EdgeCanonical;
import com.vincentderk.acircuitminer.miner.util.ArrayLongHashStrategy;
import com.vincentderk.acircuitminer.miner.util.comparators.EntryPatternSizeCom;
import com.vincentderk.acircuitminer.miner.util.comparators.EntryUseCom;
import com.vincentderk.acircuitminer.miner.util.OperationUtils;
import com.vincentderk.acircuitminer.miner.emulatable.neutralfinder.EmulatableBlock;
import com.vincentderk.acircuitminer.miner.emulatable.neutralfinder.NeutralFinder;
import com.vincentderk.acircuitminer.miner.emulatable.neutralfinder.UseBlock;
import it.unimi.dsi.fastutil.ints.IntAVLTreeSet;
import it.unimi.dsi.fastutil.objects.AbstractObject2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenCustomHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Experiment to find the best pattern taking into account the patterns
 * that are emulatable by using 0 or 1 as input.
 * 
 * <p>
 * <u>Algorithm:</u>
 * <ul>
 * <li> Find patterns and their occurrences in an AC </li>
 * <li> Evaluate each pattern on their savings </li>
 * ({@link #useNeutralElements(java.util.Map.Entry, Object2ObjectOpenCustomHashMap)})
 * </li>
 * by determining the usage of a pattern. </li>
 * <li> Sort on most savings </li>
 * <li> Print information on each pattern usage </li>
 * </ul>
 *
 * @author Vincent Derkinderen
 * @version 1.0
 */
public class ExperimentNeutralFinder {

    /**
     *
     * @param args the command line arguments
     * @throws java.io.FileNotFoundException
     */
    public static void main(String[] args) throws FileNotFoundException, IOException {
        System.out.println("Running ExperimentNeutralFinder");
        boolean verbose = false;
        String basePath = "D://Thesis//Nets Benchmark//";
        //String basePath = "D://Thesis//Nets//";
        String ac = "alarm";
        String path = basePath + ac + ".net.ac";
        int[] k = {6};
        int maxInputs = 15; // Default: 16 minus 1 for the output.
        int xBest = 10;

        // Load graph
        Stopwatch stopwatch = Stopwatch.createStarted();
        Graph g = Utils.readACStructure(new FileReader(path));
        System.out.printf("Graph loaded in %s msecs.\n", stopwatch.elapsed(TimeUnit.MILLISECONDS));
        long totalGraphCost = OperationUtils.getTotalCosts(g);

        // Find patterns
        stopwatch.reset().start();
        Object2ObjectOpenCustomHashMap<long[], ObjectArrayList<int[]>> patternsAll = Miner.executeRaw(g, k, verbose, maxInputs, xBest);
        System.out.printf("Executed mining algorithm in %s secs.\n", stopwatch.elapsed(TimeUnit.SECONDS));

        //Decide best pattern
        stopwatch.reset().start();
        Entry<long[], ObjectArrayList<int[]>>[] patterns = OperationUtils.removeOverlap(patternsAll, null);
        Entry<long[], UseBlock>[] patternUseBlocks = Stream.of(patterns).parallel() //TODO: parallel vs sequential
                .map(x -> useNeutralElements(x, patternsAll))
                .sorted(new EntryUseCom(false)) //true = low to high savings
                .toArray(Map.Entry[]::new);
        System.out.printf("Finished replace-plan for each pattern in %s secs.\n", stopwatch.elapsed(TimeUnit.SECONDS));

        // -- DEBUG, found info -- 
        double opNodeCount = Utils.operationNodeCount(g);
        System.out.println("");
        System.out.println("Amount of arithmetic nodes in AC: " + opNodeCount);
        System.out.println("Used format: - (<operation count of pattern>|<coverage of AC>%) <occurrence count> of <pattern>");
        DecimalFormat dec = new DecimalFormat("#0.000");
        for (Entry<long[], UseBlock> entry : patternUseBlocks) {
            int opCountOfP = Utils.operationNodeCount(entry.getKey());
            int inputCountOfP = Utils.nodeCount(entry.getKey()) - opCountOfP;
            double relativeSavings = entry.getValue().profit / totalGraphCost * 100;
            System.out.println("");
            System.out.println("Pattern: " + EdgeCanonical.printCode(entry.getKey()));
            System.out.println("Amount of operations|input: " + opCountOfP + "|" + inputCountOfP);
            System.out.println("Savings: " + String.format("%.2f", entry.getValue().profit) + " pJ / " + totalGraphCost + " pJ (" + dec.format(relativeSavings) + "%)");

            EmulatableBlock[] usedPatterns = entry.getValue().patterns;
            ObjectArrayList<ObjectArrayList<int[]>> usedOccurrences = entry.getValue().occurrences;
            int totalNodeCount = 0;

            int occCount0 = usedOccurrences.get(0).size();
            String code0 = EdgeCanonical.printCode(entry.getValue().patternP);
            int opCountPerOcc0 = Utils.operationNodeCount(entry.getValue().patternP);
            int nodeCount0 = opCountPerOcc0 * occCount0;
            totalNodeCount += nodeCount0;
            double relNodeCount0 = (nodeCount0 / opNodeCount) * 100;

            System.out.println("- " + "(" + opCountPerOcc0 + "|" + dec.format(relNodeCount0) + "%) " + occCount0 + " of " + code0);

            for (int i = 0; i < usedPatterns.length; i++) {
                int occCount = usedOccurrences.get(i + 1).size();
                String code = EdgeCanonical.printCode(usedPatterns[i].emulatedCode);
                int opCountPerOcc = Utils.operationNodeCount(usedPatterns[i].emulatedCode);
                int nodeCount = opCountPerOcc * occCount;
                totalNodeCount += nodeCount;
                double relNodeCount = (nodeCount / opNodeCount) * 100;

                System.out.println("- " + "(" + opCountPerOcc + "|" + dec.format(relNodeCount) + "%) " + occCount + " of " + code);
            }
            System.out.println("Total node coverage: " + dec.format(totalNodeCount / opNodeCount * 100) + "%");
        }
        System.out.println("");

        // -- Replacement, use best pattern --
        /*
        Entry<long[], UseBlock> best = patternUseBlocks[0];
        UseBlock useBlock = best.getValue();
        IntArrayList ignoreList; //Nodes to ignore (this excludes the root node for each replacement)
        short newOp = Graph.HIGHEST_OP + 1;
        ignoreList = OperationUtils.replace(g, useBlock, newOp);

        // -- Writing --
        HashMap<Short, String> symbols = new HashMap();
        symbols.put(Graph.PRODUCT, "*");
        symbols.put(Graph.SUM, "+");
        symbols.put(Graph.INPUT, "l");
        symbols.put(newOp, "n" + newOp);

        // Own pattern
        String patternPOutputPath = basePath + ac + "PatternN" + newOp + ".net.ac";

        // Emulated patterns
        short extraOp = newOp;
        extraOp++;
        String[] emulatedOutputPaths = new String[useBlock.patterns.length];
        for (int i = 0; i < emulatedOutputPaths.length; i++) {
            //Write pattern
            emulatedOutputPaths[i] = basePath + ac + "PatternN" + extraOp + ".net.ac";
            extraOp++;
        }

        // Replaced graph
        String outPath = basePath + ac + "New.net.ac";

        // Write patterns
        OperationUtils.writeUseBlock(g, useBlock, outPath, path, patternPOutputPath, emulatedOutputPaths, symbols, ignoreList);
         */
    }

    /**
     * Determine a possible usage for the pattern P contained in {@code patternEntry}.
     * <p>
     * First, the occurrences of the pattern are replaced ({@code patternEntry.getValue()}).
     * Then, {@link NeutralFinder} is used to obtain all patterns that are emulatable by P.
     * The occurrences of these emulatable patterns are also replaced, keeping in mind the overlap.
     * More specifically, the next emulatable pattern is chosen on a biggest-first basis.
     * The occurrences of the chosen pattern are picked on a first-come, first-served principal.
     * All the usage information is returned as part of a {@link UseBlock}.
     * 
     * @param patternEntry An entry of a pattern and its occurrences (non-overlapping as these are replaced).
     * @param patternsAll All found patterns and occurrences (overlap allowed).
     * @return An Entry consisting of the pattern contained in
     * {@code patternEntry} and a {@link UseBlock} that contains the usage
     * information determined for the pattern.
     */
    private static Entry<long[], UseBlock> useNeutralElements(Entry<long[], ObjectArrayList<int[]>> patternEntry,
            final Object2ObjectOpenCustomHashMap<long[], ObjectArrayList<int[]>> patternsAll) {

        long[] pattern = patternEntry.getKey();
        ObjectArrayList<int[]> patternOccurrences = patternEntry.getValue();

        ObjectArrayList<long[]> assignedPatterns = new ObjectArrayList<>();
        ObjectArrayList<ObjectArrayList<int[]>> assignedOccurrences = new ObjectArrayList<>();
        IntAVLTreeSet replacedNodes = new IntAVLTreeSet(); //Nodes that are replaceed

        //Heuristic: first pick own occurrences.
        assignedOccurrences.add(patternOccurrences);
        Utils.addInvolvedOpNodes(patternOccurrences, replacedNodes);

        //Find patterns of interest (emulatable)
        Object2ObjectOpenCustomHashMap<long[], ObjectArrayList<int[]>> patternsOfInterest
                = new Object2ObjectOpenCustomHashMap(new ArrayLongHashStrategy());
        NeutralFinder finder = new NeutralFinder();
        Object2ObjectOpenCustomHashMap<long[], EmulatableBlock> emulatablePatterns = finder.getEmulatablePatternsMap(pattern);

        //Only keep emulatable patterns of which replacement saves costs.
        for (Map.Entry<long[], EmulatableBlock> pEntry : emulatablePatterns.entrySet()) {
            long[] p = pEntry.getKey();
            ObjectArrayList<int[]> pOcc = patternsAll.get(p);
            if (pOcc != null && Utils.patternOccurrenceCost(p, 1) > Utils.patternBlockCost(pEntry.getValue(), 1)) {
                patternsOfInterest.put(p, pOcc);
            }
        }

        //Heuristic: pick next best pattern of the emulatable patterns
        //Remove overlap with choosen + internally
        while (!patternsOfInterest.isEmpty()) {
            Entry<long[], ObjectArrayList<int[]>>[] patterns = OperationUtils.removeOverlap(patternsOfInterest, replacedNodes);

            //Sort & pick
            Optional<Entry<long[], ObjectArrayList<int[]>>> optBest = Stream.of(patterns)
                    //.sorted(new EntryDynCostCom(emulatablePatterns, false)) //false = high to low profit size
                    //.sorted(new EntryOccCountCom(true)) //true = low to high #occurrences
                    .sorted(new EntryPatternSizeCom(false)) //false = high to low pattern size
                    .findFirst();

            if (optBest.isPresent()) {
                Entry<long[], ObjectArrayList<int[]>> best = optBest.get();
                patternsOfInterest.remove(best.getKey());

                if (!best.getValue().isEmpty()) { 
                    //Add as replaced
                    assignedPatterns.add(best.getKey());
                    assignedOccurrences.add(best.getValue());
                    Utils.addInvolvedOpNodes(best.getValue(), replacedNodes);
                }
            } else {
                break;
            }
        }

        UseBlock block = new UseBlock();
        block.patternP = pattern;
        block.patterns = new EmulatableBlock[assignedPatterns.size()];

        for (int i = 0; i < assignedPatterns.size(); i++) {
            block.patterns[i] = emulatablePatterns.get(assignedPatterns.get(i));
        }

        assignedOccurrences.trim();
        block.occurrences = assignedOccurrences;
        block.calculateProfit();

        return new AbstractObject2ObjectMap.BasicEntry<>(pattern, block);
    }

}
