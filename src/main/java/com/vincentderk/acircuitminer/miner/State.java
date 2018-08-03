package com.vincentderk.acircuitminer.miner;

/**
 * A data structure to keep track of an induced subgraph. It only stores the id
 * of the nodes involved in the subgraph. The structure itself (connections) can
 * be extracted by combining the information with the backend structure (the
 * graph in context).
 *
 * @author Vincent Derkinderen
 * @version 2.0
 */
public abstract class State {

    /**
     * The vertices that are part of this State. These are in a sorted order so
     * binary search can be used.
     */
    public int[] vertices;

}
