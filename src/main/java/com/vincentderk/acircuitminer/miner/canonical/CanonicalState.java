package com.vincentderk.acircuitminer.miner.canonical;

import com.vincentderk.acircuitminer.miner.Graph;
import com.vincentderk.acircuitminer.miner.State;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;

/**
 * Class used by {@link EdgeCanonical} in performing the canonical labeling of a
 * rooted DAG. It stores information regarding the assignment of ids (0,1,2,..) to the
 * nodes in a graph ({@link State} object).
 *
 * @author Vincent Derkinderen
 * @version 1.0
 */
public class CanonicalState implements Comparable<CanonicalState> {

    /**
     * The state of which this is a (intermediate) canonical state.
     */
    State state;

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
     * The amount of inputs. Input nodes used by multiple others still count as
     * 1.
     */
    int inputCount;

    /**
     * Create a CanonicalState which assigns the root of the given state to 0
     * and sets that root as the next extension of this CanonicalState.
     *
     * @param state state of which this is an (intermediate) canonical state.
     */
    public CanonicalState(State state) {
        this.state = state;
        this.revAllocation = new Int2IntOpenHashMap();
        this.revAllocation.put(state.root, 0);
        this.extensions = new int[1];
        this.extensions[0] = state.root;
        this.n = 1;
        this.inputCount = 0;
    }

    private CanonicalState(State state, Int2IntOpenHashMap revAllocation, int[] extensions, long[] code, int n, int inputCount) {
        this.state = state;
        this.revAllocation = revAllocation;
        this.extensions = extensions;
        this.code = code;
        this.n = n;
        this.inputCount = inputCount;
    }

    @Override
    public CanonicalState clone() {
        Int2IntOpenHashMap n_allocation = this.revAllocation.clone();
        int[] n_extensions = this.extensions.clone();
        long[] n_code = this.code.clone();

        return new CanonicalState(this.state, n_allocation, n_extensions, n_code, this.n, this.inputCount);
    }

    /**
     * Expand the current intermediate canonical state to a more complete one.
     * The code of these states returned is the code that is additional by
     * extending.
     *
     * <p>
     * Beware, this can not handle a node with 2 incomings from the same node.
     * The code may locally classify it as two different nodes and globally as
     * 1.
     *
     * @param g The graph wherein we expand this structure.
     * @return The list of expanded canonical states.
     */
    public Collection<CanonicalState> expand(Graph g) {
        ArrayList<CanonicalState> results = new ArrayList<>();
        int node = extensions[0];
        int currentNode = revAllocation.get(node);
        long expandNode = (long) currentNode << 32;
        long[] extra = new long[g.inc[node].length + 1]; //extra code
        final int NULLVAL = revAllocation.defaultReturnValue();

        int n_inputCount = this.inputCount;

        int aaIndex = 0; //The first index without element. Also = the length
        int[] UANI = new int[g.inc[node].length];
        int[] SHARED = new int[g.inc[node].length];
        int uaniIndex = 0; //The first index without element. Also = the length
        int sharedIndex = 0; //The first index without element. Also = the length

        //Insert the already assigned children (AA) into the code.
        //Fill in array of unassigned non input children (UANI).
        //TODO: BinarySearch could be avoid since g.inc and vertices are both sorted
        for (int x : g.inc[node]) {
            int assigned = revAllocation.get(x);
            if (assigned != NULLVAL) { //(AA)
                extra[aaIndex++] = (long) expandNode | assigned;
            } else if (Arrays.binarySearch(state.vertices, x) >= 0) { //unassigned, No input
                UANI[uaniIndex++] = x;
            } else if (isSharedInput(g, node, x)) { //unassigned, input, Shared input
                SHARED[sharedIndex++] = x;
                n_inputCount++;
            } else {
                n_inputCount++;
            }
            //else nonshared input
        }

        //Sort the AA children into correct order.
        Arrays.sort(extra, 0, aaIndex);

        //Add all children excluding the Already Assigned children to the code.
        int new_n = this.n + extra.length - aaIndex - 1;
        long baseN = expandNode | this.n;
        for (int i = aaIndex; i < extra.length - 1; i++) {
            extra[i] = baseN++;
        }

        //Label in the code
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

                        results.add(new CanonicalState(this.state, n_revAllocation, n_extensions, extra, new_n, n_inputCount));
                    } while ((assignShared = getNextPermutation(assignShared)) != null);
                } else {
                    results.add(new CanonicalState(this.state, n_inter_revAllocation, n_extensions, extra, new_n, n_inputCount));
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

                    results.add(new CanonicalState(this.state, n_revAllocation, n_extensions_temp, extra, new_n, n_inputCount));
                } while ((assignShared = getNextPermutation(assignShared)) != null);
            } else {
                results.add(new CanonicalState(this.state, revAllocation, n_extensions_temp, extra, new_n, n_inputCount));
            }
        }

        return results;
    }

    /**
     * Method used by {@link EdgeCanonical} to perform a depth first search.
     * Like {@link #expand(Graph)} it performs an extra assignment of
     * identifiers to nodes. The difference with expand is that this method only
     * covers one case (combination of which to assign to which node). A
     * {@link DecisionPointInfo} object is returned that can be used to branch
     * into other cases.
     *
     * @param g The graph structure wherein this happens.
     * @return A {@link DecisionPointInfo} object to keep track of the decision
     * made by this expansion. That object can then be used to check other
     * branches of the search-tree.
     * @see #expandDFS(Graph, DecisionPointInfo)
     */
    public DecisionPointInfo expandDFS(Graph g) {
        DecisionPointInfo dpI = new DecisionPointInfo();
        dpI.extension = extensions[0];
        dpI.n = n;

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

        //Insert the already assigned (no input) children (AA) into the code.
        //Fill in array of unassigned non input children (UANI).
        //TODO: BinarySearch could be avoided since g.inc and vertices are both sorted?
        for (int x : g.inc[node]) {
            int assigned = revAllocation.get(x);
            if (assigned != NULLVAL) { //(AA)
                extra[aaIndex++] = (long) expandNode | assigned;
            } else if (Arrays.binarySearch(state.vertices, x) >= 0) { //unassigned, No input
                UANI[uaniIndex++] = x;
            } else if (isSharedInput(g, node, x)) { //unassigned, input, Shared input
                SHARED[sharedIndex++] = x;
                this.inputCount++;
            } else {
                this.inputCount++;
            }
            //else nonshared input
        }

        dpI.UANI = UANI; //length will be assign.length
        dpI.SHARED = SHARED; //length will be assignShared.length

        //Sort the AA children into correct order.
        Arrays.sort(extra, 0, aaIndex);

        //Add all children excluding the Already Assigned children to the code.
        long baseN = expandNode | this.n;
        for (int i = aaIndex; i < extra.length - 1; i++) {
            extra[i] = baseN++;
        }

        //Label in the code
        extra[extra.length - 1] = Long.MAX_VALUE - g.label[node];
        this.code = extra;

        //Start of the new extension list. Except for the Not yet assigned but 
        //expandable (non input)  children (UANI), every edge is filled in.
        int[] n_extensions = new int[extensions.length - 1 + uaniIndex];
        System.arraycopy(extensions, 1, n_extensions, 0, extensions.length - 1);

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

            dpI.assign = assign;
            dpI.assignShared = assignSharedBase;

            //Assign UANI
            int UANIstartIndex = extensions.length - 1 - this.n; //TODO: nodig in dpi?
            for (int i = 0; i < uaniIndex; i++) {
                revAllocation.put(UANI[i], assign[i]);
                n_extensions[UANIstartIndex + assign[i]] = UANI[i];
            }
            extensions = n_extensions;

            //Assign SHARED
            for (int i = 0; i < sharedIndex; i++) {
                revAllocation.put(SHARED[i], assignSharedBase[i]);
            }
        } else {
            //Create shared base array [j,j+1,j+2,...] which we will permute
            int j = this.n;
            int[] assignSharedBase = new int[sharedIndex];
            for (int i = 0; i < sharedIndex; i++) {
                assignSharedBase[i] = j++;
            }

            dpI.assign = new int[0];
            dpI.assignShared = assignSharedBase;

            //Assign SHARED
            for (int i = 0; i < sharedIndex; i++) {
                revAllocation.put(SHARED[i], assignSharedBase[i]);
            }

            extensions = n_extensions;
        }
        this.n = this.n + extra.length - aaIndex - 1;

        return dpI;
    }

    /**
     * Performs one expansion based on the given {@link DecisionPointInfo}
     * obtained by {@link #expandDFS(Graph)}. The idea is that
     * {@link #expandDFS(dagfsm.Graph)} performs a forward expansion with a
     * certain decision (branch). When we want to come back on that decision,
     * this CanonicalState can be rolled back
     * {@link #rollBack(DecisionPointInfo)} and then this method can be
     * performed to check the next branch in the given decision point.
     *
     * @param g The graph wherein this happens.
     * @param dpI The information of the decision point that has been rolled
     * back. It is used to determine the next branch to check. This object is
     * modified in-place and returned.
     * @return The same dpI that was given but modified in-place to reflect the
     * branching decision taken. If there is no next branching decision, null is
     * returned. In that case all branches have been checked and another
     * rollback should be performed on the decision of the previous decision
     * point in the search-tree.
     */
    public DecisionPointInfo expandDFS(Graph g, DecisionPointInfo dpI) {
        int uaniIndex = dpI.assign.length;
        int sharedIndex = dpI.assignShared.length;
        int[] UANI = dpI.UANI;
        int[] SHARED = dpI.SHARED;
        //this.code remains the same if we only came from one back.

        /* Base values */
        if (uaniIndex != 0) {
            //Fix assign and assignShared to next permutation.
            int[] assign;
            int[] assignShared = (sharedIndex == 0) ? null : getNextPermutation(dpI.assignShared);
            if (assignShared == null) {
                assign = getNextPermutation(dpI.assign);
                dpI.assign = assign;
                if (assign == null) {
                    return null;
                } else {
                    //reverse to get start assignShared
                    int length = dpI.assignShared.length;
                    assignShared = new int[length];
                    for (int i = 0; i < assignShared.length; i++) {
                        assignShared[i] = dpI.assignShared[length - i - 1];
                    }
                }
            } else {
                assign = dpI.assign;
            }

            dpI.assignShared = assignShared;

            int[] n_extensions = new int[extensions.length - 1 + uaniIndex];
            System.arraycopy(extensions, 1, n_extensions, 0, extensions.length - 1);

            //Assign UANI
            int UANIstartIndex = extensions.length - 1 - this.n;
            for (int i = 0; i < uaniIndex; i++) {
                revAllocation.put(UANI[i], assign[i]);
                n_extensions[UANIstartIndex + assign[i]] = UANI[i];
            }
            extensions = n_extensions;

            //Assign SHARED
            for (int i = 0; i < sharedIndex; i++) {
                revAllocation.put(SHARED[i], assignShared[i]);
            }
        } else {
            int[] assignShared = (sharedIndex == 0) ? null : getNextPermutation(dpI.assignShared);
            if (assignShared == null) {
                return null;
            }
            dpI.assignShared = assignShared;

            System.arraycopy(extensions, 1, extensions, 0, extensions.length - 1);
            extensions = Arrays.copyOf(extensions, extensions.length - 1);

            //Assign SHARED
            for (int i = 0; i < sharedIndex; i++) {
                revAllocation.put(SHARED[i], assignShared[i]);
            }
        }

        this.n = dpI.newN;

        return dpI;
    }

    /**
     * Roll back the previous decision by using the decision point information
     * given. In the dpI, only {@link #newN} is modified so that
     * {@link #expandDFS(Graph, DecisionPointInfo)} can move forward again in
     * the next branch.
     *
     * @param dpI The decision point information of the previous decision, to
     * roll back.
     */
    public void rollBack(DecisionPointInfo dpI) {
        //revAllocation
        for (int i = 0; i < dpI.assign.length; i++) {
            revAllocation.remove(dpI.UANI[i]);
        }
        for (int i = 0; i < dpI.assignShared.length; i++) {
            revAllocation.remove(dpI.SHARED[i]);
        }

        //extension
        int[] oldExtension = new int[extensions.length - dpI.assign.length + 1];
        oldExtension[0] = dpI.extension;
        System.arraycopy(extensions, 0, oldExtension, 1, (extensions.length - dpI.assign.length));
        extensions = oldExtension;

        //n
        dpI.newN = this.n;
        this.n = dpI.n;
    }

    /**
     * Whether node is not the only user of x's output in the state.
     *
     * @param g The graph structure behind this problem.
     * @param node The parent node of x that is ignored.
     * @param x The node x of which we check the users of its output.
     * @return Whether a user of x, different from node, is present in
     * state.vertices
     * @throws ArrayOutOfBoundsException When state.vertices.length is equal to
     * 0.
     */
    private boolean isSharedInput(Graph g, int node, int x) {
        /*        
        // Equal but less efficient code.
        for(int user : g.out[x]) {
            if(user != node && Arrays.binarySearch(state.vertices, user) >= 0) {
                return true;
            }
        }
        return false;
         */

        //Iterate over users of x and check if vertices contain any of them.
        int[] users = g.out[x];
        if (users.length == 1) {
            return false;
        }

        int index_v = 0; //index of vertices
        int index_u = 0; //index of users
        int[] vertices = state.vertices;

        int max = vertices[state.vertices.length - 1];
        int min = vertices[0];

        while (index_u < users.length) {
            int user = users[index_u++];

            if (user == node) {
                continue;
            }

            if (user < min) { //less than lowest vertices; check next user
                continue;
            }

            if (max < user) { //higher than max vertices; quit
                return false;
            }

            while (user > vertices[index_v]) { //Eventually false
                index_v++;
            }
            //now user =< vertices[index_v-1]

            if (user == vertices[index_v]) {
                return true;
            } //else user < vertices -> proceed with next user
        }

        return false;
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
    public int compareTo(CanonicalState o) {
        int max = Math.min(code.length, o.code.length);
        for (int i = 0; i < max; i++) {
            int r = Long.compare(code[i], o.code[i]);

            if (r != 0) {
                return r;
            }
        }

        return o.code.length - code.length;
    }

    /**
     * Retrieve the vertices of the state in assigned order.
     *
     * @return The vertices of the associated state in order assigned by this.
     */
    public int[] getVerticesInOrder() {
        return getVerticesInOrder(this.revAllocation);
    }

    /**
     * Retrieve the vertices of the state in assigned order.
     *
     * @param usedRevAllocation The revAllocation to use
     * @return The vertices of the associated state in order assigned by the
     * given usedRevAloocation.
     */
    public int[] getVerticesInOrder(Int2IntOpenHashMap usedRevAllocation) {
        //determine size
        int max = 0;
        for (int i : usedRevAllocation.values()) {
            max = (i > max) ? i : max;
        }

        //Actual placement
        int[] result = new int[max + 1];
        Arrays.fill(result, -1);

        for (int v : state.vertices) {
            result[usedRevAllocation.get(v)] = v;
        }

        //Trim empty
        int nextIndex = 0;
        for (int i = 0; i < result.length; i++) {
            int el = result[i];
            if (el != -1) {
                result[nextIndex++] = el;
            }
        }

        return Arrays.copyOf(result, nextIndex);
    }
}
