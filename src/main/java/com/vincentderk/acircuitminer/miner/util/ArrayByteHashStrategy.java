package com.vincentderk.acircuitminer.miner.util;

import it.unimi.dsi.fastutil.Hash;
import java.util.Arrays;

/**
 * Hashing strategy for {@code byte[]} arrays.
 *
 * @author Vincent Derkinderen
 * @version 1.0
 */
public class ArrayByteHashStrategy implements Hash.Strategy<byte[]> {

    @Override
    public int hashCode(byte[] o) {
        return Arrays.hashCode(o);
    }

    @Override
    public boolean equals(byte[] a, byte[] b) {
        return Arrays.equals(a, b);
    }
}
