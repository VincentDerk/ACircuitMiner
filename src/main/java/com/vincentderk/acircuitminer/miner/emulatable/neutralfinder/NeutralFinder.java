package com.vincentderk.acircuitminer.miner.emulatable.neutralfinder;

import com.vincentderk.acircuitminer.miner.Graph;
import com.vincentderk.acircuitminer.miner.util.ArrayLongHashStrategy;
import com.vincentderk.acircuitminer.miner.util.OperationUtils;
import com.vincentderk.acircuitminer.miner.util.Utils;
import com.vincentderk.acircuitminer.miner.emulatable.neutralfinder.canonical.CanonicalNeutralState;
import com.vincentderk.acircuitminer.miner.emulatable.neutralfinder.canonical.CodeStatePair;
import com.vincentderk.acircuitminer.miner.emulatable.neutralfinder.canonical.NeutralEdgeCanonical;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenCustomHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

/**
 * Finds the patterns that a given pattern (as a Graph) can emulate. This is
 * done by trying each possible combination of values for the inputs and then
 * checking the pattern it results in.
 * <p>
 * Furthermore, for each node, we keep track of the possible values it may get
 * assigned. We use 2 to refer to actual user-input. 0 and 1 are used for the
 * neutral and absorbing elements, 0 and 1. This means that for a useful
 * pattern, the only possible option for the root node is to get value 2. More
 * clever constraints can be added to reduce the amount of combinations tested
 * to only useful ones. 3 is used to refer to useless values. For example 1 + 1
 * is not a useful value and is not allowed unless the result is eventually not
 * used. 4 is used to refer to a 'does not matter' value.
 * <p>
 * <b>The algorithm:</b> After setting the value (0,1,2) of an input port, the
 * consequences are checked. This is done by looking at the parents of the set
 * node and determine if we know their value. If we do, the value of that parent
 * is set and the same is repeated for his parents. Whenever a set value leads
 * to an invalid state (e.g root != 2) the sequence is stopped and another value
 * is set. When all consequences are determined, the next input port is given a
 * value. When all input ports have a value, the emulated pattern is determined
 * by using {@link NeutralEdgeCanonical}.
 *
 * @author Vincent Derkinderen
 * @version 1.0
 */
public class NeutralFinder {

    /**
     * Get the patterns that are emulatable by the given pattern.
     *
     * @param pattern The pattern to get the emulatable patterns of.
     * @return All emulatable patterns found by using 0 (constant) ,1 (constant)
     * or 2 (variable value) as input for the given pattern.
     * @see #getEmulatablePatterns(Graph)
     */
    public ObjectArrayList<long[]> getEmulatablePatterns(long[] pattern) {
        Graph graph = OperationUtils.codeToGraph(pattern);
        return getEmulatablePatterns(graph);
    }

    /**
     * Get the patterns that are emulatable by the given pattern (graph g).
     *
     * @param g The pattern (in graph form) to get the emulatable patterns of.
     * @return All emulatable patterns found by using 0 (constant) ,1 (constant)
     * or 2 (variable value) as input for the given pattern.
     * @see #getEmulatablePatternsMap(Graph)
     */
    public ObjectArrayList<long[]> getEmulatablePatterns(Graph g) {
        Object2ObjectOpenCustomHashMap<long[], EmulatableBlock> map = getEmulatablePatternsMap(g);
        return new ObjectArrayList(map.keySet());
    }

    /**
     * Stores the intermediate found emulatable patterns.
     */
    private Object2ObjectOpenCustomHashMap<long[], EmulatableBlock> mapping;

    /**
     * The amount of found valid inputs. An input is valid if it leads to a
     * useful pattern (excl. the given pattern, uninteresting patterns such as
     * with one input). This resets to 0 when a new emulation search starts.
     */
    public long validInputCounter;

    /**
     * Provides a way to see which node is the node representing input of index
     * x. {@code inputNodes[x] = y} : Node y is the x'th input.
     */
    private int[] inputNodes;

    private Graph g;

    /**
     * Get a mapping of every pattern that is emulatable by the given graph and
     * map that to the emulatableBlock (contains input required to emulate the
     * pattern) <b>Beware! The map will not contain the pattern (given by Graph)
     * itself!</b>
     *
     * @param pattern The graph to get the emulated patterns of.
     * @return The emulated patterns mapped to an {@link EmulatableBlock} which
     * holds information regarding to the emulation. The emulated patterns are
     * found using 0, 1 and 2 (actual variable input) as input values. This
     * means patterns emulatable by other techniques may not be found.
     * @see #getEmulatablePatternsMap(Graph)
     */
    public Object2ObjectOpenCustomHashMap<long[], EmulatableBlock> getEmulatablePatternsMap(long[] pattern) {
        Graph graph = OperationUtils.codeToGraph(pattern);
        return getEmulatablePatternsMap(graph);
    }

    /**
     * Get a mapping of every pattern that is emulatable by the given graph and
     * map that to the emulatableBlock (contains input required to emulate the
     * pattern) <b>Beware! The map will not contain the pattern (given by Graph)
     * itself!</b>
     *
     * @param graph The graph to get the emulated patterns of.
     * @return The emulated patterns mapped to an {@link EmulatableBlock} which
     * holds information regarding to the emulation. The emulated patterns are
     * found using 0, 1 and 2 (actual variable input) as input values. This
     * means patterns emulatable by other techniques may not be found.
     */
    public Object2ObjectOpenCustomHashMap<long[], EmulatableBlock> getEmulatablePatternsMap(Graph graph) {
        mapping = new Object2ObjectOpenCustomHashMap(new ArrayLongHashStrategy());
        validInputCounter = 0;
        inputNodes = Utils.getInputNodes(graph);
        g = graph;

        //Init neutralState
        NeutralState state = new NeutralState(g, inputNodes);

        for (byte i = 0; i < 3; i++) {
            if ((state.options[inputNodes[0]] & (1 << i)) > 0) {
                //We set the first input and let it branch further.
                setAndBranch(0, i, state);
            }
        }

        //System.out.println("Found " + validInputCounter + " valid input combinations."); //DEBUG INFO
        return mapping;
    }

    /**
     * Sets the given input ({@code inputIndex}) to {@code value} (0,1,2,3,4).
     *
     * @param inputIndex
     * @param value
     * @param currState
     */
    private void setAndBranch(int inputIndex, byte value, NeutralState currState) {
        NeutralState state = currState.clone();

        if (state.setInput(inputIndex, value)) { //Sets and propagates
            if (inputIndex + 1 == state.input.length) { //last input
                if (isInterestingState(state) && !isStartPattern(state)) {
                    addEmulatablePattern(g, state);
                }
                /*else {
                    System.out.println("Discarded due to less than 2 real inputs. input " + Arrays.toString(state.input) + " and options " + Arrays.toString(state.options));
                }*/
            } else {
                for (byte i = 0; i < 3; i++) {
                    int actualIndex = inputNodes[inputIndex + 1];
                    if ((state.options[actualIndex] & (1 << i)) > 0) {
                        setAndBranch(inputIndex + 1, i, state);
                    }
                }
            }
        }/* else {
            System.out.println("Discarded " + value + " at " + inputIndex + " due to invalid state");
        }*/
    }

    /**
     * Add the pattern formed by {@code state} to the {@link mapping} variable.
     *
     * @param g The graph of which we are getting the emulatable patterns.
     * @param state The NeutralState to get the formed pattern of.
     */
    private void addEmulatablePattern(Graph g, NeutralState state) {
        //get Code
        CodeStatePair codePair = NeutralEdgeCanonical.minCanonicalPermutation(g, state);

        //Add code if new
        if (mapping.get(codePair.code) == null) { //If not already set
            propagate4Down(state);
            int[] emulatedIndexToActualInput = getEmulatedIndexToActualInputIndex(g, state, codePair.revAllocation, codePair.code);
            EmulatableBlock block = getEmulatableBlock(state, codePair.code, emulatedIndexToActualInput);
            mapping.put(codePair.code, block);
        }

        validInputCounter++;
        //System.out.println("found " + NeutralEdgeCanonical.printCode(code) + " with input " + Arrays.toString(state.input) + " and " + Arrays.toString(state.options)); //DEBUG INFO
    }

    /**
     * Finds the correct index mapping. Example given, if the second input in
     * the emulated pattern is equal to the fifth input in the pattern (g), then
     * {@code returned[1] = 4}.
     *
     * @param g The pattern (in graph form) of which we try to find emulatable
     * patterns.
     * @param state The NeutralState that forms the emulated pattern information
     * @param revAllocation The allocation graphNodeId |-> AssignInEmulatedCode
     * that was used to obtain the emulated code.
     * @param code The pattern g emulated via state.
     * @return The input indices of the actual pattern that correspond to the
     * input indices of the emulated pattern.
     */
    private static int[] getEmulatedIndexToActualInputIndex(Graph g, NeutralState state, Int2IntOpenHashMap revAllocation, long[] emulatedCode) {
        //Idea: First find input_index_in_emulated |-> GraphNodeId
        //Secondly use inputNodes of state = input_index_in_actualP |-> GraphNodeId
        //Combine: input_index_in_emulated |-> GraphNodeId |-> input_index_in_actualP

        //re-reverse allocation
        Int2IntOpenHashMap allocation = new Int2IntOpenHashMap(); //AssignedInEmulatedCode |->  GraphNodeId
        revAllocation.forEach((a, b) -> allocation.put(b.intValue(), a.intValue()));
        final int allocationDefault = allocation.defaultReturnValue();
        IntSet inputAllocated = new IntOpenHashSet(); // GraphNodeId already allocated
        IntArrayList revInputAllocation = new IntArrayList(); // Index_in_emulated |-> GraphNodeId

        //Find all inputs Index_in_emulated |-> GraphNodeId
        for (long l : emulatedCode) {
            if (l < Long.MAX_VALUE - Graph.HIGHEST_OP) {
                long MASK = ((long) 1 << 32) - 1; //32 1-bits: 0..0111..111
                int right = (int) (MASK & l);

                int alloc = allocation.get(right);
                if (alloc == allocationDefault) { //not assigned == non-shared input
                    int left = (int) (l >> 32);
                    int leftNodeId = allocation.get(left);

                    //Check all children and find input node.
                    for (int leftNodeInput : g.inc[leftNodeId]) {
                        int x = state.originValue[leftNodeInput];

                        if (g.inc[x].length == 0 && state.options[x] == 4
                                //&& inputAllocated.get(x) == allocationDefault) { //non shared useful input
                                && !inputAllocated.contains(x)
                                && !CanonicalNeutralState.isSharedInput(g, state, x)) {
                            //inputAllocation.put(x, right);
                            inputAllocated.add(x);
                            revInputAllocation.add(x);
                            break;
                        }
                    }

                } else { //Was assigned so vertex or shared input
                    if (g.inc[alloc].length == 0) { //shared
                        //inputAllocation.put(right, alloc);
                        inputAllocated.add(alloc);
                        revInputAllocation.add(alloc);
                    }
                }
            }
        }

        //revAllocation here contains all input nodes (GraphNodeId) in correct order (small to high assigned id).
        // revInputAllocation: input_index_in_emulated |-> GraphNodeId
        // inputNodes: input_index_in_actualP |-> GraphNodeId
        Int2IntOpenHashMap revInputNodes = new Int2IntOpenHashMap(); // GraphNodeId |-> input_index_in_actualP
        for (int i = 0; i < state.inputNodes.length; i++) {
            revInputNodes.put(state.inputNodes[i], i);
        }

        int[] emulatedIndexToActualInputIndex = revInputAllocation.toIntArray(); //input_index_in_emulated |-> GraphNodeId
        for (int i = 0; i < emulatedIndexToActualInputIndex.length; i++) { //input_index_in_emulated |-> GraphNodeId |-> input_index_in_actualP
            emulatedIndexToActualInputIndex[i] = revInputNodes.get(emulatedIndexToActualInputIndex[i]);
        }

        return emulatedIndexToActualInputIndex;
    }

    /**
     * Get the emulatable block associated with the given state and
     * emulatedCode. It contains the information of the emulation such as the
     * amount of active nodes.
     *
     * @param state The state to get the block of.
     * @param emulatedCode The emulatedCode to set.
     * @return The emulatedBlock associated with the given state and
     * emulatedCode.
     *
     */
    private EmulatableBlock getEmulatableBlock(NeutralState state, long[] emulatedCode,
            int[] emulatedIndexToActualInputIndex) {
        EmulatableBlock block = new EmulatableBlock();
        block.emulatedCode = emulatedCode;
        block.emulatedIndexToActualInputIndex = emulatedIndexToActualInputIndex;
        block.input = state.input;
        block.options = state.options;

        // (in)active arithmetic nodes
        int activeMultCount = 0;
        int activeSumCount = 0;
        int inactiveMultCount = 0;
        int inactiveSumCount = 0;

        for (int i = 0; i < state.options.length; i++) {
            if (state.options[i] == 4 && state.originValue[i] == i) { // active, 4 = 1 << 2 = real input
                switch (state.g.label[i]) {
                    case Graph.PRODUCT:
                        activeMultCount++;
                        break;
                    case Graph.SUM:
                        activeSumCount++;
                        break;
                }
            } else { // inactive
                switch (state.g.label[i]) {
                    case Graph.PRODUCT:
                        inactiveMultCount++;
                        break;
                    case Graph.SUM:
                        inactiveSumCount++;
                        break;
                }
            }
        }

        block.activeInputCount = Utils.inputNodeCount(emulatedCode);
        block.activeMultCount = activeMultCount;
        block.activeSumCount = activeSumCount;
        block.inactiveMultCount = inactiveMultCount;
        block.inactiveSumCount = inactiveSumCount;

        return block;
    }

    /**
     * Should only be called after the state is finished (all inputs set and
     * propagated). This checks the options for irrelevant values and propagates
     * the irrelevance back down. This means irrelevant inputs get set to 4
     * (1&lt&lt4 = 16 in options). 'Rules:'
     * <> 3 gets converted to 4.
     * <> inputs of 3 get converted to 4 if they can.
     * <> inputs of multiplication resulting in 0: if they are 1 or 2 they get
     * converted to 4 if they can.
     *
     * @param state The state in which to propagate downwards.
     */
    private void propagate4Down(NeutralState state) {
        //Opt: '4' will mean that incoming 1,2 don't matter. 0 means unvisited.
        byte[] opt = new byte[state.options.length];
        Graph g = state.g;

        IntArrayList toCheck = new IntArrayList();
        int currIndex = state.root; //index of root
        opt[currIndex] = 1; //1=visited

        //Add all non input children
        for (int i : g.inc[currIndex]) {
            if (g.inc[i].length > 0) {
                if (g.out[i].length == 1) {
                    toCheck.push(i);
                } else { // > 1
                    //If all parents are visited, add it.
                    boolean foundZero = false;

                    for (int j : g.out[i]) {
                        if (opt[j] == 0) {
                            foundZero = true;
                            break;
                        }
                    }

                    if (!foundZero) {
                        toCheck.push(i);
                    }
                }
            }
        }

        while (!toCheck.isEmpty()) {
            currIndex = toCheck.popInt();
            opt[currIndex] = 1;

            //Add all non input children with all their parents visited already
            for (int i : g.inc[currIndex]) {
                if (g.inc[i].length > 0) {
                    if (g.out[i].length == 1) {
                        toCheck.push(i);
                    } else { // > 1
                        //If all parents are visited, add it.
                        boolean foundZero = false;

                        for (int j : g.out[i]) {
                            if (opt[j] == 0) {
                                foundZero = true;
                                break;
                            }
                        }

                        if (!foundZero) {
                            toCheck.push(i);
                        }
                    }
                }
            }

            /* Check if current can be 4 */
            if (state.options[currIndex] == 2 || state.options[currIndex] == 4) { //== 1, == 2
                boolean allFour = true;
                for (int j : state.g.out[currIndex]) {
                    if (opt[j] != 4) {
                        allFour = false;
                        break;
                    }
                }
                if (allFour) {
                    state.options[currIndex] = 16;
                }
            }

            /* Determine if children can be 4 */
            switch (state.options[currIndex]) {
                case 1: //0
                    if (state.g.label[currIndex] == Graph.PRODUCT) {
                        opt[currIndex] = 4;
                    }
                    break;
                case 8: //3
                    opt[currIndex] = 4;
                    break;
                case 16: //4
                    opt[currIndex] = 4;
                    break;
            }
        }

        /* Fix input */
        for (int i = 0; i < state.inputNodes.length; i++) {
            int inputNode = state.inputNodes[i];

            boolean all4 = true;
            for (int user : g.out[inputNode]) {
                if (state.options[user] != 16) { //16 == 1 << 4
                    all4 = false;
                    break;
                }
            }
            if (all4) {
                state.options[inputNode] = 16;
                state.input[i] = 4;
            }
        }
    }

    /**
     * Whether the given state is the pattern we started with.
     *
     * @param state The state to check.
     * @return Whether all inputs of the given state are 2.
     */
    private boolean isStartPattern(NeutralState state) {
        for (byte v : state.input) {
            if (v != 2) {
                return false;
            }
        }

        return true;
    }

    /**
     * Whether the given state provides an interesting pattern. This does not
     * check whether the result for the root node is 2.
     *
     * @param state The state to check
     * @return Whether there are at least 2 children of the real root
     * ({@link NeutralState#getRealRoot()} which provide an actual value (2).
     */
    private boolean isInterestingState(NeutralState state) {
        boolean foundOne = false;

        for (int child : g.inc[state.getRealRoot()]) {
            int originChild = state.originValue[child];

            if (state.options[originChild] == 4) { //4 = 1 << 2
                if (!foundOne) {
                    foundOne = true; //Found first 2
                } else {
                    return true; //Found second 2
                }
            }
        }

        return false;
    }

    /**
     * Count the amount of 2's present in the given array.
     *
     * @param arr The array to check the amount of 2's in.
     * @return The amount of two's present in the given array.
     */
    private int amountOfTwos(byte[] arr) {
        int count = 0;
        for (byte a : arr) {
            if (a == 2) {
                count++;
            }
        }

        return count;
    }

}
