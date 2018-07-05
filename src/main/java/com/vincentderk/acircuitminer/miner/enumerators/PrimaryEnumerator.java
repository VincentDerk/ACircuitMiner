package com.vincentderk.acircuitminer.miner.enumerators;

import com.vincentderk.acircuitminer.miner.Graph;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenCustomHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

/**
 * An enumerator that performs a complete search and finds all patterns (of a specified kind) up to a
 * given size, along with their occurrences.
 *
 * @author Vincent Derkinderen
 * @version 2.0
 */
public interface PrimaryEnumerator extends ExpandableEnumerator {

    /**
     * Enumerate all patterns in the given graph and store the occurrences with
     * the found pattern in map which is returned.
     *
     * @param g The backend graph
     * @param k The maximum pattern size, equal to the amount of internal nodes
     * (so excluding input nodes).
     * @param maxPorts The maximum amount of ports that a pattern may have.
     * @param expandAfterFlag Denotes whether the enumerator should keep track
     * of the expandableStates. When set to true, a list of expandable
     * occurrences is kept such that they can later be retrieved
     * ({@link #getExpandableStates()}) and expanded further. If this is the
     * last enumeration of the process and you do not want to keep track of the
     * further expandable occurrences, set to false. Otherwise use true.
     * @return Mapping of patterns to lists of occurrences of that pattern.
     */
    public Object2ObjectOpenCustomHashMap<long[], ObjectArrayList<int[]>> enumerate(Graph g, int k, int maxPorts, boolean expandAfterFlag);

}
