package com.vincentderk.acircuitminer.miner.util.comparators;

import com.vincentderk.acircuitminer.miner.util.Utils;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.Comparator;
import java.util.Map;

/**
 * Comparator to sort based on the amount of internal operation nodes each
 * pattern has.
 *
 * @author Vincent Derkinderen
 * @version 1.0
 */
public class EntryPatternSizeCom implements Comparator<Map.Entry<long[], ObjectArrayList<int[]>>> {

    /**
     * Compares pattern entries where the key is the pattern and the value is
     * the list of occurrences. This sort from low to high.
     */
    public EntryPatternSizeCom() {
        this.lowToHigh = true;
    }

    /**
     * Compares pattern entries where the key is the pattern and the value is
     * the list of occurrences.
     *
     * @param lowToHigh Whether to sort from low to high or from high to low.
     */
    public EntryPatternSizeCom(boolean lowToHigh) {
        this.lowToHigh = lowToHigh;
    }

    private final boolean lowToHigh;

    @Override
    public int compare(Map.Entry<long[], ObjectArrayList<int[]>> o1, Map.Entry<long[], ObjectArrayList<int[]>> o2) {
        long o1Value;
        long o2Value;

        if (o1.getValue().isEmpty()) {
            o1Value = Utils.operationNodeCount(o1.getKey());
        } else {
            o1Value = o1.getValue().get(0).length;
        }

        if (o2.getValue().isEmpty()) {
            o2Value = Utils.operationNodeCount(o2.getKey());
        } else {
            o2Value = o2.getValue().get(0).length;
        }

        return (lowToHigh) ? Long.compare(o1Value, o2Value) : Long.compare(o2Value, o1Value);
    }
}
