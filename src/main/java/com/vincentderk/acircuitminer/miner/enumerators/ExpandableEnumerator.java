package com.vincentderk.acircuitminer.miner.enumerators;

import com.vincentderk.acircuitminer.miner.State;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenCustomHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

/**
 * Represents an enumerator that can return a set of expandable states which can
 * further be expanded. This is can be used to extend an approach that finds all
 * patterns up to a certain size with an heuristic search that will further
 * expand some states.
 *
 * @author Vincent Derkinderen
 * @version 1.0
 */
public interface ExpandableEnumerator {

    /**
     * Get the encountered states that can still be expanded.
     *
     * @return The states that can still be expanded.
     */
    public Object2ObjectOpenCustomHashMap<long[], ObjectArrayList<State>> getExpandableStates();
}
