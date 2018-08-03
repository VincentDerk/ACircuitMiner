package com.vincentderk.acircuitminer.miner.util.comparators;

import com.vincentderk.acircuitminer.miner.StateSingleOutput;
import static com.vincentderk.acircuitminer.miner.util.Utils.patternProfitState;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.Comparator;

/**
 * Comparator to sort based on the amount of savings a pattern provides. The
 * savings is calculated based on the energy saved by replacing one occurrence
 * of the pattern, multiplied by the amount of occurrences in the value of the
 * pattern's entry
 * ({@link com.vincentderk.acircuitminer.miner.util.Utils#patternProfitState(long[], ObjectArrayList)}).
 *
 * @author Vincent Derkinderen
 * @version 2.0
 */
public class EntryProfitStateCom implements Comparator<Object2ObjectMap.Entry<long[], ObjectArrayList<StateSingleOutput>>> {

    /**
     * Compares pattern entries where the key is the pattern and the value is
     * the list of occurrences. This sort from low to high.
     */
    public EntryProfitStateCom() {
        this.lowToHigh = true;
    }

    /**
     * Compares pattern entries where the key is the pattern and the value is
     * the list of occurrences.
     *
     * @param lowToHigh Whether to sort from low to high or from high to low.
     */
    public EntryProfitStateCom(boolean lowToHigh) {
        this.lowToHigh = lowToHigh;
    }

    private final boolean lowToHigh;

    @Override
    public int compare(Object2ObjectMap.Entry<long[], ObjectArrayList<StateSingleOutput>> o1, Object2ObjectMap.Entry<long[], ObjectArrayList<StateSingleOutput>> o2) {
        //Compress profit (vertices-1 * occurence count)
        double o1CompressProfit = patternProfitState(o1.getKey(), o2.getValue());
        double o2CompressProfit = patternProfitState(o1.getKey(), o2.getValue());
        return (lowToHigh) ? Double.compare(o1CompressProfit, o2CompressProfit) : Double.compare(o2CompressProfit, o1CompressProfit);
    }
}
