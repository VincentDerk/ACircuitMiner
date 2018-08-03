package com.vincentderk.acircuitminer.miner.enumerators;

import com.vincentderk.acircuitminer.miner.Graph;
import com.vincentderk.acircuitminer.miner.StateExpandable;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenCustomHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

/**
 * Represents an enumerator that can return a set of expandable states which can
 * further be expanded. This is can be used to extend an approach that finds all
 * patterns up to a certain size with an heuristic search that will further
 * expand some states.
 *
 * @author Vincent Derkinderen
 * @version 2.0
 */
public interface ExpandableEnumerator extends Enumerator {

    /**
     * Get the encountered states that can still be expanded.
     *
     * @return The states that can still be expanded.
     */
    public Object2ObjectOpenCustomHashMap<long[], ? extends ObjectArrayList<? extends StateExpandable>> getExpandableStates();

    
    public Object2ObjectOpenCustomHashMap<long[], ObjectArrayList<int[]>> enumerate(Graph g, int[] k, int maxPorts, int xBest, boolean verbose);
}
