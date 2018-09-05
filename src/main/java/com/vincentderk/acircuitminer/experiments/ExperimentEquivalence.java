package com.vincentderk.acircuitminer.experiments;

import com.vincentderk.acircuitminer.miner.util.Utils;
import com.google.common.base.Stopwatch;
import com.vincentderk.acircuitminer.miner.Graph;
import com.vincentderk.acircuitminer.miner.Miner;
import com.vincentderk.acircuitminer.miner.SOSR;
import com.vincentderk.acircuitminer.miner.canonical.EdgeCanonical;
import com.vincentderk.acircuitminer.miner.util.ArrayLongHashStrategy;
import com.vincentderk.acircuitminer.miner.util.comparators.EntryPatternSizeCom;
import com.vincentderk.acircuitminer.miner.util.comparators.EntryUseCom;
import com.vincentderk.acircuitminer.miner.util.OperationUtils;
import com.vincentderk.acircuitminer.miner.emulatable.neutralfinder.EmulatableBlock;
import com.vincentderk.acircuitminer.miner.emulatable.neutralfinder.NeutralFinder;
import com.vincentderk.acircuitminer.miner.emulatable.neutralfinder.UseBlock;
import com.vincentderk.acircuitminer.miner.util.verification.EquivalenceChecker;
import it.unimi.dsi.fastutil.ints.IntAVLTreeSet;
import it.unimi.dsi.fastutil.objects.AbstractObject2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenCustomHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.shorts.Short2ObjectAVLTreeMap;
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
 * Runs the {@link NeutralFinderExperiment} and replaces the occurrences that
 * the best pattern can replace. After replacement, the functional
 * {@link EquivalenceChecker equivalence} is of before and after is checked.
 * This should provide more guarantees that the current replacement is correct.
 * However, currently the canonical labeling that is used is not efficient
 * enough to perform this in a short time span for large graphs (1000+ nodes).
 *
 * <p>
 * Focuses on Single Output Single Root ({@link SOSR}) patterns.
 *
 * @author Vincent Derkinderen
 * @version 2.0
 */
public class ExperimentEquivalence {

    /**
     * {@link NeutralFinderExperiment} + test
     * {@link EquivalenceChecker equivalence} of before and after replacement.
     *
     * @param args the command line arguments
     * @throws java.io.FileNotFoundException
     */
    public static void main(String[] args) throws FileNotFoundException, IOException {
        System.out.println("Running ExperimentEquivalence");
        boolean verbose = false;
        String basePath = "D://Thesis//Nets//";
        String ac = "small_asia";
        String path = basePath + ac + ".net.ac";
        int[] k = {9};
        int maxPorts = 16;
        int xBest = 10;

        // Load graph
        Stopwatch stopwatch = Stopwatch.createStarted();
        Graph g = Utils.readACStructure(new FileReader(path));
        System.out.printf("Graph loaded in %s msecs.\n", stopwatch.elapsed(TimeUnit.MILLISECONDS));
        Graph originalGraph = g.clone();
        long totalGraphCost = g.getTotalCosts();

        // Find patterns
        stopwatch.reset().start();
        Object2ObjectOpenCustomHashMap<long[], ObjectArrayList<int[]>> patternsAll = Miner.executeRaw(g, k, verbose, maxPorts, xBest);
        System.out.printf("Executed total algorithm in %s secs.\n", stopwatch.elapsed(TimeUnit.SECONDS));

        //Decide best pattern
        Entry<long[], ObjectArrayList<int[]>>[] patterns = OperationUtils.removeOverlap(patternsAll, null);
        Entry<long[], UseBlock>[] patternUseBlocks = Stream.of(patterns).parallel() //TODO: parallel vs sequential
                .map(x -> useNeutralElements(x, patternsAll))
                .sorted(new EntryUseCom(false)) //true = low to high savings
                .toArray(Map.Entry[]::new);

        // -- Debug --
        double opNodeCount = g.getOperationNodeCount();
        System.out.println("");
        System.out.println("Amount of arithmetic nodes in AC: " + opNodeCount);
        System.out.println("Used format: - (<operation count of pattern>|<coverage of AC>%) <occurrence count> of <pattern>");
        DecimalFormat dec = new DecimalFormat("#0.000");
        Entry<long[], UseBlock> entry = patternUseBlocks[0];
        int opCountOfP = SOSR.getOperationNodeCount(entry.getKey());
        int inputCountOfP = SOSR.getNodeCount(entry.getKey()) - opCountOfP;
        double relativeSavings = entry.getValue().profit / totalGraphCost * 100;
        System.out.println("");
        System.out.println("Pattern: " + EdgeCanonical.printCode(entry.getKey()));
        System.out.println("Amount of operations|input: " + opCountOfP + "|" + inputCountOfP);
        System.out.println("Savings: " + entry.getValue().profit + " pJ / " + totalGraphCost + " pJ (" + dec.format(relativeSavings) + "%)");

        EmulatableBlock[] usedPatterns = entry.getValue().patterns;
        ObjectArrayList<ObjectArrayList<int[]>> usedOccurrences = entry.getValue().occurrences;
        int totalNodeCount = 0;

        int occCount0 = usedOccurrences.get(0).size();
        String code0 = EdgeCanonical.printCode(entry.getValue().patternP);
        int opCountPerOcc0 = SOSR.getOperationNodeCount(entry.getValue().patternP);
        int nodeCount0 = opCountPerOcc0 * occCount0;
        totalNodeCount += nodeCount0;
        double relNodeCount0 = (nodeCount0 / opNodeCount) * 100;

        System.out.println("- " + "(" + opCountPerOcc0 + "|" + dec.format(relNodeCount0) + "%) " + occCount0 + " of " + code0);

        for (int i = 0; i < usedPatterns.length; i++) {
            int occCount = usedOccurrences.get(i + 1).size();
            String code = EdgeCanonical.printCode(usedPatterns[i].emulatedCode);
            int opCountPerOcc = SOSR.getOperationNodeCount(usedPatterns[i].emulatedCode);
            int nodeCount = opCountPerOcc * occCount;
            totalNodeCount += nodeCount;
            double relNodeCount = (nodeCount / opNodeCount) * 100;

            System.out.println("- " + "(" + opCountPerOcc + "|" + dec.format(relNodeCount) + "%) " + occCount + " of " + code);
        }
        System.out.println("Total node coverage: " + dec.format(totalNodeCount / opNodeCount * 100) + "%");
        System.out.println("");

        // -- Replacement --
        Entry<long[], UseBlock> best = patternUseBlocks[0];
        UseBlock useBlock = best.getValue();
        short newOp = Graph.HIGHEST_OP + 1;
        OperationUtils.replace(g, useBlock, newOp);

        // -- Equivalence --
        EquivalenceChecker eq = new EquivalenceChecker();
        Short2ObjectAVLTreeMap pMap = new Short2ObjectAVLTreeMap();
        pMap.put(newOp, best.getKey());
        boolean equivalent = eq.isEquivalent(originalGraph, g, pMap);
        System.out.println(equivalent);
    }

    /**
     * Determine a possible usage for the pattern P contained in
     * {@code patternEntry}.
     * <p>
     * First, the occurrences of the pattern are replaced
     * ({@code patternEntry.getValue()}). Then, {@link NeutralFinder} is used to
     * obtain all patterns that are emulatable by P. The occurrences of these
     * emulatable patterns are also replaced, keeping in mind the overlap. More
     * specifically, the next emulatable pattern is chosen on a biggest-first
     * basis. The occurrences of the chosen pattern are picked on a first-come,
     * first-served principal. All the usage information is returned as part of
     * a {@link UseBlock}.
     *
     * @param patternEntry An entry of a pattern and its occurrences
     * (non-overlapping as these are replaced).
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

        for (Map.Entry<long[], EmulatableBlock> pEntry : emulatablePatterns.entrySet()) {
            long[] p = pEntry.getKey();
            ObjectArrayList<int[]> pOcc = patternsAll.get(p);
            if (pOcc != null && SOSR.patternOccurrenceCost(p, 1) > SOSR.patternBlockCost(pEntry.getValue(), 1)) {
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
