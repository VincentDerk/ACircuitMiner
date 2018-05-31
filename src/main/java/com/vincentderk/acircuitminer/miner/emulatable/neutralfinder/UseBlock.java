package com.vincentderk.acircuitminer.miner.emulatable.neutralfinder;

import com.vincentderk.acircuitminer.miner.util.Utils;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

/**
 * This data structure can be used to keep track of the usage of a pattern P
 * ({@link #patternP}). It can store the patterns that P can emulate
 * ({@link #patterns}) and for each emulated pattern, it can store the list of
 * occurrences we replace ({@link #occurrences}).
 * <p>
 * Usage:
 * First populate {@link #patternP}, {@link #patterns} and {@link #occurrences}.
 * Then call {@link #calculateProfit()} to set the {@link #profit} to the correct value.
 *
 * @author Vincent Derkinderen
 * @version 1.0
 */
public class UseBlock {

    /**
     * The energy saved by replacing the occurrences of the emulated patterns by
     * a call to patternP.
     */
    public double profit;

    /**
     * The pattern used to emulate the rest
     */
    public long[] patternP;

    /**
     * The patterns that can be emulated by {@link #patternP}.
     */
    public EmulatableBlock[] patterns;

    /**
     * The occurrences assigned to be 'used' for the patterns. e.g.
     * {@code occurrences.get(i+1)} represents the list of occurrences of emulated
     * pattern {@code patterns[i]} that can be replaced without conflicting with the
     * occurrences of the other patterns. {@code occurrences.get(0)} represents the list
     * of occurrences of the main pattern that forms the hardware block,
     * {@link #patternP}.
     */
    public ObjectArrayList<ObjectArrayList<int[]>> occurrences;

    /**
     * Set the profit variable to the correct value. This calculation is based
     * on the profit obtained by replacing the occurrences with a call to the
     * hardware block (formed by the pattern of {@code patterns[0]}).
     *
     * It is assumed that it is beneficial to replace all the emulatable
     * patterns in {@link #patterns}. When replacing an emulated pattern is not
     * beneficial, it must not be present.
     * 
     * @see Utils#patternOccurrenceCost(long[], int) 
     * @see Utils#patternBlockCost(EmulatableBlock, int) 
     */
    public void calculateProfit() {
        long totalProfit = 0;

        totalProfit += Utils.patternProfit(patternP, occurrences.get(0).size());

        for (int i = 0; i < patterns.length; i++) {
            int occCount = occurrences.get(i + 1).size();
            double extraProfit = (Utils.patternOccurrenceCost(patterns[i].emulatedCode, 1)
                    - Utils.patternBlockCost(patterns[i], 1)) * occCount;
            
            totalProfit += extraProfit;
        }

        this.profit = totalProfit;
    }

}
