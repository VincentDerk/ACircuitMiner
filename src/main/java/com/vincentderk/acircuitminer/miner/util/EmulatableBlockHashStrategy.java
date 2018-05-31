package com.vincentderk.acircuitminer.miner.util;

import com.vincentderk.acircuitminer.miner.emulatable.neutralfinder.EmulatableBlock;
import it.unimi.dsi.fastutil.Hash;
import java.util.Arrays;

/**
 * Hashing strategy for {@link EmulatableBlock EmulatableBlocks}.
 *
 * @author Vincent Derkinderen
 * @version 1.0
 */
public class EmulatableBlockHashStrategy implements Hash.Strategy<EmulatableBlock> {

    @Override
    public int hashCode(EmulatableBlock o) {
        return Arrays.hashCode(o.emulatedCode);
    }

    @Override
    public boolean equals(EmulatableBlock a, EmulatableBlock b) {
        return Arrays.equals(a.emulatedCode, b.emulatedCode);
    }
}
