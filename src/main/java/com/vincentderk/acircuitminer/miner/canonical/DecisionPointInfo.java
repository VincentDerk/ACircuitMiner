package com.vincentderk.acircuitminer.miner.canonical;

/**
 * Stores information regarding the decision made in the depth-first approach of
 * canonical labeling,
 * {@link EdgeCanonical#minCanonicalPermutationDFS(Graph, State)}. It stores
 * information used to go forward into the next branch and to roll backwards,
 * rolling back the decision made by this point.
 *
 * @author Vincent Derkinderen
 * @version 1.0
 */
public class DecisionPointInfo {

    //Store forward and backward info
    //Forward
    int[] assign;
    int[] assignShared;
    int[] UANI; //Beware: filler elements at end. Actual length = assign.length
    int[] SHARED; //Beware: filler elements at end. Actual length = assignShared.length
    int newN;

    //Backward
    int extension; //put back in front and take away assign.length away from the back.
    int n; //old n

    //Current info
    //long[] extra;
}
