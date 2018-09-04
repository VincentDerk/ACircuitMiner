package com.vincentderk.acircuitminer.miner.util;

import com.vincentderk.acircuitminer.miner.Graph;
import com.vincentderk.acircuitminer.miner.emulatable.neutralfinder.EmulatableBlock;
import com.vincentderk.acircuitminer.miner.emulatable.neutralfinder.UseBlock;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntAVLTreeSet;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.objects.AbstractObject2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMaps;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenCustomHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

//TODO: Verify which operationUtils methods work for SOSR/MOSR and update the docs.
/**
 * Contains some useful operations such as replacing occurrences, writing to an
 * .ac file, getting total execution energy costs, removing overlap,...
 *
 * @author Vincent Derkinderen
 * @version 2.0
 */
public class OperationUtils {

    /**
     * Replace the given occurrences by a new operation presented with newLabel,
     * in g. In each occurrence, the vertices should match the order of the
     * canonicalCode they are occurrences of. So the first is the root, the
     * second is vertex assigned 1 in the canonicalCode,...
     * <p>
     * <b> Note: This invalidates the graph invariant that inc and out are
     * sorted.</b>
     *
     * @param g The graph to replace in.
     * @param code The pattern we are replacing
     * @param occurrences The occurrences of the {@code code} pattern that have
     * to be replaced.
     * @param newLabel The new label of the new operation
     * @return The inc and out of the nodes that should be ignored from now on,
     * are replaced with null.
     */
    public static IntArrayList replace(Graph g, long[] code, ObjectArrayList<int[]> occurrences, short newLabel) {
        IntArrayList ignore = new IntArrayList();

        /* Fix edges */
        for (int[] occurrence : occurrences) {
            if (occurrence.length <= 1) {
                continue;
            }

            //codeId |-> nodeId in graph
            Int2IntMap inputMap = Utils.getInputMap(g, code, occurrence); //TODO: Change to more efficient one

            int root = occurrence[0];
            int[] sortedOccurrence = Arrays.copyOf(occurrence, occurrence.length);
            Arrays.sort(sortedOccurrence); //for quick contains check

            // Remove internal node data
            for (int i = 1; i < occurrence.length; i++) {
                int node = occurrence[i];
                g.inc[node] = null;
                g.out[node] = null;
                g.label[node] = Graph.MARKER;
                g.expandable_children[node] = null;
                g.unexpandable_children[node] = null;
                ignore.add(node);
            }

            // Make inputs point to root
            for (int node : inputMap.values()) {
                //for (int i : g.out[node]) {
                for (int i = 0; i < g.out[node].length; i++) {
                    if (Arrays.binarySearch(sortedOccurrence, g.out[node][i]) >= 0) {
                        g.out[node][i] = root;
                    }
                }
            }

            // Order the inputs according to codeId for the root.
            int[] sortedInputKeys = inputMap.keySet().toIntArray(); //TODO: optimizable, always same.
            Arrays.sort(sortedInputKeys);
            int[] inputNodesOrdered = new int[sortedInputKeys.length];

            for (int i = 0; i < sortedInputKeys.length; i++) {
                inputNodesOrdered[i] = inputMap.get(sortedInputKeys[i]);
            }

            g.inc[root] = inputNodesOrdered;
            g.label[root] = newLabel;

            // Fix expandable and unexpandable
            IntArrayList expandable = new IntArrayList();
            IntArrayList unexpandable = new IntArrayList();
            for (int node : inputNodesOrdered) {
                if (g.label[node] == Graph.INPUT) {
                    unexpandable.add(node);
                } else {
                    expandable.add(node);
                }
            }

            g.expandable_children[root] = expandable.toIntArray();
            g.unexpandable_children[root] = unexpandable.toIntArray();
        }

        return ignore;
    }

    /**
     * Replace the given occurrences in g that form a pattern given by
     * {@code emulatedCode}. Replacement is done by another pattern given by
     * {@code code} that uses a certain input, given by {@code input}, to
     * emulate the {@code emulatedCode}. In each occurrence, the vertices should
     * match the order of the canonicalCode they are occurrences of. So the
     * first is the root, the second is vertex assigned 1 in the
     * canonicalCode,...
     *
     * <p>
     * <b> Note: This invalidates the graph invariant that inc and out are
     * sorted.</b>
     *
     * @param g The graph to replace in.
     * @param emulatedIndexToActualInputIndex This provides the link between the
     * inputs given to an emulated pattern and the order of those inputs in the
     * actual input given to the pattern P. See
     * {@link EmulatableBlock#emulatedIndexToActualInputIndex} for an example.
     * @param emulatedCode The pattern we are emulating by giving the
     * {@code input} to the {@code code}.
     * @param input The input given to the {@code code} to emulate the
     * {@code emulatedCode}
     * @param occurrences The occurrences to replace
     * @param newLabel The new label of the new operation
     * @return The inc and out of the nodes that should be ignored from now on,
     * are replaced with null.
     * @see EmulatableBlock#emulatedIndexToActualInputIndex
     */
    public static IntArrayList replaceNeutral(Graph g, int[] emulatedIndexToActualInputIndex, long[] emulatedCode, byte[] input, ObjectArrayList<int[]> occurrences, short newLabel) {
        IntArrayList ignore = new IntArrayList();

        /* Fix edges */
        for (int[] occurrence : occurrences) {
            if (occurrence.length <= 1) {
                continue;
            }

            //codeId |-> bigger graph NodeId
            Int2IntMap inputMap = Utils.getInputMap(g, emulatedCode, occurrence); //TODO: Change to more efficient one

            int root = occurrence[0];
            int[] sortedOccurrence = Arrays.copyOf(occurrence, occurrence.length);
            Arrays.sort(sortedOccurrence); //for quick contains check

            // Remove internal node data
            for (int i = 1; i < occurrence.length; i++) {
                int node = occurrence[i];
                g.inc[node] = null;
                g.out[node] = null;
                g.label[node] = Graph.MARKER;
                g.expandable_children[node] = null;
                g.unexpandable_children[node] = null;
                ignore.add(node);
            }

            // Make inputs point to root
            for (int node : inputMap.values()) {
                for (int i : g.out[node]) {
                    if (Arrays.binarySearch(sortedOccurrence, i) >= 0) {
                        replace(g.out[node], i, root);
                    }
                }
            }

            // Order the inputs according to codeId for the root.
            int[] sortedInputKeys = inputMap.keySet().toIntArray(); //TODO: optimizable, always same for same pattern
            Arrays.sort(sortedInputKeys);
            //int[] inputNodesOrdered = new int[sortedInputKeys.length];
            int[] inputNodesOrdered = new int[input.length];

            for (int i = 0; i < sortedInputKeys.length; i++) {
                int actualIndex = emulatedIndexToActualInputIndex[i];
                inputNodesOrdered[actualIndex] = inputMap.get(sortedInputKeys[i]);
            }

            for (int i = 0; i < input.length; i++) {
                int insertInput = input[i];
                if (insertInput != 2) {
                    if (inputNodesOrdered[i] != 0) {
                        System.err.println("Error, unexpected result. It should have been zero."); //TODO: delete debug message
                    }
                    inputNodesOrdered[i] = -(1 << insertInput); //- 2^input e.g 0=-1, 1=-2, 4=-16
                }
            }

            g.inc[root] = inputNodesOrdered;
            g.label[root] = newLabel;

            // Fix expandable and unexpandable
            IntArrayList expandable = new IntArrayList();
            IntArrayList unexpandable = new IntArrayList();
            for (int node : inputNodesOrdered) {
                if (node < 0 || g.label[node] == Graph.INPUT) {
                    unexpandable.add(node);
                } else {
                    expandable.add(node);
                }
            }

            g.expandable_children[root] = expandable.toIntArray();
            g.unexpandable_children[root] = unexpandable.toIntArray();
        }

        return ignore;
    }

    /**
     * Execute the given {@code replaceBlock}. This means that in g, all
     * occurrences in that {@link UseBlock} are replaced by a call to the
     * patternP with a certain input. Both the occurrences of patternP and the
     * emulatable occurrences are replaced.
     *
     * @param g The graph to replace in.
     * @param replaceBlock The block containing information on which occurrence
     * and patterns to replace.
     * @param newOp The operation id of the newly introduced operation
     * {@code patternP}.
     * @return The list of nodes in g to ignore after this replacement.
     *
     * @see #replace(Graph, long[], ObjectArrayList, short)
     * @see #replaceNeutral(Graph, int[], long[], byte[], ObjectArrayList,
     * short)
     */
    public static IntArrayList replace(Graph g, UseBlock replaceBlock, short newOp) {
        IntArrayList ignoreList;
        EmulatableBlock[] patterns = replaceBlock.patterns;
        ObjectArrayList<ObjectArrayList<int[]>> usedOccurrences = replaceBlock.occurrences;

        //Replace own
        ignoreList = OperationUtils.replace(g, replaceBlock.patternP, usedOccurrences.get(0), newOp);

        //Replace emulatable
        for (int i = 0; i < patterns.length; i++) {
            EmulatableBlock emulBlock = patterns[i];
            IntArrayList ignore = OperationUtils.replaceNeutral(g, emulBlock.emulatedIndexToActualInputIndex,
                    emulBlock.emulatedCode, emulBlock.input, usedOccurrences.get(i + 1), newOp);
            ignoreList.addAll(ignore);
        }

        return ignoreList;
    }

    /**
     * Write all patterns involved in the given {@code replaceBlock} and the
     * graph {@code g}.
     *
     * @param g The graph to write as well.
     * @param replaceBlock The block of patterns to write. patternP and all
     * patterns will be written.
     * @param replacedOutputPath The output path for the graph g ac file.
     * @param graphInputPath This path to an ac file that is used to extract the
     * required literalMap.
     * @param patternPOutputPath The output path for the patternP ac file.
     * @param emulatedOutputPath The output paths for each
     * {@code replaceBlock.patterns} ac file.
     * @param symbols A mapping from the operationIds to the symbols used in the
     * AC file. This must contain a mapping for each used label in the
     * {@code g.label}. This will typically contain entries such as:
     * <ul>
     * <li> symbols.put(Graph.PRODUCT, "*");</li>
     * <li> symbols.put(Graph.SUM, "+");</li>
     * <li> symbols.put(Graph.INPUT, "l");</li>
     * </ul>
     * @param ignoreList The list of nodes to ignore when writing the graph g.
     * @throws java.io.IOException Can occur while writing to any of the given
     * paths or reading from {@code graphInputPath}.
     *
     * @see Utils#getLiteralMap(java.io.Reader)
     */
    public static void writeUseBlock(Graph g, UseBlock replaceBlock, String replacedOutputPath,
            String graphInputPath,
            String patternPOutputPath, String[] emulatedOutputPath,
            HashMap<Short, String> symbols,
            IntArrayList ignoreList) throws IOException {

        // Write base pattern
        writePattern(replaceBlock.patternP, patternPOutputPath, symbols);

        // Write rest
        EmulatableBlock[] usedPatterns = replaceBlock.patterns;
        for (int i = 0; i < usedPatterns.length; i++) {
            writePattern(usedPatterns[i].emulatedCode, emulatedOutputPath[i], symbols);
        }

        // Write replaced AC (graph g)
        writeAC(g, replacedOutputPath, graphInputPath, ignoreList, symbols);
    }

    /**
     * Write Graph g to AC file and use the literals obtained from the ac file
     * at path {@code readPath}.
     *
     * @param g The graph to write
     * @param outPath The output path for the graph
     * @param readPath The read path for the original graph, to extract the
     * literalMap.
     * @param ignore The nodes to ignore
     * @param symbols The symbols of the operations
     * @throws IOException
     * @see #write(Graph, FileWriter, IntArrayList, HashMap, Int2IntMap)
     */
    public static void writeAC(Graph g, String outPath, String readPath, IntArrayList ignore, HashMap<Short, String> symbols) throws IOException {
        FileWriter writer = new FileWriter(outPath);
        Int2IntMap literalMap = Utils.getLiteralMap(new FileReader(readPath));
        OperationUtils.write(g, writer, ignore, symbols, literalMap);
    }

    /**
     * Write the given pattern to the given path.
     *
     * @param best pattern to write
     * @param patternPath The path to write to
     * @param symbols Symbols of the operations
     * @throws IOException
     * @see #codeToGraph(long[])
     * @see #writePatternGraph(FileWriter, Graph, IntArrayList, HashMap)
     */
    public static void writePattern(long[] best, String patternPath, HashMap<Short, String> symbols) throws IOException {
        Graph patternGraph = Graph.codeToGraph(best);
        FileWriter writer = new FileWriter(patternPath);
        writePatternGraph(writer, patternGraph, new IntArrayList(), symbols);
    }

    /**
     * Replace first occurrence of x in array by y.
     *
     * @param array The array to replace in
     * @param x The element to replace
     * @param y The element to replace with
     */
    private static void replace(int[] array, int x, int y) {
        for (int i = 0; i < array.length; i++) {
            if (array[i] == x) {
                array[i] = y;
                return;
            }
        }
    }

    /**
     * Write the given graph to AC file but ignore the nodes in ignore. Nodes
     * that have ignored nodes in between will be relocated. e.g 0,1,ignored,3,4
     * writes 0,1,3,4
     * <p>
     * So symbol 4 2 3 4 5 means that operation associated with symbol has 4
     * inputs, of which the order is of importance. The external inputs required
     * for the first node (determined by the occurrence) are listed. Then follow
     * the external inputs of the other nodes. The amount of inputs for each
     * note is determined by the code (pattern) that the symbol represents.
     *
     * @param writer
     * @param g The graph to write.
     * @param ignore The nodes to ignore in the writing. Can be empty
     * @param symbols The symbols which every label maps to. Every label should
     * be present.
     * @param literalMap The map that maps literal nodes to their id.
     * @throws java.io.IOException
     */
    public static void write(Graph g, FileWriter writer, IntArrayList ignore, HashMap<Short, String> symbols, Int2IntMap literalMap) throws IOException {
        try (BufferedWriter bw = new BufferedWriter(writer)) {
            int nbNode = g.inc.length - ignore.size();
            int nbEdges = g.getEdgeCount(); //TODO test
            int nbLiterals = literalMap.values().parallelStream().mapToInt(x -> Math.abs(x)).max().getAsInt();
            bw.write("nnf " + nbNode + " " + nbEdges + " " + nbLiterals);
            bw.newLine();

            int[] ignoreSorted = ignore.toIntArray();
            Arrays.sort(ignoreSorted);

            for (int i = 0; i < g.inc.length; i++) {
                int[] incs = g.inc[i];

                if (incs != null) { //ignore otherwise
                    StringBuilder sb = new StringBuilder();
                    String symbol = symbols.get(g.label[i]);
                    sb.append(symbol);
                    sb.append(" ");

                    if (g.label[i] == Graph.INPUT) {
                        int literalId = literalMap.get(i);
                        sb.append(literalId); //TODO: check for correctness
                    } else {
                        sb.append(incs.length);
                        for (int node : incs) {
                            sb.append(" ");
                            sb.append(map(ignoreSorted, node));
                        }
                    }
                    bw.write(sb.toString());
                    bw.newLine();
                }
            }
        }
    }

    /**
     * Write the given Pattern graph to AC file but ignore the nodes in ignore.
     * Pattern graph refers to graphs built from a canonical code. They are
     * written by writing the incomings of 0 in a recursive way. e.g if 0 has
     * incoming 1 and 2, node 1 is written first by the same method. This is
     * different from just handling node 0,1,2,3,...
     * <p>
     * So <i>symbol</i> 4 2 3 4 5 means that operation associated with
     * <i>symbol</i> has 4 inputs, of which the order is of importance. The
     * amount of inputs for each note is determined by the code (pattern) that
     * the symbol represents.
     * <p>
     * This uses
     * {@link #writePatternGraph(FileWriter, Graph, IntArrayList, HashMap, Int2IntMap)}
     * with the literalMap given by {@link Utils#getInputNodesMap(Graph)}.
     *
     * @param writer The file writer to write with
     * @param patternGraph The pattern graph to write.
     * @param ignore The nodes to ignore in the writing. Since ignored nodes
     * must not be connected to the rest of the graph, this list is only used to
     * determine the actual nodeCount. Can be empty.
     * @param symbols The symbols which every label maps to. Every label should
     * be present.
     * @throws java.io.IOException
     * @see Utils#getInputNodesMap(Graph)
     */
    public static void writePatternGraph(FileWriter writer, Graph patternGraph, IntArrayList ignore, HashMap<Short, String> symbols) throws IOException {
        Int2IntMap inputMap = patternGraph.getInputNodesMap();
        OperationUtils.writePatternGraph(writer, patternGraph, new IntArrayList(), symbols, inputMap);
    }

    /**
     * Write the given Pattern graph to AC file but ignore the nodes in ignore.
     * Pattern graph refers to graphs built from a canonical code. They are
     * written by writing the incomings of 0 in a recursive way. e.g if 0 has
     * incoming 1 and 2, node 1 is written first by the same method. This is
     * different from just handling node 0,1,2,3,...
     * <p>
     * So <i>symbol</i> 4 2 3 4 5 means that operation associated with
     * <i>symbol</i> has 4 inputs, of which the order is of importance. The
     * amount of inputs for each note is determined by the code (pattern) that
     * the symbol represents.
     *
     * @param writer The file writer to write with
     * @param g The graph to write.
     * @param ignore The nodes to ignore in the writing. Since ignored nodes
     * must not be connected to the rest of the graph, this list is only used to
     * determine the actual nodeCount. Can be empty.
     * @param symbols The symbols which every label maps to. Every label should
     * be present.
     * @param literalMap A mapping of {@link Graph#inc} index to its appropriate
     * input index. See {@link Graph#getInputNodesMap()}.
     * @throws java.io.IOException
     */
    public static void writePatternGraph(FileWriter writer, Graph g, IntArrayList ignore, HashMap<Short, String> symbols, Int2IntMap literalMap) throws IOException {
        try (BufferedWriter bw = new BufferedWriter(writer)) {
            int nbNode = g.inc.length - ignore.size();
            int nbEdges = g.getEdgeCount();
            int nbLiterals = literalMap.values().parallelStream().mapToInt(x -> Math.abs(x)).max().getAsInt();
            bw.write("nnf " + nbNode + " " + nbEdges + " " + nbLiterals);
            bw.newLine();

            Int2IntMap lineMap = new Int2IntOpenHashMap(); //incIndex -> lineIndex
            writePatternGraph_aux(bw, g, 0, lineMap, symbols, literalMap);
        }
    }

    /**
     * Auxiliary method for {@link #writePatternGraph}. It deals with the
     * {@code k}'th index in the graph. This is a recursive method that may
     * executes the same method to process the indices incoming to the current
     * index {@code k}.
     *
     * @param writer The BufferedWriter to write with
     * @param g The graph
     * @param k The index in graph to process.
     * @param lineMap A map to keep track of the lines ({@link Graph#inc} index
     * -&gt line number it appears on)
     * @param symbols The symbols which every label maps to. Every label should
     * be present.
     * @param literalMap A mapping of {@link Graph#inc} index to its appropriate
     * input index. See {@link Graph#getInputNodesMap()}.
     *
     * @return The line number assigned to index k of g's incoming structure.
     * The given lineMap is also updated with indices that were handled.
     */
    private static int writePatternGraph_aux(BufferedWriter bw, Graph g, int k, Int2IntMap lineMap, HashMap<Short, String> symbols, Int2IntMap literalMap) throws IOException {
        if (lineMap.containsKey(k)) {
            return lineMap.get(k);
        } else if (g.inc[k].length == 0) { //literal
            bw.write("l " + literalMap.get(k));
            bw.newLine();
            int id = lineMap.size();
            lineMap.put(k, id);
            return id;
        } else {
            // First handle incomings
            for (int i : g.inc[k]) {
                if (!lineMap.containsKey(i)) {
                    lineMap.put(i, writePatternGraph_aux(bw, g, i, lineMap, symbols, literalMap));
                }
            }

            // Write own line
            StringBuilder sb = new StringBuilder();
            String symbol = symbols.get(g.label[k]);
            sb.append(symbol);
            sb.append(" ");

            sb.append(g.inc[k].length);
            for (int node : g.inc[k]) {
                sb.append(" ");
                sb.append(lineMap.get(node));
            }

            bw.write(sb.toString());
            bw.newLine();

            // Return
            int id = lineMap.size();
            lineMap.put(k, id);
            return id;
        }
    }

    /**
     * Find the real nodeIndex given the node and the ignored nodes.
     * <p>
     * Replacing an occurrences ignores the inner nodes and links inputs to the
     * one new node. Since we have to ignore those inner nodes that were
     * dropped, we have to correct the id of nodes that have a higher id. e.g if
     * nodeX is 6 but we ignore node {2,4,7}, the new Id of 6 is 4. The new Id
     * of 8 would then be 5.
     *
     * @param sortedIgnore The ids to ignore in a sorted order.
     * @param node The node id to map
     * @return node - #(ignoredNodes &lt node)
     */
    private static int map(int[] sortedIgnore, int node) {
        int index = Arrays.binarySearch(sortedIgnore, node);
        if (index >= 0) {
            throw new IllegalArgumentException("Error: Found node that should have been ignored. This musn't happen!\nOutput might be invalid.");
        } else {
            index = Math.abs(index + 1);
            return node - index;
        }
    }

    /**
     * Remove overlap for the given patterns. (Single threaded)
     *
     * @param patterns The patterns to remove the overlap of.
     * @return A new map containing the same keys but now for every key/pattern
     * only a subset of the original occurrences. The new subset does not
     * overlap internally.
     */
    public static Object2ObjectOpenCustomHashMap<long[], ObjectArrayList<int[]>> removeOverlapSingle(
            Object2ObjectOpenCustomHashMap<long[], ObjectArrayList<int[]>> patterns) {
        Object2ObjectOpenCustomHashMap<long[], ObjectArrayList<int[]>> subset
                = new Object2ObjectOpenCustomHashMap(new ArrayLongHashStrategy());

        for (Object2ObjectMap.Entry<long[], ObjectArrayList<int[]>> p : Object2ObjectMaps.fastIterable(patterns)) {
            ObjectArrayList<int[]> values = p.getValue();
            ObjectArrayList<int[]> newValues = new ObjectArrayList();

            IntAVLTreeSet nodeSet = new IntAVLTreeSet();

            for (int[] vertices : values) {
                boolean alreadyIn = false;

                //Check if no vertex is present yet in the nodeSet.
                for (int v : vertices) {
                    if (nodeSet.contains(v)) {
                        alreadyIn = true;
                        break;
                    }
                }

                if (!alreadyIn) {
                    for (int i : vertices) {
                        nodeSet.add(i);
                    }
                    newValues.add(vertices);
                }
            }

            subset.put(p.getKey(), newValues);
        }
        return subset;
    }

    /**
     * Remove the overlap for the given patterns while also considering the
     * given filterSet as overlap. So for each pattern, all occurrences are
     * reduced to a set of occurrences that do not overlap with each other.
     * Additionally, these occurrences do not overlap with a node present in the
     * filterSet. This is multi-threaded (parallelStream).
     *
     * @param patterns The patterns to remove the overlap of. Remains unchanged.
     * @param filterSet The set of nodes that aren't allowed to be used and are
     * also considered overlap. Remains unchanged. This can also be null or
     * empty. Remains unchanged.
     * @return An array of entries, each originating from the given patterns
     * map. For each entry/pattern, the occurrences are reduced to only
     * occurrences that do not overlap.
     */
    public static Map.Entry<long[], ObjectArrayList<int[]>>[] removeOverlap(
            Entry<long[], ObjectArrayList<int[]>>[] patterns,
            final IntAVLTreeSet filterSet) {
        Map.Entry<long[], ObjectArrayList<int[]>>[] result = Arrays.stream(patterns)
                .parallel()
                .map(x -> removeOverlap_aux(x, filterSet))
                .toArray(Map.Entry[]::new);

        return result;
    }

    /**
     * Remove the overlap for the given patterns while also considering the
     * given filterSet as overlap. So for each pattern, all occurrences are
     * reduced to a set of occurrences that do not overlap with each other.
     * Additionally, these occurrences do not overlap with a node present in the
     * filterSet. This is multi-threaded (parallelStream).
     *
     * @param patterns The patterns to remove the overlap of. Remains unchanged.
     * @param filterSet The set of nodes that aren't allowed to be used and are
     * also considered overlap. Remains unchanged. This can also be null or
     * empty. Remains unchanged.
     * @return An array of entries, each originating from the given patterns
     * map. For each entry/pattern, the occurrences are reduced to only
     * occurrences that do not overlap.
     */
    public static Map.Entry<long[], ObjectArrayList<int[]>>[] removeOverlap(
            Object2ObjectOpenCustomHashMap<long[], ObjectArrayList<int[]>> patterns,
            final IntAVLTreeSet filterSet) {
        Map.Entry<long[], ObjectArrayList<int[]>>[] result = patterns.object2ObjectEntrySet().parallelStream()
                .map(x -> removeOverlap_aux(x, filterSet))
                .toArray(Map.Entry[]::new);

        return result;
    }

    /**
     * Remove the overlap from the given entry/pattern.
     *
     * @param before The entry/pattern to remove its overlap of. Remains
     * unchanged.
     * @param ignoreFilter the nodes that are also considered overlap. This can
     * also be null or empty. Remains unchanged.
     * @return A new entry with the same key and a new occurrence list
     * containing only non-overlapping occurrences.
     */
    private static Map.Entry<long[], ObjectArrayList<int[]>> removeOverlap_aux(
            Map.Entry<long[], ObjectArrayList<int[]>> before, IntAVLTreeSet ignoreFilter) {
        ObjectArrayList<int[]> values = before.getValue();
        ObjectArrayList<int[]> newValues = new ObjectArrayList();

        /* TODO Some heuristic ? - This is just first come first serve */
        IntSet nodeSet;
        if (ignoreFilter == null) {
            nodeSet = new IntAVLTreeSet();
        } else {
            nodeSet = (IntSet) ignoreFilter.clone();
        }

        for (int[] vertices : values) {
            boolean alreadyIn = false;

            //Check if no vertex is present yet in the nodeSet.
            for (int v : vertices) {
                if (nodeSet.contains(v)) {
                    alreadyIn = true;
                    break;
                }
            }

            if (!alreadyIn) {
                for (int i : vertices) {
                    nodeSet.add(i);
                }
                newValues.add(vertices);
            }
        }
        return new AbstractObject2ObjectMap.BasicEntry<>(before.getKey(), newValues);
    }

}
