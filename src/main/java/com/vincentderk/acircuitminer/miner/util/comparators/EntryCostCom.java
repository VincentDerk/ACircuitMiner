package com.vincentderk.acircuitminer.miner.util.comparators;

import static com.vincentderk.acircuitminer.miner.util.Utils.patternOccurrenceCost;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.Comparator;
import java.util.Map;

/**
 * Comparator to sort based on the {@code patternOccurrenceCost(long[], int)}.
 *
 * @author Vincent Derkinderen
 * @version 1.0
 */
public class EntryCostCom implements Comparator<Map.Entry<long[], ObjectArrayList<int[]>>> {

    /**
     * Compares pattern entries where the key is the pattern and the value is
     * the list of occurrences. This sort from low to high.
     */
    public EntryCostCom() {
        this.lowToHigh = true;
    }

    /**
     * Compares pattern entries where the key is the pattern and the value is
     * the list of occurrences.
     *
     * @param lowToHigh Whether to sort from low to high or from high to low.
     */
    public EntryCostCom(boolean lowToHigh) {
        this.lowToHigh = lowToHigh;
    }

    private final boolean lowToHigh;

    @Override
    public int compare(Map.Entry<long[], ObjectArrayList<int[]>> o1, Map.Entry<long[], ObjectArrayList<int[]>> o2) {
        double o1CompressProfit = patternOccurrenceCost(o1.getKey(), o1.getValue().size());
        double o2CompressProfit = patternOccurrenceCost(o2.getKey(), o2.getValue().size());
        return (lowToHigh) ? Double.compare(o1CompressProfit, o2CompressProfit) : Double.compare(o2CompressProfit, o1CompressProfit);
    }
}
