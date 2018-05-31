package com.vincentderk.acircuitminer.miner.util;

import it.unimi.dsi.fastutil.Hash;
import java.util.Arrays;

/**
 * Hashing strategy for {@code long[]} arrays.
 *
 * @author Vincent Derkinderen
 * @version 1.0
 */
public class ArrayLongHashStrategy implements Hash.Strategy<long[]> {

    @Override
    public int hashCode(long[] o) {
        return Arrays.hashCode(o);
    }

    @Override
    public boolean equals(long[] a, long[] b) {
        return Arrays.equals(a, b);
    }
}
