package com.vincentderk.acircuitminer.miner.emulatable.neutralfinder;

import com.vincentderk.acircuitminer.miner.Graph;
import java.util.Arrays;

/**
 * Data structure used by the {@link NeutralFinder} to keep track of and modify
 * the current state to find all emulatable patterns by using 0 and 1 as input.
 * <p>
 * It stores the current set {@link #input} and the consequences on the nodes
 * ({@link #options} and {@link #originValue}) It provides a method to set an
 * input {@link #setInput(int, byte)} which will take care of the propagation of
 * the information onto the other stored variables.
 *
 * @author Vincent Derkinderen
 * @version 1.0
 */
public class NeutralState {

    /**
     * The back-bone graph to find emulatable patterns of. Unchanged.
     */
    public Graph g;

    /**
     * Provides a way to see which node is the node representing input of index
     * x. {@code inputNodes[x] = y} : Node y is the x'th input.
     */
    public int[] inputNodes;

    /**
     * The input currently given to this state (-1,0,1,2). -1 means unassigned
     * and 2 represents a variable input. 0 and 1 are the constants.
     */
    public byte[] input;

    /**
     * The possible values for each node in the graph. {@code options[x] = y}
     * indicates the node x has the options represented by y. This is a byte,
     * the bits that are 1 represent the possible value. e.g 3 = 0011 = possible
     * value 0 and 1. Possible values: 0,1,2,3 where 3 denotes useless value.
     */
    public byte[] options;

    /**
     * For each node in the graph the origin of the value is given. This is a
     * way of indicating which node is providing the value. For example, when a
     * product node has an input 1 and 2. The origin of the value is the node
     * that provides the input 2 since 1 is a neutral element for
     * multiplication. This can be used to quickly determine the pattern in the
     * graph without changing the underlying graph.
     */
    public int[] originValue;

    /**
     * The root node of the Graph g.
     */
    public int root;

    /**
     * Create a neutral state based on the given g and inputNodes.
     *
     * @param g The graph to find the emulatable patterns of.
     * @param inputNodes The nodes that are input nodes of g. e.g
     * {@code inputNodes[2] = 5} means node 5 of g is the third input node.
     */
    public NeutralState(Graph g, int[] inputNodes) {
        this.g = g;
        this.inputNodes = inputNodes;
        this.input = new byte[inputNodes.length];
        Arrays.fill(input, (byte) -1);

        this.root = getRootOfGraph(g);

        this.options = new byte[g.inc.length];
        Arrays.fill(options, (byte) 15); //0,1,2,3 = 1111 = 1+2+4+8
        for (int inputNode : inputNodes) { //InputNodes can not be 3.
            this.options[inputNode] = 7; //0,1,2 = 111 = 1+2+4
        }
        this.options[root] = 4; // root must be 2 (so 1 << 2).
        //TODO optimisation set(root,..) and let it propagate down. (oh and check if no outgoing + propagate up is a problem)

        this.originValue = new int[g.inc.length];
        for (int i = 0; i < originValue.length; i++) {
            originValue[i] = i;
        }

    }

    private NeutralState() {
    }

    /**
     * Set the given value as input and propagate it through the graph.
     *
     * @param inputIndex The index of the input to set (0,1,..). The actual node
     * is found by {@code inputNodes[inputIndex]}.
     * @param value The value to set the input to. Expected to be 0,1 or 2.
     * @return Whether the state is still consistent. False means the state was
     * found to be inconsistent with the given value set.
     */
    public boolean setInput(int inputIndex, byte value) {
        byte resultValue = (byte) (1 << value); // 1 bit set.
        if ((resultValue & options[inputNodes[inputIndex]]) == 0) { //Non valid
            //System.out.println("Invalid set for " + inputIndex + " to " + value + " and options " + options[inputNodes[inputIndex]]); //DEBUG INFO
            return false;
        }

        input[inputIndex] = value;
        return set(inputNodes[inputIndex], value);
    }

    /**
     * Set the given value and propagate it trough the graph.
     *
     * @param inputIndex The index of the node to set.
     * @param value The value to set the node to. Expected to be 0,1,2 or 3.
     * //TODO: If set 3, parent+ = 3, parent*= 0,1
     * @return Whether the state is still consistent. False means the state was
     * found to be inconsistent with the given value set.
     */
    private boolean set(int index, byte value) {
        byte resultValue = (byte) (1 << value); // 1 bit set.

        /* Check valid input */
        if ((resultValue & options[index]) == 0) { //Non valid
            return false;
        }

        /* Check if no child is originating from same node */
        if (value == 2) {
            int[] inc = g.inc[index];
            int[] originInc = new int[g.inc[index].length];
            for (int i = 0; i < originInc.length; i++) {
                originInc[i] = originValue[inc[i]];
            }
            Arrays.sort(originInc);
            int prev = -1;
            for (int i : originInc) {
                if (i == prev) {
                    return false;
                } else {
                    prev = i;
                }
            }
        }

        /* Set input */
        if (resultValue == options[index]) { //was already that value
            return true;
        } else {
            options[index] = resultValue;
            return propagate(index, value);
        }
    }

    /**
     * Propagate the value set on inputIndex.
     *
     * @param nodeIndex The index of the node where value is the new value.
     * @param value The value to set for the node.
     * @return Whether the state is still consistent. False means the state was
     * found to be inconsistent with the given value set.
     */
    private boolean propagate(int nodeIndex, byte value) {

        //Propagate up
        int[] parents = g.out[nodeIndex];

        for (int parent : parents) {
            boolean consistent = checkParentAndItsChildren(parent, nodeIndex, value);
            if (!consistent) {
                return false;
            }
        }

        // Propagate down
        //TODO optimisation
        return true;
    }

    //Up
    /**
     * Propagate the result (newValue) of a node (originChildNode) to a parent
     * node (parentNode). If parentNode changes, it propagates further to the
     * children and parents.
     *
     * @param parentNode The parent of originChildNode to check. This must be a
     * parent of that child.
     * @param originChildNode The child node that changed and triggered this
     * check
     * @param newValue The new value of the originChildNode
     * @return False if an inconsistency was found.
     */
    private boolean checkParentAndItsChildren(int parentNode, int originChildNode, byte newValue) {
        //Currently only check when all children have a value and propagates the value up.
        switch (g.label[parentNode]) {
            case Graph.PRODUCT: // *
            case Graph.PRODUCT_OUTPUT: // root *
                return checkParentAndItsChildren_auxMulti(parentNode, originChildNode, newValue);
            case Graph.SUM: //+
            case Graph.SUM_OUTPUT: // root +
                return checkParentAndItsChildren_auxPlus(parentNode, originChildNode, newValue);
            default:
                throw new IllegalArgumentException("Warning: found an operation different than * and + while calculating neutral state.");
        }
    }

    /**
     * Propagate the result (newValue) of a node (originChildNode) to a parent
     * node (parentNode) given that the parentNode is multiplication. If
     * parentNode changes, it propagates further to the children and parents.
     *
     * @param parentNode The parent of originChildNode to check. This must be a
     * parent of that child.
     * @param originChildNode The child node that changed and triggered this
     * check
     * @param newValue The new value of the originChildNode
     * @return False if an inconsistency was found.
     */
    private boolean checkParentAndItsChildren_auxMulti(int parentNode, int originChildNode, byte newValue) {
        //Currently only check when all children have a value and propagates the value up.
        int[] children = g.inc[parentNode];

        //allOne = (first2Child == -1 && !containsThree)
        boolean containsThree = false;
        int first2Child = -1;
        boolean multipleTwo = false;

        for (int child : children) { //TODO: atm only doing something when all have an one option left.
            switch (optionCount(options[child])) {
                case 0:
                    return false;

                case 1: //1 option left
                    byte value = getFirstOption(options[child]);

                    switch (value) {
                        case 0:
                            return set(parentNode, (byte) 0); // 0
                        case 1:
                            break;
                        case 2:
                            if (first2Child == -1) {
                                first2Child = child;
                            } else {
                                multipleTwo = true;
                            }
                            break;
                        case 3:
                            containsThree = true;
                            break;
                    }
                    break;
                default: // 2,3 options still
                    return true;
            }
        }

        if (containsThree) { // 3 (no 0)
            return set(parentNode, (byte) 3);
        } else if (first2Child == -1) { //no 0,3,2
            return set(parentNode, (byte) 1);
        } else { // only 2 is left
            if (!multipleTwo) {
                originValue[parentNode] = originValue[first2Child];
            }
            return set(parentNode, (byte) 2);
        }
    }

    /**
     * Propagate the result (newValue) of a node (originChildNode) to a parent
     * node (parentNode) given that the parentNode is summation. If
     * parentNode changes, it propagates further to the children and parents.
     *
     * @param parentNode The parent of originChildNode to check. This must be a
     * parent of that child.
     * @param originChildNode The child node that changed and triggered this
     * check
     * @param newValue The new value of the originChildNode
     * @return False if an inconsistency was found.
     */
    private boolean checkParentAndItsChildren_auxPlus(int parentNode, int originChildNode, byte newValue) {
        //Currently only check when all children have a value and propagates the value up.
        int[] children = g.inc[parentNode];
        int[] childCount = new int[4];
        int first2Child = -1;

        for (int child : children) { //TODO: atm only doing something when all have an one option left.
            switch (optionCount(options[child])) {
                case 0:
                    return false;

                case 1: //1 option left
                    byte value = getFirstOption(options[child]);
                    childCount[value]++;

                    switch (value) {
                        case 1:
                            if (childCount[1] > 1) { // 1 + 1
                                return set(parentNode, (byte) 3);
                            } else if (childCount[2] > 0) { // 1 + 2
                                return set(parentNode, (byte) 3);
                            }
                            break;

                        case 2:
                            if (childCount[1] > 0) { // 2 + 1
                                return set(parentNode, (byte) 3);
                            } else if (first2Child == -1) {
                                first2Child = child;
                            }
                            break;

                        case 3:
                            return set(parentNode, (byte) 3);
                    }
                    break;

                default: // 2,3 options still
                    return true;
            }
        }

        //Already covered the cases for 3 (1+1, 1+2, 3)
        if (childCount[1] == 0 && childCount[2] > 0) { // 2 + (2/0)
            if (childCount[2] == 1) { // 2 + 0
                originValue[parentNode] = originValue[first2Child];
            }
            return set(parentNode, (byte) 2);
        } else if (childCount[1] == 0 && childCount[2] == 0) { // 0
            return set(parentNode, (byte) 0);
        } else if (childCount[1] == 1 && childCount[2] == 0) { // 1
            return set(parentNode, (byte) 1);
        }

        // set and propagate
        System.err.println("Should not be reachable. -NeutralState_Plus");
        return true;
    }

    public NeutralState clone() {
        NeutralState newState = new NeutralState();
        newState.g = this.g;
        newState.originValue = this.originValue.clone();
        newState.inputNodes = this.inputNodes;
        newState.input = this.input.clone();
        newState.options = this.options.clone();
        newState.root = this.root;

        return newState;
    }

    /**
     * Get the first option from the given options.
     *
     * @param options The options to get the first from.
     * @return The first option there is {0,1,2,3}. -1 if there isn't any.
     */
    public static byte getFirstOption(byte options) {
        if ((1 & options) > 0) {
            return 0;
        } else if ((2 & options) > 0) {
            return 1;
        } else if ((4 & options) > 0) {
            return 2;
        } else if ((8 & options) > 0) {
            return 3;
        } else {
            return -1;
        }
    }

    /**
     * Get the amount of 1 bits present in the given options.
     *
     * @param options The options to check the amount of.
     * @return The amount of 1 bits present in the given options. Only the first
     * 4 bits are checked. Max answer is 3.
     */
    public static int optionCount(byte options) {
        return (1 & options) + ((2 & options) >> 1) + ((4 & options) >> 2) + ((8 & options) >> 3);
    }

    /**
     * Get the root node the given Graph g.
     *
     * @param graph The graph to get the root node of.
     * @return The first node to have no outgoing edges (starting from the
     * highest node numbers). -1 if there is no such node. Can not happen in a
     * rooted DAG.
     */
    private static int getRootOfGraph(Graph graph) {
        for (int i = graph.out.length - 1; i >= 0; i--) {
            if (graph.out[i].length == 0) {
                return i;
            }
        }

        return -1;
    }

    /**
     * Get the node that is the origin of the result of the root.
     *
     * @return The root node except when the due to neutral elements the value
     * of the root is originating from one other node (directly passed on from
     * it). In that case the originating node is returned.
     */
    public int getRealRoot() {
        return this.originValue[this.root];
    }

}
