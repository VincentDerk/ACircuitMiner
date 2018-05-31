package com.vincentderk.acircuitminer.miner.emulatable.neutralfinder.canonical;

import com.vincentderk.acircuitminer.miner.Graph;
import com.vincentderk.acircuitminer.miner.emulatable.neutralfinder.NeutralState;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;

/**
 * This data structure is used during the canonical labeling in
 * {@link NeutralEdgeCanonical}. It is analogous to
 * {@link com.vincentderk.acircuitminer.miner.canonical.CanonicalState} used by
 * {@link com.vincentderk.acircuitminer.miner.canonical.EdgeCanonical} but with
 * a change in {@link #expand(Graph)}.
 *
 * @author Vincent Derkinderen
 * @version 1.0
 */
public class CanonicalNeutralState implements Comparable<CanonicalNeutralState> {

    /**
     * The state of which this is a (intermediate) canonical state.
     */
    NeutralState state;

    /**
     * revAllocation = (x,y) = node x has been allocated y (codeId)
     */
    Int2IntOpenHashMap revAllocation;

    /**
     * Extensions possible (nodes), sorted from first to last allocated number.
     */
    int[] extensions;

    /**
     * The next assignable number
     */
    int n;

    /**
     * Encoding of the extra edges - excluding the previous code.
     */
    long[] code;

    /**
     * The initial state where the root of the given state has been assigned 0
     * and the the children of that root node will be the next to be assigned a
     * value.
     *
     * @param state state of which this is an (intermediate) canonical state.
     */
    public CanonicalNeutralState(NeutralState state) {
        this.state = state;
        this.revAllocation = new Int2IntOpenHashMap();
        this.revAllocation.put(state.getRealRoot(), 0);

        this.extensions = new int[1];
        this.extensions[0] = state.getRealRoot();
        this.n = 1;
    }

    /**
     * Create a new state with the given arguments.
     *
     * @param state The state to set.
     * @param revAllocation The revAllocation so far.
     * @param extensions The nodes to extend next, in order.
     * @param code The code that resulted from the last extension.
     * @param n The next id to assign to a node.
     */
    private CanonicalNeutralState(NeutralState state, Int2IntOpenHashMap revAllocation, int[] extensions, long[] code, int n) {
        this.state = state;
        this.revAllocation = revAllocation;
        this.extensions = extensions;
        this.code = code;
        this.n = n;
    }

    @Override
    public CanonicalNeutralState clone() {
        Int2IntOpenHashMap n_allocation = this.revAllocation.clone();
        int[] n_extensions = this.extensions.clone();
        long[] n_code = this.code.clone();

        return new CanonicalNeutralState(this.state, n_allocation, n_extensions, n_code, this.n);
    }

    /**
     * Expand the current intermediate canonical neutral state to a more
     * complete one. The code that is contained by the returned states is the
     * code additional code resulting from the performed extension.
     *
     * This implementation is not suited for nodes that have more than one
     * direct connection to the same node. It may assign the same node two
     * different id's or only detect one of the connections.
     *
     * @param g The graph wherein we expand this structure.
     * @return The list of expanded canonical neutral states.
     */
    public Collection<CanonicalNeutralState> expand(Graph g) {
        ArrayList<CanonicalNeutralState> results = new ArrayList<>();
        int node = extensions[0];
        int currentNode = revAllocation.get(node);
        long expandNode = (long) currentNode << 32;
        long[] extra = new long[g.inc[node].length + 1]; //extra code
        final int NULLVAL = revAllocation.defaultReturnValue();

        int aaIndex = 0; //The first index without element. Also = the length
        int[] UANI = new int[g.inc[node].length];
        int[] SHARED = new int[g.inc[node].length];
        int uaniIndex = 0; //The first index without element. Also = the length
        int sharedIndex = 0; //The first index without element. Also = the length
        int totalChildCount = 0;
        //Insert the already assigned (no input) children (AA) into the code.
        //Fill in array of unassigned non input children (UANI).
        //TODO: BinarySearch could be avoid since g.inc and vertices are both sorted ?
        for (int y : g.inc[node]) {
            int x = state.originValue[y];
            //Note: there are at least 2 useful (2) childs. If there was only 1,
            //we would be looking at that node as it is the origin.
            //If there was none, then the value of node would be 0,1 or 3 and it would be ignored by the node above it.

            if (state.options[x] == 4) { //4 = (1 << 2)
                totalChildCount++;

                int assigned = revAllocation.get(x);
                if (assigned != NULLVAL) { //(AA)
                    extra[aaIndex++] = (long) expandNode | assigned;
                } else if (g.inc[x].length != 0) { //unassigned, No input
                    UANI[uaniIndex++] = x;
                } else if (isSharedInput(g, state, x)) { //unassigned, input, Shared input
                    //sharedInput can still be actually non-shared due to other output being irrelevant later on.
                    //This isn't that much of an issue, it will just result in assignments
                    //that will be thrown out when 'discovered' that they are not shared.
                    //(The smallest lexicografische code is the one where non shared input has highest assignments).
                    SHARED[sharedIndex++] = x;
                }
            } // else irrelevant
        }

        //Sort the AA children into correct order.
        Arrays.sort(extra, 0, aaIndex);

        //Add all children excluding the Already Assigned children to the code.
        int new_n = this.n + totalChildCount - aaIndex;
        long baseN = expandNode | this.n;
        for (int i = aaIndex; i < totalChildCount; i++) {
            extra[i] = baseN++;
        }

        //Label in the code
        extra = Arrays.copyOf(extra, totalChildCount + 1);
        extra[extra.length - 1] = Long.MAX_VALUE - g.label[node];

        //Start of the new extension list. Except for the Not yet assigned but 
        //expandable (non input)  children (UANI), every edge is filled in.
        int[] n_extensions_temp = new int[extensions.length - 1 + uaniIndex];
        System.arraycopy(extensions, 1, n_extensions_temp, 0, extensions.length - 1);

        /* Base values */
        if (uaniIndex > 0) {
            //Create base array [n,n+1,n+2,...] which we will permute
            int[] assign = new int[uaniIndex];
            int j = this.n;
            for (int i = 0; i < assign.length; i++) {
                assign[i] = j++; //j = this.n+i
            }

            //Create shared base array [j,j+1,j+2,...] which we will permute
            int[] assignSharedBase = new int[sharedIndex];
            for (int i = 0; i < sharedIndex; i++) {
                assignSharedBase[i] = j++;
            }

            int UANIstartIndex = extensions.length - 1 - this.n;
            //For each permutation of assign; create a state with the assigned values to the children.
            //TODO: This can be improved, we know for some permutation already that they shouldn't be checked.
            do {
                Int2IntOpenHashMap n_inter_revAllocation = this.revAllocation.clone();
                int[] n_extensions = n_extensions_temp.clone();

                for (int i = 0; i < uaniIndex; i++) {
                    n_inter_revAllocation.put(UANI[i], assign[i]);
                    n_extensions[UANIstartIndex + assign[i]] = UANI[i];
                }

                /* All possible permutations of shared input nodes */
                if (sharedIndex > 0) {
                    int[] assignShared = assignSharedBase.clone();
                    do {
                        Int2IntOpenHashMap n_revAllocation = n_inter_revAllocation.clone();

                        for (int i = 0; i < sharedIndex; i++) {
                            n_revAllocation.put(SHARED[i], assignShared[i]);
                        }

                        results.add(new CanonicalNeutralState(this.state, n_revAllocation, n_extensions, extra, new_n));
                    } while ((assignShared = getNextPermutation(assignShared)) != null);
                } else {
                    results.add(new CanonicalNeutralState(this.state, n_inter_revAllocation, n_extensions, extra, new_n));
                }
            } while ((assign = getNextPermutation(assign)) != null);
        } else {

            /* All possible permutations of shared input nodes */
            if (sharedIndex > 0) {
                //Create shared base array [j,j+1,j+2,...] which we will permute
                int j = this.n;
                int[] assignSharedBase = new int[sharedIndex];
                for (int i = 0; i < sharedIndex; i++) {
                    assignSharedBase[i] = j++;
                }

                int[] assignShared = assignSharedBase.clone();
                do {
                    Int2IntOpenHashMap n_revAllocation = revAllocation.clone();

                    for (int i = 0; i < sharedIndex; i++) {
                        n_revAllocation.put(SHARED[i], assignShared[i]);
                    }

                    results.add(new CanonicalNeutralState(this.state, n_revAllocation, n_extensions_temp, extra, new_n));
                } while ((assignShared = getNextPermutation(assignShared)) != null);
            } else {
                results.add(new CanonicalNeutralState(this.state, revAllocation, n_extensions_temp, extra, new_n));
            }
        }

        return results;
    }

    /**
     * Whether x has multiple nodes that use its input in the given state.
     *
     * @param g The graph structure behind this problem.
     * @param state The state in which to test
     * @param x The node x of which we check the users of its output.
     * @return Whether there is more than one user of x.
     */
    public static boolean isSharedInput(Graph g, NeutralState state, int x) {
        switch (g.out[x].length) {
            case 0:
                return false;

            case 1: //Also cover the case where the user is just passing on and is shared
                int user = g.out[x][0];

                if (state.originValue[user] == x) {
                    return isSharedInput(g, state, user);
                } else {
                    return false;
                }

            default: // >1
                return true;
        }
    }

    /**
     * Get the next permutation given the current permutation. The current
     * permutation will be altered in-place, clone beforehand if required.
     * Returns null when there is no next permutation.
     *
     * Implementation of
     * http://en.wikipedia.org/wiki/Permutation#Systematic_generation_of_all_permutations
     * Algorithm to efficiently generate permutations of a sequence until all
     * possibilities are exhausted
     *
     * @param currPerm The current permutation
     * @return null if there is no next permutation, the next permutation
     * otherwise.
     */
    public static int[] getNextPermutation(int[] currPerm) {
        int i, j, l;

        //get maximum index j for which arr[j+1] > arr[j]
        for (j = currPerm.length - 2; j >= 0; j--) {
            if (currPerm[j + 1] > currPerm[j]) {
                break;
            }
        }

        //has reached it's lexicographic maximum value, No more permutations left 
        if (j == -1) {
            return null;
        }

        //get maximum index l for which arr[l] > arr[j]
        for (l = currPerm.length - 1; l > j; l--) {
            if (currPerm[l] > currPerm[j]) {
                break;
            }
        }

        //Swap arr[i],arr[j]
        int swap = currPerm[j];
        currPerm[j] = currPerm[l];
        currPerm[l] = swap;

        //reverse array present after index : j+1 
        for (i = j + 1; i < currPerm.length; i++) {
            if (i > currPerm.length - i + j) {
                break;
            }
            swap = currPerm[i];
            currPerm[i] = currPerm[currPerm.length - i + j];
            currPerm[currPerm.length - i + j] = swap;
        }

        return currPerm;
    }

    @Override
    public int compareTo(CanonicalNeutralState o) {
        int max = Math.min(code.length, o.code.length);
        for (int i = 0; i < max; i++) {
            int r = Long.compare(code[i], o.code[i]);

            if (r != 0) {
                return r;
            }
        }

        return o.code.length - code.length;
    }
}
