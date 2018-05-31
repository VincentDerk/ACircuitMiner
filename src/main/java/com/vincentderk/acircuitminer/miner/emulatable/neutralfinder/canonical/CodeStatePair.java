package com.vincentderk.acircuitminer.miner.emulatable.neutralfinder.canonical;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;

/**
 *
 * Data structure containing the code of a pattern and the revAllocation that was used to obtain it.
 * An element (x,y) of revAllocation denotes that node x has been allocated y (codeId) to obtain the code.
 * 
 * @author Vincent Derkinderen
 * @version 1.0
 */
public class CodeStatePair {
    
    /**
     * The generated code
     */
    public long[] code;
    
    /**
     * revAllocation = (x,y) = node x in the graph has been allocated y (codeId)
     * Only the arithmetic nodes and shared input nodes are present.
     */
    public Int2IntOpenHashMap revAllocation;
    
}
