package com.vincentderk.acircuitminer.miner.enumerators;

import com.vincentderk.acircuitminer.miner.StateSingleOutput;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenCustomHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.Map.Entry;

/**
 *
 * An enumerator that performs its search on the expandable occurrences of a
 * previous {@link ExpandableEnumerator}. This can be used to heuristically only
 * extend certain occurrences.
 *
 * @author Vincent Derkinderen
 * @version 2.0
 */
public interface SecondaryEnumerator extends ExpandableEnumerator {

    /**
     * Extend the patternsMap by extending the occurrences of the
     * expandablePatterns in the patternsMap. Both patternsMap and
     * expandablePatterns will be modified.
     *
     * @param patternsMap The map of patterns to extend to. Will be modified.
     * @param expandableStates The states which we want to expand. Will be
     * modified.
     * @param k The maximum pattern size, equal to the amount of internal nodes
     * (so excluding input nodes).
     * @param maxPorts The maximum amount of ports that an occurrence ({@link State}) may have.
     * @param expandAfterFlag Denotes whether the enumerator should keep track
     * of the expandableStates. If this is the last enumeration of the process,
     * set to false. Otherwise use true.
     * @param prevK The k from which to start adding the found occurrences to
     * the patternsMap.
     */
    public void expandSelectedPatterns(Object2ObjectOpenCustomHashMap<long[], ObjectArrayList<int[]>> patternsMap,
            Entry<long[], ObjectArrayList<StateSingleOutput>>[] expandableStates, int k,
            int maxPorts, boolean expandAfterFlag, int prevK);
}
