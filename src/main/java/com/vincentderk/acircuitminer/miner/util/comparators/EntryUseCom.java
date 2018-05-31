package com.vincentderk.acircuitminer.miner.util.comparators;

import com.vincentderk.acircuitminer.miner.emulatable.neutralfinder.UseBlock;
import java.util.Comparator;
import java.util.Map;

/**
 * Comparator to sort based on the amount of profit in each {@link UseBlock}.
 *
 * @author Vincent Derkinderen
 * @version 1.0
 */
public class EntryUseCom implements Comparator<Map.Entry<long[], UseBlock>> {

    /**
     * Compares pattern entries where the key is the pattern and the value is
     * the list of occurrences. This sort from low to high.
     */
    public EntryUseCom() {
        this.lowToHigh = true;
    }

    /**
     * Compares pattern entries where the key is the pattern and the value is
     * the list of occurrences.
     *
     * @param lowToHigh Whether to sort from low to high or from high to low.
     */
    public EntryUseCom(boolean lowToHigh) {
        this.lowToHigh = lowToHigh;
    }

    private final boolean lowToHigh;

    @Override
    public int compare(Map.Entry<long[], UseBlock> o1, Map.Entry<long[], UseBlock> o2) {
        double o1CompressProfit = o1.getValue().profit;
        double o2CompressProfit = o2.getValue().profit;
        return (lowToHigh) ? Double.compare(o1CompressProfit, o2CompressProfit) : Double.compare(o2CompressProfit, o1CompressProfit);
        //return Long.compare(o1.getValue().size(), o2.getValue().size()); //Sort on occ size
    }
}
