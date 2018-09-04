package com.vincentderk.acircuitminer.miner.util.comparators;

import com.vincentderk.acircuitminer.miner.SOSR;
import com.vincentderk.acircuitminer.miner.emulatable.neutralfinder.EmulatableBlock;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenCustomHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.Comparator;
import java.util.Map;

/**
 * Comparator to sort based on the savings of both patterns based on their
 * {@link EmulatableBlock} given to the constructor.
 *
 * <p>
 * This uses {@link SOSR#patternOccurrenceCost(long[], int)} and
 * {@link SOSR#patternBlockCost(EmulatableBlock, int)} to determine the savings.
 *
 * @author Vincent Derkinderen
 * @version 1.0
 */
public class EntryDynCostCom implements Comparator<Map.Entry<long[], ObjectArrayList<int[]>>> {

    /**
     * Compares pattern entries where the key is the pattern and the value is
     * the emulatable information object.This sort from low to high.
     *
     * @param emulatablePatterns The mapping to extract the emulatableBlocks
     * from.
     */
    public EntryDynCostCom(Object2ObjectOpenCustomHashMap<long[], EmulatableBlock> emulatablePatterns) {
        this.emulatablePatterns = emulatablePatterns;
        this.lowToHigh = true;
    }

    /**
     * Compares pattern entries where the key is the pattern and the value is
     * the emulatable information object.
     *
     * @param emulatablePatterns The mapping to extract the emulatableBlocks
     * from.
     * @param lowToHigh Whether to sort from low to high or from high to low.
     */
    public EntryDynCostCom(Object2ObjectOpenCustomHashMap<long[], EmulatableBlock> emulatablePatterns, boolean lowToHigh) {
        this.emulatablePatterns = emulatablePatterns;
        this.lowToHigh = lowToHigh;
    }

    private final boolean lowToHigh;
    private final Object2ObjectOpenCustomHashMap<long[], EmulatableBlock> emulatablePatterns;

    @Override
    public int compare(Map.Entry<long[], ObjectArrayList<int[]>> o1, Map.Entry<long[], ObjectArrayList<int[]>> o2) {
        long[] o1Pattern = o1.getKey();
        EmulatableBlock o1EmulatedBlock = emulatablePatterns.get(o1Pattern);

        long[] o2Pattern = o2.getKey();
        EmulatableBlock o2EmulatedBlock = emulatablePatterns.get(o2Pattern);

        double o1CompressProfit = (SOSR.patternOccurrenceCost(o1Pattern, 1) - SOSR.patternBlockCost(o1EmulatedBlock, 1)) * o1.getValue().size();
        double o2CompressProfit = (SOSR.patternOccurrenceCost(o2Pattern, 1) - SOSR.patternBlockCost(o2EmulatedBlock, 1)) * o2.getValue().size();
        return (lowToHigh) ? Double.compare(o1CompressProfit, o2CompressProfit) : Double.compare(o2CompressProfit, o1CompressProfit);
    }
}
