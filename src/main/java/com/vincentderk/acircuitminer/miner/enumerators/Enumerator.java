package com.vincentderk.acircuitminer.miner.enumerators;

import com.vincentderk.acircuitminer.miner.Graph;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenCustomHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

/**
 *
 * @author Vincent Derkinderen
 */
public interface Enumerator {

    /**
     * Enumerate all patterns in the given graph and store the occurrences with
     * the found pattern in a map which is returned.
     *
     * @param g The back-end graph
     * @param k The maximum pattern size, equal to the amount of internal nodes
     * (so excluding input nodes).
     * @param maxPorts The maximum amount of ports that a pattern may have.
     * @return Mapping of patterns to lists of occurrences of that pattern.
     */
    public Object2ObjectOpenCustomHashMap<long[], ObjectArrayList<int[]>> enumerate(Graph g, int k, int maxPorts);

}
