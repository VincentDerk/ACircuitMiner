package com.vincentderk.acircuitminer.miner.util.comparators;

import com.vincentderk.acircuitminer.miner.MOSR;
import static com.vincentderk.acircuitminer.miner.MOSR.patternProfit;
import com.vincentderk.acircuitminer.miner.StateMultiOutput;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.Comparator;

/**
 * Comparator to sort based on the amount of savings a pattern provides.
 * <br>The savings are calculated based on the energy saved by replacing one
 * occurrence of the pattern, multiplied by the amount of occurrences (value of
 * the entry object). This uses
 * {@link MOSR#patternProfitState(long[], ObjectArrayList)}.
 *
 * @author Vincent Derkinderen
 * @version 2.0
 */
public class EntryProfitStateMOSRCom implements Comparator<Object2ObjectMap.Entry<long[], ObjectArrayList<StateMultiOutput>>> {

    /**
     * Compares pattern entries where the key is the pattern and the value is
     * the list of occurrences. This sort from low to high.
     */
    public EntryProfitStateMOSRCom() {
        this.lowToHigh = true;
    }

    /**
     * Compares pattern entries where the key is the pattern and the value is
     * the list of occurrences.
     *
     * @param lowToHigh Whether to sort from low to high or from high to low.
     */
    public EntryProfitStateMOSRCom(boolean lowToHigh) {
        this.lowToHigh = lowToHigh;
    }

    private final boolean lowToHigh;

    @Override
    public int compare(Object2ObjectMap.Entry<long[], ObjectArrayList<StateMultiOutput>> o1, Object2ObjectMap.Entry<long[], ObjectArrayList<StateMultiOutput>> o2) {
        //Compress profit (vertices-1 * occurence count)
        double o1CompressProfit = patternProfitState(o1.getKey(), o2.getValue());
        double o2CompressProfit = patternProfitState(o1.getKey(), o2.getValue());
        return (lowToHigh) ? Double.compare(o1CompressProfit, o2CompressProfit) : Double.compare(o2CompressProfit, o1CompressProfit);
    }

    /**
     * Get the estimated profit the given pattern (code) gives, multiplied by
     * the amount of occurrences.
     *
     * @param code The pattern to calculate the profit of.
     * @param occurrences The replaceable occurrences of the given pattern.
     * @return The estimated profit of a pattern. This is based on comparing the
     * cost of evaluating the pattern as if only + and * were available hardware
     * components versus the cost of evaluating the pattern as if it was
     * available as one hardware component. The profit is multiplied by the
     * amount of occurrences.
     * @see MOSR#patternProfit(long[], int)
     */
    public static double patternProfitState(long[] code, ObjectArrayList<StateMultiOutput> occurrences) {
        if (!occurrences.isEmpty()) {
            return patternProfit(code, occurrences.size());
        } else {
            return 0;
        }
    }
}
