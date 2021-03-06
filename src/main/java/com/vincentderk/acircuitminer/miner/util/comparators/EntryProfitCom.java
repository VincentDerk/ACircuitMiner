package com.vincentderk.acircuitminer.miner.util.comparators;

import com.vincentderk.acircuitminer.miner.SOSR;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.Comparator;
import java.util.Map;

/**
 * Comparator to sort based on the amount of savings a pattern provides.
 * <br> The savings are calculated based on the energy saved by replacing one
 * occurrence of the pattern, multiplied by the amount of occurrences (value of
 * the entry object). This uses {@link SOSR#patternProfit(long[], int)}.
 *
 * @author Vincent Derkinderen
 * @version 1.0
 */
public class EntryProfitCom implements Comparator<Map.Entry<long[], ObjectArrayList<int[]>>> {

    /**
     * Compares pattern entries where the key is the pattern and the value is
     * the list of occurrences. This sort from low to high.
     */
    public EntryProfitCom() {
        this.lowToHigh = true;
    }

    /**
     * Compares pattern entries where the key is the pattern and the value is
     * the list of occurrences.
     *
     * @param lowToHigh Whether to sort from low to high or from high to low.
     */
    public EntryProfitCom(boolean lowToHigh) {
        this.lowToHigh = lowToHigh;
    }

    private final boolean lowToHigh;

    @Override
    public int compare(Map.Entry<long[], ObjectArrayList<int[]>> o1, Map.Entry<long[], ObjectArrayList<int[]>> o2) {
        double o1CompressProfit = SOSR.patternProfit(o1.getKey(), o1.getValue());
        double o2CompressProfit = SOSR.patternProfit(o2.getKey(), o2.getValue());
        return (lowToHigh) ? Double.compare(o1CompressProfit, o2CompressProfit) : Double.compare(o2CompressProfit, o1CompressProfit);
        //return Long.compare(o1.getValue().size(), o2.getValue().size()); //Sort on occ size
    }
}
