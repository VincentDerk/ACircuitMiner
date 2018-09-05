package com.vincentderk.acircuitminer.miner.util.verification;

import com.vincentderk.acircuitminer.miner.Graph;
import com.vincentderk.acircuitminer.miner.canonical.CodeOccResult;
import com.vincentderk.acircuitminer.miner.canonical.EdgeCanonical;
import com.vincentderk.acircuitminer.miner.util.ArrayLongHashStrategy;
import com.vincentderk.acircuitminer.miner.util.ArrayByteHashStrategy;
import com.vincentderk.acircuitminer.miner.util.OperationUtils;
import com.vincentderk.acircuitminer.miner.util.Utils;
import it.unimi.dsi.fastutil.ints.IntArrayFIFOQueue;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.objects.AbstractObject2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap.Entry;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenCustomHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.shorts.AbstractShort2ObjectSortedMap;
import it.unimi.dsi.fastutil.shorts.ShortArrayList;
import java.util.Arrays;
import java.util.Map;

/**
 * This class has an
 * {@link #isEquivalent(Graph, Graph, AbstractShort2ObjectSortedMap)} method
 * that can be used to verify that the replacement of occurrences has been
 * performed correctly. This can provide extra guarantees on the correctness
 * that the functionality of the graph is equivalent before and after
 * replacement.
 *
 * @author Vincent Derkinderen
 * @version 1.0
 */
public class EquivalenceChecker {

    /**
     * Test whether two given graphs og and rg are still equivalent after
     * replacing occurrences of patterns in og.
     * <ol>
     * <li> It does this by first unpacking every composed operation in og and
     * rg. This should result in equal graphs.
     * </li>
     * <li> This is tested by checking the isomorphism of the two graphs. This
     * is currently done by using the depth-first search version of the
     * canonical labeling implemented in
     * {@link EdgeCanonical#minCanonicalPermutationDFS(Graph)}. Unfortunately
     * this takes a very long time for bigger graphs (for ALARM it was manually
     * stopped after 64 minutes). </li>
     * </ol>
     * The isomorphism step could be made more efficient by a matching
     * algorithm. Especially using the background information that rg has been
     * produced out of og by replacing occurrences. That means that nodes not
     * involved in the replacement will be the same (index-wise) in both graphs.
     * This could be implemented/improved in the future.
     *
     * <p>
     * <b> Note: og and rg are modified in-place so clone beforehand if you do
     * not want this.</b>
     *
     * @param og The original graph before any replacement occurred.
     * @param rg The replaced graph that resulted from replacing occurrences
     * with calls to composed operations.
     * @param patternMap A mapping
     * @return
     */
    public boolean isEquivalent(Graph og, Graph rg, AbstractShort2ObjectSortedMap<long[]> patternMap) {
        Object2ObjectOpenCustomHashMap<long[], Map<byte[], Entry<Graph, int[]>>> memoizationPatternMap
                = new Object2ObjectOpenCustomHashMap(new ArrayLongHashStrategy());

        //Unpack
        unpack(og, patternMap, memoizationPatternMap);
        unpack(rg, patternMap, memoizationPatternMap);

        //Compare structure
        CodeOccResult ogCodeResult = EdgeCanonical.minCanonicalPermutationDFS(og);
        CodeOccResult rgCodeResult = EdgeCanonical.minCanonicalPermutationDFS(rg);

        boolean equalStructure = Arrays.equals(ogCodeResult.code, rgCodeResult.code);
        if (!equalStructure) {
            System.out.println("og: " + EdgeCanonical.printCode(ogCodeResult.code));
            System.out.println("rg: " + EdgeCanonical.printCode(rgCodeResult.code));
            return false;
        }

        return true;
    }

    /**
     * Unpacks all the composed nodes in the given Graph g. It does this by
     * decomposing all nodes that are composed operations.
     *
     * <p>
     * This decomposing is done by creating a graph of the pattern that code is
     * emulating. That graph is added 'behind' g (index-wise) and the
     * appropriate connections are changed to connect to the nodes of that
     * graph.
     * <p>
     * Note: only {@code g.inc, g.out and g.label} are updated/unpacked.
     * Some notes will be marked with the operation {@link Graph#MARKER}, these
     * are dangling nodes that have no function anymore.
     *
     * @param g The graph to unpack
     * @param patternMap A mapping from the label ({@code g.label}) of an
     * operation to the code of that operation. That code may not contain
     * composed operations.
     * {@link Graph#PRODUCT}, {@link Graph#SUM}, {@link Graph#INPUT} and
     * {@link Graph#MARKKER} do not have to be added.
     * @param memoizationPatternMap A mapping that can be used to memoize some
     * of the work. It maps a code to (a mapping of inputs to (a pair of 1. the
     * compact Graph formed by that input and the code and 2. the input nodes of
     * that compact graph))
     */
    private void unpack(Graph g, AbstractShort2ObjectSortedMap<long[]> patternMap,
            Object2ObjectOpenCustomHashMap<long[], Map<byte[], Entry<Graph, int[]>>> memoizationPatternMap) {
        int maxLength = g.label.length; //changes over time so store current.
        for (int i = 0; i < maxLength; i++) {
            short opId = g.label[i];
            switch (opId) {
                case Graph.PRODUCT:
                case Graph.SUM:
                case Graph.INPUT:
                case Graph.MARKER:
                    break;
                default:
                    long[] code = patternMap.get(opId);
                    if (code == null) {
                        throw new IllegalArgumentException("Found unknown operation: " + opId + " during unpacking node: " + i);
                    }

                    Map<byte[], Entry<Graph, int[]>> memGraphPatternMap = memoizationPatternMap.get(code);
                    if (memGraphPatternMap == null) {
                        memGraphPatternMap = new Object2ObjectOpenCustomHashMap(new ArrayByteHashStrategy());
                        memoizationPatternMap.put(code, memGraphPatternMap);
                    }

                    unpack(g, i, code, memGraphPatternMap);
            }
        }
    }

    /**
     * Auxiliary method of
     * {@link #unpack(Graph, AbstractShort2ObjectSortedMap, Object2ObjectOpenCustomHashMap)}.
     *
     * Unpacks the given node ({@code currentNodeIndex}) into its
     * decomposed operations. It does this by creating a graph of the pattern
     * that code is emulating. That graph is added 'behind' g (index-wise) and
     * the appropriate connections are changed to connect to the nodes of that
     * graph.
     * <p>
     * Note: only {@code g.inc, g.out and g.label} are updated/unpacked.
     * Some notes will be marked with the operation {@link Graph#MARKER}, these
     * are dangling nodes that have no function anymore.
     * </p>
     *
     * @param g The graph in which to unpack
     * @param currentNodeIndex The index of the node in g to unpack.
     * @param code The code denoting the pattern of the currentNodeIndex node
     * @param memGraphPatternMap A graph that is used for memoization. The given
     * code has to be converted into a compact graph structure using the inputs
     * of the code (extracted from g). To memoize this operation, this
     * GraphPatternMap is used. It stores for an input sequence, the resulting
     * compact Graph and its associated array that holds the indices of the
     * input nodes (-1 for the input nodes that are irrelevant). If it does not
     * contain the required input, it is added by this method.
     */
    private void unpack(Graph g, int currentNodeIndex, long[] code, Map<byte[], Entry<Graph, int[]>> memGraphPatternMap) {
        int[] graphInputNodes = g.inc[currentNodeIndex];
        byte[] input = new byte[graphInputNodes.length]; //1,2,4,8,16

        for (int i = 0; i < graphInputNodes.length; i++) {
            if (graphInputNodes[i] >= 0) {
                input[i] = 4; // 1 << 2
            } else {
                input[i] = (byte) -graphInputNodes[i]; // {1,2,8,16} = {0,1,3,4}
            }
        }

        // get corresponding patternGraph
        Entry<Graph, int[]> pattern = memGraphPatternMap.get(input);
        Graph patternGraph;
        int[] patternInputNodes;
        if (pattern == null) {
            patternGraph = Graph.codeToGraph(code);
            patternInputNodes = patternGraph.getInputNodes();
            byte[] nodeValue = propagateUp(patternGraph, input, patternInputNodes);
            //patternInputNodes[x] = y; the node that was given by patternInputNodes[x] is now y.
            //patternInputNodes[x] = -1 is a removal of that node.
            //patternInputNodes is changed by getCompactGraph, see doc.
            patternGraph = getCompactGraph(patternGraph, nodeValue, patternInputNodes);
            Entry<Graph, int[]> entry = new AbstractObject2ObjectMap.BasicEntry(patternGraph, patternInputNodes);
            memGraphPatternMap.put(input, entry);
        } else {
            patternGraph = pattern.getKey();
            patternInputNodes = pattern.getValue();
        }

        patternGraph = patternGraph.clone(); //required due to copy inc[][]
        //patternInputNodes = patternInputNodes.clone();

        // Put patternGraph in-place (in g)
        int incrementWith = g.inc.length;
        g.inc = Arrays.copyOf(g.inc, g.inc.length + patternGraph.inc.length);
        System.arraycopy(patternGraph.inc, 0, g.inc, incrementWith, patternGraph.inc.length);
        g.out = Arrays.copyOf(g.out, g.out.length + patternGraph.out.length);
        System.arraycopy(patternGraph.out, 0, g.out, incrementWith, patternGraph.out.length);
        g.label = Arrays.copyOf(g.label, g.label.length + patternGraph.label.length);
        System.arraycopy(patternGraph.label, 0, g.label, incrementWith, patternGraph.label.length);

        for (int i = incrementWith; i < g.inc.length; i++) {
            for (int j = 0; j < g.inc[i].length; j++) {
                g.inc[i][j] += incrementWith;
            }
        }

        for (int i = incrementWith; i < g.out.length; i++) {
            for (int j = 0; j < g.out[i].length; j++) {
                g.out[i][j] += incrementWith;
            }
        }

        //Connect graph to new in-place patternGraph.
        //Fix root (0+incrementWith)
        g.out[incrementWith] = g.out[currentNodeIndex];
        g.out[currentNodeIndex] = new int[0]; //TODO: null ok?
        g.inc[currentNodeIndex] = new int[0]; //TODO: null ok?
        g.label[currentNodeIndex] = Graph.MARKER;

        for (int parent : g.out[incrementWith]) {
            replace(g.inc[parent], currentNodeIndex, incrementWith);
        }

        //fix inputs
        for (int i = 0; i < input.length; i++) {
            if (input[i] == 4) { //1 << 2
                final int inputNode = patternInputNodes[i] + incrementWith;
                final int actualInputNode = graphInputNodes[i];

                //out = out of actualInputNode (- patternRoot) + out of inputNode.
                if (g.out[inputNode].length != g.out[actualInputNode].length) {
                    int[] newOut = Arrays.copyOf(g.out[inputNode], g.out[actualInputNode].length);
                    int nextIndex = g.out[inputNode].length;
                    for (int parent : g.out[actualInputNode]) {
                        if (parent != currentNodeIndex) {
                            newOut[nextIndex++] = parent;
                        }
                    }

                    g.out[actualInputNode] = newOut;
                } else {
                    g.out[actualInputNode] = g.out[inputNode];
                }
                g.out[inputNode] = new int[0]; //TODO: null ok?
                g.label[inputNode] = Graph.MARKER;
                for (int parent : g.out[actualInputNode]) {
                    replace(g.inc[parent], inputNode, actualInputNode);
                }
            }
        }
    }

    /**
     * Get the compact graph of g. The operations of g that are irrelevant as
     * denoted by {@code nodeValue} are removed. The
     * {@code inputNodes} structure is modified in-place to reflect the
     * indices of the inputs in the new compact Graph.
     *
     * @param g The graph structure of the pattern.
     * @param nodeValue For each node in the graph, its value as given by
     * {@link #propagateUp(Graph, byte[], int[])}.
     * @param inputNodes The nodes that provide the input of the graph. This
     * will be modified to hold the indices of the input nodes in the new
     * compact graph. It is assumed that irrelevant inputs are already -1.
     * @return A compact graph resulting from modifying g to only hold relevant
     * operations (given by {@code nodeValue}) Only the {@code inc,
     * out} and {@code label} structure of this graph is correct. The
     * rest is not.
     */
    private Graph getCompactGraph(Graph g, byte[] nodeValue, int[] inputNodes) {
        // Start with 'going down' and fixing the inc
        IntArrayFIFOQueue todo = new IntArrayFIFOQueue();
        int root = g.getFirstNonOutputNode();
        todo.enqueue(root);

        int nextId = 0;
        int[] assignMap = new int[g.inc.length];
        Arrays.fill(assignMap, -1);
        assignMap[root] = nextId++;

        ShortArrayList compactLabel = new ShortArrayList();
        ObjectArrayList<int[]> compactInc = new ObjectArrayList();

        while (!todo.isEmpty()) {
            int currentIndex = todo.dequeueInt();
            compactLabel.add(g.label[currentIndex]);

            if (g.inc[currentIndex].length == 0) {
                compactInc.add(new int[0]);
                replace(inputNodes, currentIndex, assignMap[currentIndex]);
            } else {
                IntArrayList inc = new IntArrayList();
                for (int incNode : g.inc[currentIndex]) {
                    if (nodeValue[incNode] == 4) { //1 << 2

                        //Fix inc
                        int assigned = assignMap[incNode];
                        if (assigned == -1) {
                            assigned = nextId++;
                            assignMap[incNode] = assigned;
                            todo.enqueue(incNode); //Next todo
                        }

                        inc.add(assigned);
                    }
                }

                compactInc.add(inc.toIntArray());
            }
        }

        Graph compactG = new Graph(compactInc.size());
        compactG.inc = compactInc.toArray(new int[0][]);
        compactG.label = compactLabel.toShortArray();

        //Fix out
        for (int node = 0; node < g.out.length; node++) {
            int newIndex = assignMap[node];

            if (newIndex != -1) {
                if (g.out[node].length == 1) {
                    //Only 1 parent means valid parent otherwise this wasn't assigned.
                    compactG.out[newIndex] = new int[]{assignMap[g.out[node][0]]};
                } else {
                    //Add each valid (assigned) parent.
                    IntArrayList compactOut = new IntArrayList();
                    for (int parent : g.out[node]) {
                        int newParentIndex = assignMap[parent];

                        if (assignMap[parent] != -1) {
                            compactOut.add(newParentIndex);
                        }
                    }

                    compactG.out[newIndex] = compactOut.toIntArray();
                }
            }
        }

        return compactG;
    }

    /**
     * Propagate the given inputs up in the graph and provide the resulting
     * value for each node.
     * <p>
     * Each input is given to the input nodes of the graph. These inputs
     * propagate upwards in the graph until the top. The resulting values are
     * returned. Nodes that just pass on a value due to neutral elements are
     * located in between two nodes. The node of which it passes the value and
     * the node to which it passes the value. After this method, the node is
     * taken out of the connection such that the two nodes are directly
     * connected to each other without the passing-on-node.
     *
     * @param g The graph to propagate in.
     * @param input The inputs given to the {@link inputNodes},
     * {1,2,4,8,16} interpreted as {0,1,2,3,4}. 2 is used to represent an actual
     * value. 3 and 4 are used for irrelevant values.
     * @param inputNodes The nodes in graph that take the given
     * {@code input}. {@code inputNodes[x]} is the node (index in the
     * graph) that takes input {@code input[x]}.
     * @return The resulting values of each node. For each node in graph the
     * result informs of the value of the node {0,1,2,3,4} by {1,2,4,16,16}.
     * Nodes that apparently pass on the value (due to neutral elements), are
     * bridged over. This means the given graph is changed such that the nodes
     * passing over are no longer in between the nodes it connects.
     * <p>
     * 2 is used to represent an actual value. 3 and 4 are used for irrelevant
     * values.
     */
    private byte[] propagateUp(Graph g, byte[] input, int[] inputNodes) {
        byte[] nodeValue = new byte[g.inc.length]; //1,2,4,8,16
        Arrays.fill(nodeValue, (byte) -1);
        for (int i = 0; i < input.length; i++) {
            nodeValue[inputNodes[i]] = input[i];
        }

        //Add parents of inputNodes to toCheck
        IntArrayList toCheck = new IntArrayList();
        for (int inputNode : inputNodes) {
            toCheck.addElements(toCheck.size(), g.out[inputNode]);
        }

        while (!toCheck.isEmpty()) {
            int currentNodeIndex = toCheck.popInt();

            if (nodeValue[currentNodeIndex] == -1) {

                //Check if all children are set
                boolean allChildrenSet = true;
                for (int child : g.inc[currentNodeIndex]) {
                    if (nodeValue[child] == -1) {
                        allChildrenSet = false;
                        break;
                    }
                }

                if (allChildrenSet) {
                    //Add parents
                    toCheck.addElements(toCheck.size(), g.out[currentNodeIndex]);

                    //Fix the nodeValue and connection (Passing on = node bridged over)
                    switch (g.label[currentNodeIndex]) {
                        case Graph.SUM:
                        case Graph.SUM_OUTPUT:
                            propagateUp_auxSum(g, currentNodeIndex, nodeValue);
                            break;
                        case Graph.PRODUCT:
                        case Graph.PRODUCT_OUTPUT:
                            propagateUp_auxProduct(g, currentNodeIndex, nodeValue);
                            break;
                        default:
                            throw new IllegalArgumentException("Expected sum or product operation but found operation: " + g.label[currentNodeIndex]);
                    }
                }
            }
        }

        return nodeValue;
    }

    /**
     * Auxiliary method of {@link #propagateUp(Graph, byte[], int[])}.
     *
     * This fixes {@code nodeValue[currentNodeIndex]} to the correct value
     * (1,2,4,16) by looking at the values (nodeValue) of the children of that
     * node. Assumes the operation of the node is the product operation.
     *
     * @param g The graph where nodeValue is associated with
     * @param currentNodeIndex The index of node we are looking at
     * @param nodeValue The array of values of each node. It holds for each node
     * what its value would be. (0,1,2,3,4) causes nodeValue to be (1,2,4,16,16)
     * respectively. 2 is an actual value, 3 means useless value (e.g 1+1) and 4
     * means does not matter.
     *
     * @see #propagateUp(Graph, byte[], int[])
     * @see #propagateUp_auxSum(Graph, int, byte[])
     */
    private void propagateUp_auxProduct(Graph g, int currentNodeIndex, byte[] nodeValue) {
        int[] children = g.inc[currentNodeIndex];

        //allOne = (first2Child == -1 && !containsThree)
        boolean containsIrrelevant = false;
        int first2Child = -1;
        boolean multipleTwo = false;

        for (int child : children) {
            switch (nodeValue[child]) {
                case 1: //0
                    nodeValue[currentNodeIndex] = 1;
                    return;
                case 2: //1
                    break;
                case 4: //2
                    if (first2Child == -1) {
                        first2Child = child;
                    } else {
                        multipleTwo = true;
                    }
                    break;
                case 8: //3
                case 16: //4
                    containsIrrelevant = true;
                    break;
            }
        }

        if (containsIrrelevant) { // 3,4 (no 0)
            nodeValue[currentNodeIndex] = 16; // 1 << 4
        } else if (first2Child == -1) { //no 0,3,2
            nodeValue[currentNodeIndex] = 2; //1 << 1
        } else { // only 2 is left
            if (!multipleTwo) {
                bridgeConnection(g, first2Child, currentNodeIndex);
            }
            nodeValue[currentNodeIndex] = 4; //1 << 2
        }
    }

    /**
     * Auxiliary method of {@link #propagateUp(dagfsm.Graph, byte[], int[])}.
     *
     * This fixes  {@code nodeValue[currentNodeIndex]} to the correct value
     * (1,2,4,16) by looking at the values (nodeValue) of the children of that
     * node. Assumes the operation of the node is the sum operation.
     *
     * @param g The graph where nodeValue is associated with
     * @param currentNodeIndex The index of node we are looking at
     * @param nodeValue The array of values of each node. It holds for each node
     * what its value would be. (0,1,2,3,4) causes nodeValue to be (1,2,4,16,16)
     * respectively. 2 is an actual value, 3 means useless value (e.g 1+1) and 4
     * means does not matter.
     *
     * @see #propagateUp(dagfsm.Graph, byte[], int[])
     * @see #propagateUp_auxProduct(dagfsm.Graph, int, byte[])
     */
    private void propagateUp_auxSum(Graph g, int currentNodeIndex, byte[] nodeValue) {
        int[] children = g.inc[currentNodeIndex];
        int[] childCount = new int[3];
        int first2Child = -1;

        for (int child : children) {
            switch (nodeValue[child]) {
                //case 1: we ignore, no action required.
                case 2: //1 << 1
                    childCount[1]++;
                    if (childCount[1] > 1 || childCount[2] > 0) { // 1 + 1 || 1 + 2
                        nodeValue[currentNodeIndex] = 16; //1 << 4
                        return;
                    }
                    break;

                case 4: //1 << 2
                    childCount[2]++;
                    if (childCount[1] > 0) { // 2 + 1
                        nodeValue[currentNodeIndex] = 16; //1 << 4
                        return;
                    } else if (first2Child == -1) {
                        first2Child = child;
                    }
                    break;

                case 8: //2 << 3
                case 16: //1 << 4
                    nodeValue[currentNodeIndex] = 16; //1 << 4
            }
        }

        //Already covered the cases for 3 (1+1, 1+2, 3)
        if (childCount[1] == 0 && childCount[2] > 0) { // 2 + (2/0)
            if (childCount[2] == 1) { // 2 + 0
                bridgeConnection(g, first2Child, currentNodeIndex);
            }
            nodeValue[currentNodeIndex] = 4; //1 << 2
        } else if (childCount[1] == 0 && childCount[2] == 0) { // 0
            nodeValue[currentNodeIndex] = 1; //1 << 0
        } else if (childCount[1] == 1 && childCount[2] == 0) { // 1
            nodeValue[currentNodeIndex] = 2; //1 << 1
        }
    }

    /**
     * Bridge the connection from {@code fromPass} to any parent of
     * {@code toPass} as if {@code toPass} is removed.
     * <p>
     * The connections that toPass has with any of its parents X, is changed to
     * originate from {@code fromPass}.
     * <p>
     * The inc and out of toPass node will be set to null and its label to
     * {@link Graph#MARKER}.
     *
     * @param g The graph to perform the change in
     * @param fromPass The node (index in graph) whose value is passed on by
     * {@code toPass}. After this method this node will have the outgoing
     * connections of {@code toPass}.
     * @param toPass The node (index in graph) which passes on the value of
     * {@code fromPass}. After this method the parents of this node will be
     * connected to {@code fromPass}.
     *
     * @throws IllegalArgumentException When the result is that
     * {@code fromPass} has two or more direct connections to the same
     * node. This can not be handled by the Canonical Code algorithm and may
     * also not be a logical thing to allow.
     */
    private void bridgeConnection(Graph g, int fromPass, int toPass) {
        //Parents of toPass now get result from fromPass
        for (int parent : g.out[toPass]) {
            replace(g.inc[parent], toPass, fromPass);
        }

        //fromPass outputs to both its own out (excl toPass) and the outs of toPass
        int[] newOut = Arrays.copyOf(g.out[toPass], g.out[toPass].length + g.out[fromPass].length - 1);
        int nextIndex = g.out[toPass].length;
        for (int parent : g.out[fromPass]) {
            if (parent != toPass) {
                newOut[nextIndex++] = parent;
            }
        }

        g.out[fromPass] = newOut;

        //Cleaning toPass
        g.inc[toPass] = null;
        g.out[toPass] = null;
        g.label[toPass] = Graph.MARKER;

        //Verify that the fromPass only outputs to each parent once.
        //If it outputs twice that means a node is connected to another node twice
        //CanonicalCode can't handle this, it will give the wrong result. 
        //(It might also not make sense to have that.)
        if (!isDistinct(newOut)) {
            throw new IllegalArgumentException("While briding node " + toPass + ", it is found that " + fromPass + " has two direct connections to another node.");
        }
    }

    /**
     * Check whether each element in the array only occurrence once.
     *
     * @param arr The array to check
     * @return True if every element in {@code arr} is only present once.
     * False otherwise, when there is an element that occurs at least twice.
     */
    private boolean isDistinct(int[] arr) {
        int[] arrSorted = arr.clone();
        int prev = Integer.MAX_VALUE;
        for (int val : arrSorted) {
            if (val == prev) {
                return false;
            } else {
                val = prev;
            }
        }

        return true;
    }

    /**
     * Replace first occurrence of x in array by y.
     *
     * @param array The array to replace in
     * @param x The element to replace
     * @param y The element to replace with
     */
    private void replace(int[] array, int x, int y) {
        if (x != y) {
            for (int i = 0; i < array.length; i++) {
                if (array[i] == x) {
                    array[i] = y;
                    return;
                }
            }
        }
    }

}
