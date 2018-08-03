package com.vincentderk.acircuitminer.miner.util;

import com.vincentderk.acircuitminer.miner.util.comparators.EntryProfitStateCom;
import com.vincentderk.acircuitminer.miner.util.comparators.EntryProfitCom;
import com.vincentderk.acircuitminer.miner.Graph;
import com.vincentderk.acircuitminer.miner.StateSingleOutput;
import com.vincentderk.acircuitminer.miner.canonical.EdgeCanonical;
import it.unimi.dsi.fastutil.ints.Int2IntAVLTreeMap;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenCustomHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.Arrays;
import java.util.Map.Entry;
import java.util.stream.Stream;
import static com.vincentderk.acircuitminer.miner.util.OperationUtils.codeToGraph;
import com.vincentderk.acircuitminer.miner.emulatable.neutralfinder.EmulatableBlock;
import it.unimi.dsi.fastutil.ints.IntAVLTreeSet;

/**
 * Contains some useful methods such as reading from an ac. file, calculating
 * the evaluation energy costs of occurrences or patterns, counting the amount
 * of internal operation nodes,...
 *
 * @author Vincent Derkinderen
 * @version 2.0
 */
public class Utils {

    /**
     * The instruction cost for each active input. Active being an actual number
     * instead of 0,1 or a meaningless number.
     */
    public static final int INSTRUCTION_COST_EXTRA = 6;

    /**
     * Base cost of an instruction (node) in an AC.
     */
    public static final int INSTRUCTION_COST_BASE = 70;

    /**
     * Cost of in- or output port for an operation in an AC.
     */
    public static final int IO_COST = 50;

    /**
     * Cost of a sum operation in an AC.
     */
    public static final double SUM_COST = 0.9;

    /**
     * Cost of an inactive sum operation in an AC.
     */
    public static final double SUM_COST_INACTIVE = 0.09;

    /**
     * Cost of a multiplication operation in an AC.
     */
    public static final int MULTIPLICATION_COST = 4;

    /**
     * Cost of an inactive multiplication operation in an AC.
     */
    public static final double MULTIPLICATION_COST_INACTIVE = 0.4;

    /**
     * Read an AC from .ac format.
     * <p>
     * <b>Note: Every node used by a node must be preceded to it. e.g if "* 3 6
     * 2 8" then node 6, 2 and 8 must have been declared already.</b>
     *
     * @param reader The reader to read the ac from.
     * @return The Graph (Arithmetic circuit) that was read from the .ac file
     * @throws IOException
     */
    public static Graph readACStructure(Reader reader) throws IOException {
        Graph graph = null;
        String line;
        int last_v = 0;

        try (BufferedReader br = new BufferedReader(reader)) {
            while ((line = br.readLine()) != null) {
                if (line.isEmpty()) {
                    continue;
                }
                if (line.startsWith("#")) {
                    System.out.printf("Skipped a line: [%s]\n", line);
                    continue;
                }
                String[] tokens = line.split("\\s+");

                if (tokens[0].equals("nnf")) {
                    int vertexNb = Integer.parseInt(tokens[1]);
                    if (vertexNb > 2000000000) {
                        throw new IllegalArgumentException("No AC with more than 2 billion nodes allowed");
                    }
                    //int edgeNb = Integer.parseInt(tokens[2]);
                    graph = new Graph(vertexNb);

                    System.out.println(line);
                } else if (tokens[0].equalsIgnoreCase("l")) {
                    graph.addVertex(last_v, Graph.INPUT);
                    last_v++;

                } else if (tokens[0].equals("*") || tokens[0].equals("+")
                        || tokens[0].equalsIgnoreCase("O") || tokens[0].equalsIgnoreCase("A")) {
                    switch (tokens[0]) {
                        case "*":
                        case "A":
                            graph.addVertex(last_v, Graph.PRODUCT);
                            break;

                        case "+":
                        case "O":
                            graph.addVertex(last_v, Graph.SUM);
                            break;
                        default:
                            System.out.println("Unhandeled token " + tokens[0]);
                    }

                    int start = (tokens[0].equalsIgnoreCase("O")) ? 2 : 1;
                    int nb = Integer.parseInt(tokens[start]);
                    start++;
                    for (int i = start; i < nb + start; i++) {
                        int e = Integer.parseInt(tokens[i]);
                        graph.addEdge(e, last_v);
                    }

                    last_v++;

                } else {
                    System.out.println("Could not parse: " + line);
                }
            }
        }
        graph.finishBuild();
        return graph;
    }

    private static long count = 0;

    /**
     * Print out the given patterns in a sorted order (low to high profit
     * according to {@link EntryProfitCom}).
     *
     * @param patterns The patterns to print
     * @param printEmpty Whether to print patterns that have no occurrences.
     * @return The total amount of occurrences found.
     */
    public static long writeResults(Object2ObjectOpenCustomHashMap<long[], ObjectArrayList<int[]>> patterns, final boolean printEmpty) {
        count = 0;
        patterns.object2ObjectEntrySet().parallelStream()
                .sorted(new EntryProfitCom(true))
                .forEachOrdered(p -> writeOccurence(p, printEmpty));

        System.out.println("Total count of " + count);

        return count;
    }

    /**
     * Print out the given patterns in a sorted order (low to high profit
     * according to {@link EntryProfitCom}).
     *
     * @param patterns The patterns to print
     * @param printEmpty Whether to print patterns that have no occurrences.
     * @return The total amount of occurrences found.
     */
    public static long writeResults(Entry<long[], ObjectArrayList<int[]>>[] patterns, final boolean printEmpty) {
        count = 0;
        Stream.of(patterns).parallel()
                .sorted(new EntryProfitCom(true))
                .forEachOrdered(p -> writeOccurence(p, printEmpty));

        System.out.println("Total count of " + count);

        return count;
    }

    /**
     * Print the given pattern p and increase the count with the occurrenceCount
     * of the given pattern.
     *
     * @param p The pattern to print (entry of pattern and its occurrences).
     * @param printEmpty Whether to print patterns that have no occurrences.
     */
    private static void writeOccurence(Entry<long[], ObjectArrayList<int[]>> p, final boolean printEmpty) {
        if (printEmpty || !p.getValue().isEmpty()) {
            double compressProfit = patternProfit(p.getKey(), p.getValue());
            System.out.println("(" + String.format("%.2f", compressProfit) + ") " + p.getValue().size() + " occurrences of " + EdgeCanonical.printCode(p.getKey()));
            count += p.getValue().size();
        }
    }

    /**
     * Get the amount of (internal) vertices in the given code.
     *
     * @param code The code the get the amount of internal vertices of.
     * @return The amount of internal vertices. This is equal to the amount of
     * distinct starting points of all edges.
     */
    public static long getNbVertices(long[] code) {
        long count = 1;
        long prev = 0;

        for (long l : code) {
            if (l < Long.MAX_VALUE - Graph.HIGHEST_OP) {
                long left = l >> 32;
                if (left != prev) {
                    prev = left;
                    count++;
                }
            }
        }
        return count;
    }

    /**
     * Get the amount of internal edges in the given code.
     * <p>
     * An internal edge is an edge where both vertices are internal vertices.
     *
     * @param code The code to check the internal edge count of.
     * @return The amount of internal edges in the given code.
     */
    public static long getInternalEdgeCount(long[] code) {
        /* Get internal nodes */
        long[] internal = new long[code.length];
        Arrays.fill(internal, -1);
        internal[0] = 0;

        long prev = 0;
        int index = 1;
        for (long l : code) {
            if (l < Long.MAX_VALUE - Graph.HIGHEST_OP) {
                long left = l >> 32;
                if (left != prev) {
                    prev = left;
                    internal[index++] = left;
                }
            }
        }

        /* Calculate amount of internal edges */
        int count = 0;
        for (long l : code) {
            if (l < Long.MAX_VALUE - Graph.HIGHEST_OP) {
                int right = (int) ((((long) 1 << 32) - 1) & l);
                if (Arrays.binarySearch(internal, 0, index, right) >= 0) {
                    count++;
                }
            }
        }
        return count;
    }

    /**
     * The cost of evaluating the given pattern (code) as if it the only
     * available hardware operations were + and *.
     * <p>
     * <b> Note: The cost of + and * is independent of the amount of inputs of
     * it. So +/3 = +/2.</b>
     *
     * @param code The code that represents the pattern to get the cost of.
     * @param occurrenceCount The amount of occurrences of the given pattern
     * @return The sum of the instruction cost, the input cost, the output cost
     * and the cost of the operations; multiplied with the amount of
     * occurrences.
     * <br>
     * Each operation is an instruction so this is
     * {@link #INSTRUCTION_COST_BASE}*AmountOfOperations +
     * {@link #INSTRUCTION_COST_EXTRA}*AmountOfInputEdges
     * <br>
     * Each operation has 1 output so this is
     * {@link #IO_COST}*AmountOfOperations.
     * <br>
     * The cost of operations is {@link #SUM_COST}*AmountOfSumOperations +
     * {@link #MULTIPLICATION_COST}*AmountOfMultOperations
     * <br>
     * The cost of input is {@link #IO_COST}*AmountOfInputEdges
     *
     */
    public static double patternOccurrenceCost(long[] code, int occurrenceCount) {
        if (occurrenceCount == 0) {
            return 0;
        } else {
            int sumCount = 0;
            int multCount = 0;

            for (long c : code) {
                if (c >= Long.MAX_VALUE - Graph.HIGHEST_OP) {
                    long op = Long.MAX_VALUE - c;

                    if (op == Graph.PRODUCT || op == Graph.PRODUCT_OUTPUT) {
                        multCount++;
                    } else if (op == Graph.SUM || op == Graph.SUM_OUTPUT) {
                        sumCount++;
                    }
                }
            }

            //Each operation = 50 + 2 (output)
            //Each +: 1 (# * operations)
            //Each *: 4 (# + operations)
            //#edges: 2 (= input)   (length of code - #operations)
            int edgeCount = (code.length - (sumCount + multCount));
            int opCount = sumCount + multCount;
            long instructionAndOutputCost = (INSTRUCTION_COST_BASE + IO_COST) * opCount;
            instructionAndOutputCost += INSTRUCTION_COST_EXTRA * edgeCount; //+ 6*active nodes
            double operationCosts = SUM_COST * sumCount + MULTIPLICATION_COST * multCount;

            long inputCost = (code.length - (sumCount + multCount)) * IO_COST; //length - #operations
            return (operationCosts + instructionAndOutputCost + inputCost) * occurrenceCount;
        }
    }

    /**
     * The cost of evaluating the given pattern as if it was an available
     * hardware component.
     * <p>
     * <b>Note: The cost of + and * is independent of the amount of inputs of
     * it. So +/3 = +/2.</b>
     * <br>If another pattern is to be emulated, use
     * {@link #patternBlockCost(EmulatableBlock, int)} as it accounts for
     * inactive nodes and inputs.
     *
     * @param pattern The pattern to evaluate.
     * @param occurrenceCount The amount of occurrences of the given pattern
     * @return The sum of the instruction cost, the input cost, the output cost
     * and the cost of the operations; multiplied with the amount of
     * occurrences.
     */
    public static double patternBlockCost(long[] pattern, int occurrenceCount) {
        if (occurrenceCount == 0) {
            return 0;
        } else {
            int sumCount = 0;
            int multCount = 0;

            for (long c : pattern) { //TODO: test correctness
                if (c >= Long.MAX_VALUE - Graph.HIGHEST_OP) {
                    long op = Long.MAX_VALUE - c;

                    if (op == Graph.PRODUCT || op == Graph.PRODUCT_OUTPUT) {
                        multCount++;
                    } else if (op == Graph.SUM || op == Graph.SUM_OUTPUT) {
                        sumCount++;
                    }
                }
            }

            int inputCount = inputNodeCount(pattern);
            long instructionAndOutputCost = INSTRUCTION_COST_BASE + IO_COST
                    + INSTRUCTION_COST_EXTRA * inputCount;
            double operationCosts = SUM_COST * sumCount + MULTIPLICATION_COST * multCount;
            long inputCost = IO_COST * inputCount;
            return (operationCosts + instructionAndOutputCost + inputCost) * occurrenceCount;
        }
    }

    /**
     * The cost of evaluating the pattern P that formed the emulatedBlock to
     * emulate the pattern {@link EmulatableBlock#emulatedCode}, as if P was
     * available as a hardware block.
     * <p>
     * <b>Note: The cost of + and * is independent of the amount of inputs of
     * it. So +/3 = +/2.</b>
     * <br>If the hardware block P is used with all actual inputs, thus to
     * evaluate its own pattern, {@link #patternBlockCost(long[], int)} can be
     * used.
     *
     * @param emulatedBlock The information of the pattern that the hardware
     * block in question is to emulate. This is linked to the pattern of the
     * hardware block due to the information stored in the emulatedBlock object.
     * That information is based on the pattern that formed that block object.
     * @param occurrenceCount The amount of occurrences of the given pattern
     * {@link EmulatableBlock#emulatedCode}.
     * @return The sum of the instruction cost, the input cost, the output cost
     * and the cost of the operations; multiplied with the amount of
     * occurrences.
     * <br>
     * There is only 1 instruction so this is {@link #INSTRUCTION_COST_BASE} +
     * {@link #INSTRUCTION_COST_EXTRA}*activeInputCount
     * <br>
     * There is only 1 output so this is {@link #IO_COST}.
     * <br>
     * The cost of operations is {@link #SUM_COST}*AmountOfActiveSumOperations +
     * {@link #MULTIPLICATION_COST}*AmountOfActiveMultOperations +
     * {@link #SUM_COST_INACTIVE}*AmountOfInactiveSumOperations +
     * {@link #MULTIPLICATION_COST_INACTIVE}*AmountOfInactiveSumOperations +
     * <br>
     * The cost of input is {@link #IO_COST} * activeInputCount
     *
     */
    public static double patternBlockCost(EmulatableBlock emulatedBlock, int occurrenceCount) {
        double operationCosts = SUM_COST * emulatedBlock.activeSumCount
                + MULTIPLICATION_COST * emulatedBlock.activeMultCount;
        double inactiveOperationCosts = SUM_COST_INACTIVE * emulatedBlock.inactiveSumCount
                + MULTIPLICATION_COST_INACTIVE * emulatedBlock.inactiveMultCount;

        int activeInputCount = emulatedBlock.activeInputCount;
        double instructionAndOutputCost = INSTRUCTION_COST_BASE
                + (activeInputCount * INSTRUCTION_COST_EXTRA) + IO_COST;
        double inputCost = activeInputCount * IO_COST;

        return (operationCosts + inactiveOperationCosts + instructionAndOutputCost + inputCost) * occurrenceCount;
    }

    /**
     * Get the estimated profit the given pattern (code) gives, multiplied by
     * the amount of occurrences.
     *
     * @param code The pattern to calculate the profit of, used to calculate the
     * internal edge count.
     * @param occurrenceCount The amount of replaceable occurrences the pattern
     * has.
     * @return The estimated profit of a pattern. Currently the cost of the
     * occurrence minus the cost of evaluating the pattern as if it was
     * available in a hardware block (times occurrenceCount).
     */
    public static double patternProfit(long[] code, int occurrenceCount) {
        if (occurrenceCount == 0) {
            return 0;
        } else {
            return patternOccurrenceCost(code, occurrenceCount) - patternBlockCost(code, occurrenceCount);
        }
    }

    /**
     * Get the estimated profit the given pattern (code) gives, multiplied by
     * the amount of occurrences. The patternSize and occurrenceCount is
     * retrieved from the given occurrences.
     *
     * @param code The pattern to calculate the profit of, used to calculate the
     * internal edge count.
     * @param occurrences The replaceable occurrences of the given pattern.
     * @return The estimated profit of a pattern. See
     * {@link #patternProfit(long[], int, int)}
     */
    public static double patternProfit(long[] code, ObjectArrayList<int[]> occurrences) {
        if (!occurrences.isEmpty()) {
            return patternProfit(code, occurrences.size());
        } else {
            return 0;
        }
    }

    /**
     * Get the estimated profit the given pattern (code) gives, multiplied by
     * the amount of occurrences. The patternSize and occurrenceCount is
     * retrieved from the given occurrences.
     *
     * @param code The pattern to calculate the profit of, used to calculate the
     * internal edge count.
     * @param occurrences The replaceable occurrences of the given pattern.
     * @return The estimated profit of a pattern. See
     * {@link #patternProfit(long[], int, int)}
     */
    public static double patternProfitState(long[] code, ObjectArrayList<StateSingleOutput> occurrences) {
        if (!occurrences.isEmpty()) {
            return patternProfit(code, occurrences.size());
        } else {
            return 0;
        }
    }

    /**
     * Filter out all but the x best patterns out of expandableStates. The best
     * is determined using the patternProfit,
     * {@link #patternProfit(long[], int, int)}.
     *
     * @param expandableStates The pattern-states mapping to filter.
     * @param xBest The x best (most occurring) patterns.
     * @return The xBest best patterns according to
     * {@link EntryStateCom}{@code (false)}.
     * @see EntryStateCom
     */
    public static Entry<long[], ObjectArrayList<StateSingleOutput>>[] filter(Object2ObjectOpenCustomHashMap<long[], ObjectArrayList<StateSingleOutput>> expandableStates, int xBest) {
        Entry<long[], ObjectArrayList<StateSingleOutput>>[] filtered
                = expandableStates.object2ObjectEntrySet().parallelStream()
                        .sorted(new EntryProfitStateCom(false)) //highToLow
                        .limit(xBest)
                        .toArray(Entry[]::new);

        return filtered;
    }

    /**
     * Count the amount of nodes (both internal and input) present in the given
     * code.
     *
     * @param code The code to get the amount of nodes of.
     * @return The amount of nodes (both internal and input) present in the
     * given code.
     */
    public static int nodeCount(long[] code) {
        IntSet set = new IntOpenHashSet();

        for (long l : code) {
            if (l < Long.MAX_VALUE - Graph.HIGHEST_OP) {
                int left = (int) (l >> 32);
                long mask = ((long) 1 << 32) - 1; //32 1 bits: 0..0111..111
                int right = (int) (mask & l);

                set.add(left);
                set.add(right);
            }
        }
        return set.size();
    }

    /**
     * Count the amount of input nodes present in the given code.
     *
     * @param code The code to get the amount of input nodes of.
     * @return The amount of input nodes present in the given code.
     */
    public static int inputNodeCount(long[] code) {
        IntSet set = new IntOpenHashSet(); //total node count
        int countOps = 0; // ops count

        for (long l : code) {
            if (l < Long.MAX_VALUE - Graph.HIGHEST_OP) {
                int left = (int) (l >> 32);
                long mask = ((long) 1 << 32) - 1; //32 1 bits: 0..0111..111
                int right = (int) (mask & l);

                set.add(left);
                set.add(right);
            } else {
                countOps++;
            }
        }
        return set.size() - countOps;
    }

    /**
     * Count the amount of operation nodes (so only internal nodes) present in
     * the given code.
     *
     * @param code The code to get the amount of operation nodes of.
     * @return The amount of operations present in the given code.
     */
    public static int operationNodeCount(long[] code) {
        int countOps = 0;

        for (long l : code) {
            if (l >= Long.MAX_VALUE - Graph.HIGHEST_OP) {
                countOps++;
            }
        }
        return countOps;
    }

    /**
     * Count the amount of operation nodes (so only internal nodes) present in
     * the given graph.
     *
     * @param g The graph to get the amount of operation nodes of.
     * @return The amount of labels in g that are not equal to
     * {@link Graph#INPUT}.
     */
    public static int operationNodeCount(Graph g) {
        int opCount = 0;

        for (short s : g.label) {
            if (s != Graph.INPUT) {
                opCount++;
            }
        }
        return opCount;
    }

    /**
     * Get a mapping from the id in the code to the vertex that is associated
     * with it. The whole code is iterated over to compute the result.
     * <br> Note this maps only the vertices. This does not include the input
     * nodes.
     *
     * @param code The code that represents the pattern of which vertices form
     * an instance.
     * @param vertices The vertices in order of appearance in code.
     * @return A mapping from the id in the code to the vertex that is
     * associated with it.
     * @throws ArrayIndexOutOfBoundsException When there are fewer vertices than
     * there are appearing in code.
     */
    public static Int2IntMap getVerticesMap(long[] code, int[] vertices) {
        Int2IntMap map = new Int2IntAVLTreeMap();
        int index = 0;
        int count = 0;

        while (index < code.length) {
            //Get left element of group
            int left = (int) (code[index] >> 32);
            map.put(left, vertices[count++]);

            //Skip to the next group
            while (code[index] < Long.MAX_VALUE - Graph.HIGHEST_OP) {
                index++;
            }
            index++;
        }

        return map;
    }

    /**
     * Get a map of codeId's mapped to their appropriate input index. For
     * example, if node 8 in the code is the 3rd input (order given by
     * {@link #getInputNodes(Graph)}) then this map maps 8 to 3.
     *
     * @param patternGraph The graph of the pattern
     * @return A mapping of codeId to the appropriate input index of that
     * codeId.
     * @see #getInputNodes(Graph)
     */
    public static Int2IntMap getInputNodesMap(Graph patternGraph) {
        int[] orderedInput = getInputNodes(patternGraph);
        Int2IntMap map = new Int2IntOpenHashMap();

        for (int i = 0; i < orderedInput.length; i++) {
            map.put(orderedInput[i], i);
        }

        return map;
    }

    /**
     * Get a mapping of the pattern's input node to the id of that node
     * associated with the given occurrence, in the graph. e.g if node Id 5 is
     * an input node in the given code, the result contains a mapping from 5 to
     * the actual matching node Id in the bigger graph, given that we are
     * looking at the given occurrence.
     *
     * @param g The graph where the occurrence is in.
     * @param code The code of the pattern.
     * @param occurrence The occurrence we want a mapping from.
     * @return A mapping of the input nodes of the pattern to the node in the
     * graph.
     * @see #getInputMap(long[], Graph, Graph, int[], int[])
     */
    public static Int2IntMap getInputMap(Graph g, long[] code, int[] occurrence) {
        Graph patternGraph = codeToGraph(code);
        int[] inputs = getInputNodes(patternGraph);

        return Utils.getInputMap(code, patternGraph, g, inputs, occurrence);
    }

    /**
     * Get a mapping of the pattern's input node to the id of that node
     * associated with the given occurrence, in the graph. e.g if node Id 5 is
     * an input node in the given code, the result contains a mapping from 5 to
     * the actual matching node Id in the bigger graph, given that we are
     * looking at the given occurrence.
     *
     * @param code The code of the pattern.
     * @param patternGraph The graph associated with the given code.
     * @param g The graph where the occurrence is in.
     * @param inputs The input nodes of the patternGraph, in order of
     * appearance. (see {@link #getInputNodes(Graph)})
     * @param occurrence The occurrence we want a mapping from.
     * @return A mapping of the input nodes of the pattern to the node in the
     * graph.
     * @see #getInputMap(long[], Graph, Graph, int[], int[])
     */
    public static Int2IntMap getInputMap(long[] code, Graph patternGraph, Graph g, int[] inputs, int[] occurrence) {
        Int2IntMap vertMap = getVerticesMap(code, occurrence);

        Int2IntMap inputMap = new Int2IntAVLTreeMap();
        int[] sortedOcc = Arrays.copyOf(occurrence, occurrence.length);
        Arrays.sort(sortedOcc);

        for (int v : inputs) {
            /* Based on the parents of v: figure out the nodes that have all these parents. */
            int[] parents = Arrays.stream(patternGraph.out[v]).map(x -> vertMap.get(x)).sorted().toArray();

            //Base
            IntSet possibleNodes = new IntOpenHashSet();
            for (int node : new IntArrayList(g.inc[parents[0]])) {
                if (Arrays.binarySearch(sortedOcc, node) < 0) {
                    possibleNodes.add(node);
                }
            }

            //Intersection
            for (int parent : parents) {
                possibleNodes.retainAll(new IntArrayList(g.inc[parent]));
                if (possibleNodes.size() == 1) {
                    break;
                }
            }

            /* Figure out which one node it is */
            if (possibleNodes.isEmpty()) {
                System.err.println("Error, no node match found when finding parents. Musn't happen.");
            } else if (possibleNodes.size() == 1) {
                inputMap.put(v, possibleNodes.iterator().nextInt());
            } else {
                //Multiple choices: pick one where the only parents is equal to parents or other non vertices parents.
                IntArrayList options = new IntArrayList();

                for (int option : possibleNodes) {
                    boolean bad = false;

                    for (int actualParent : g.out[option]) {
                        if (Arrays.binarySearch(parents, actualParent) < 0 && Arrays.binarySearch(occurrence, actualParent) >= 0) {
                            bad = true;
                            break;
                        }
                    }

                    if (!bad) {
                        options.add(option);
                    }
                }

                if (options.isEmpty()) {
                    System.err.println("Error, no node match found when finding parents. Musn't happen.");
                } else if (options.size() == 1) {
                    inputMap.put(v, options.getInt(0));
                } else {
                    for (int opt : options) {
                        if (!inputMap.containsValue(opt)) {
                            inputMap.put(v, opt);
                            break;
                        }
                    }
                }
            }
        }

        return inputMap;
    }

    /**
     * Reads the AC from the given reader (.ac file format) and returns the
     * literal map.
     *
     * @param reader The reader reading the AC file
     * @return The literal map of the AC file.
     * @throws java.io.IOException
     */
    public static Int2IntAVLTreeMap getLiteralMap(Reader reader) throws IOException {
        Int2IntAVLTreeMap literalMap = new Int2IntAVLTreeMap();

        String line;
        int last_v = 0;

        try (BufferedReader br = new BufferedReader(reader)) {
            while ((line = br.readLine()) != null) {
                if (line.isEmpty()) {
                    continue;
                }
                if (line.startsWith("#")) {
                    System.out.printf("Skipped a line: [%s]\n", line);
                    continue;
                }
                String[] tokens = line.split("\\s+");

                if (tokens[0].equals("nnf")) {
                    int vertexNb = Integer.parseInt(tokens[1]);
                    if (vertexNb > 2000000000) {
                        throw new IllegalArgumentException("No AC with more than 2 billion nodes allowed");
                    }
                    int edgeNb = Integer.parseInt(tokens[2]);
                } else if (tokens[0].equalsIgnoreCase("l")) {
                    literalMap.put(last_v, Integer.parseInt(tokens[1]));
                    last_v++;

                } else {
                    last_v++;
                }
            }
        }

        return literalMap;
    }

    /**
     * Get all nodes involved in the given occurrences as a set.
     *
     * @param occurrences The occurrences to get the nodes of.
     * @return A set of all nodes in the given occurrences list.
     */
    public static IntAVLTreeSet getInvolvedOpNodes(ObjectArrayList<int[]> occurrences) {
        return addInvolvedOpNodes(occurrences, null);
    }

    /**
     * Get all nodes involved in the given occurrences and add them to the given
     * startSet.
     *
     * @param occurrences The occurrences to get the nodes of.
     * @param startSet The set to further add to. Can be null or empty. Will be
     * changed.
     * @return A set of all nodes in the given occurrences list.
     */
    public static IntAVLTreeSet addInvolvedOpNodes(ObjectArrayList<int[]> occurrences, IntAVLTreeSet startSet) {
        IntAVLTreeSet set = startSet;
        if (set == null) {
            set = new IntAVLTreeSet();
        }

        for (int[] occ : occurrences) {
            for (int node : occ) {
                set.add(node);
            }
        }

        return set;
    }

    /**
     * Get the input nodes (indices) in order of appearance in the graph.
     * <br>
     * This iterates over all labels and returns the indices which have an input
     * label, in that order.
     *
     * @param graph The graph to get the input nodes of
     * @return The nodes (indices) that are input nodes in the given graph. The
     * order is from small to high index.
     */
    public static int[] getInputNodes(Graph graph) {
        IntArrayList list = new IntArrayList();

        for (int i = 0; i < graph.label.length; i++) {
            int label = graph.label[i];
            if (label == Graph.INPUT) {
                list.add(i);
            }
        }

        return list.toIntArray();
    }

}
