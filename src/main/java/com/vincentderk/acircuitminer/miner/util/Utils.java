package com.vincentderk.acircuitminer.miner.util;

import com.vincentderk.acircuitminer.miner.util.comparators.EntryProfitCom;
import com.vincentderk.acircuitminer.miner.Graph;
import com.vincentderk.acircuitminer.miner.SOSR;
import com.vincentderk.acircuitminer.miner.canonical.EdgeCanonical;
import it.unimi.dsi.fastutil.ints.Int2IntAVLTreeMap;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
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
import it.unimi.dsi.fastutil.ints.IntAVLTreeSet;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import java.util.Comparator;

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
     * Cost of in- or output port for an hardware component in an AC.
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
     * <p>
     * This only supports the {@link SOSR} type of patterns.
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
     * <p>
     * This only supports the {@link SOSR} type of patterns.
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
     * <p>
     * This only supports the {@link SOSR} type of patterns.
     *
     * @param p The pattern to print (entry of pattern and its occurrences).
     * @param printEmpty Whether to print patterns that have no occurrences.
     * @see SOSR#patternProfit(long[],
     * it.unimi.dsi.fastutil.objects.ObjectArrayList)
     */
    private static void writeOccurence(Entry<long[], ObjectArrayList<int[]>> p, final boolean printEmpty) {
        if (printEmpty || !p.getValue().isEmpty()) {
            double compressProfit = SOSR.patternProfit(p.getKey(), p.getValue());
            System.out.println("(" + String.format("%.2f", compressProfit) + ") " + p.getValue().size() + " occurrences of " + EdgeCanonical.printCode(p.getKey()));
            count += p.getValue().size();
        }
    }

    /**
     * Filter out all but the x best objects out of expandableStates. The best
     * is determined using the given comparator. This uses a parallelStream.
     *
     * @param <T> Any {@link State}, for example.
     * @param expandableStates The pattern-states mapping to filter.
     * @param comparator The comparator used to determine the best
     * @param xBest The x best (most occurring) patterns.
     * @return The {@code xBest} best patterns according to {@code comparator}.
     * @see com.vincentderk.acircuitminer.miner.util.comparators
     */
    public static <T> Entry<long[], ObjectArrayList<T>>[] filter(Object2ObjectOpenCustomHashMap<long[], ObjectArrayList<T>> expandableStates, Comparator<Object2ObjectMap.Entry<long[], ObjectArrayList<T>>> comparator, int xBest) {
        Entry<long[], ObjectArrayList<T>>[] filtered
                = expandableStates.object2ObjectEntrySet().parallelStream()
                        .sorted(comparator)
                        .limit(xBest)
                        .toArray(Entry[]::new);

        return filtered;
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
     * Get a mapping of the input nodes of the pattern to the id of that node
     * associated with the given occurrence in the graph. e.g. if node Id 5 is
     * an input node in the given code, the result contains a mapping from 5 to
     * the actual matching node Id in the bigger graph {@code g}, given that we
     * are looking at the given occurrence.
     *
     * @param g The graph where the {@code occurrence} is in.
     * @param code The code (pattern) of the occurrence.
     * @param occurrence The occurrence we want a mapping from. The pattern of
     * this occurrence is given by {@code code}.
     * @return A mapping of the input nodes of the pattern to the nodes in graph
     * {@code g}.
     * @see #getInputMap(long[], Graph, Graph, int[], int[])
     */
    public static Int2IntMap getInputMap(Graph g, long[] code, int[] occurrence) {
        Graph patternGraph = Graph.codeToGraph(code);
        int[] inputs = patternGraph.getInputNodes();

        return Utils.getInputMap(code, patternGraph, g, inputs, occurrence);
    }

    /**
     * Get a mapping of the input nodes of the pattern to the id of that node
     * associated with the given occurrence in the graph. e.g. if node Id 5 is
     * an input node in the given code, the result contains a mapping from 5 to
     * the actual matching node Id in the bigger graph {@code g}, given that we
     * are looking at the given occurrence.
     *
     * @param code The code of the pattern.
     * @param patternGraph The graph associated with {@code code}.
     * @param g The graph where the {@code occurrence} is in.
     * @param inputs The input nodes of the {@code patternGraph}, in order of
     * appearance. (see {@link Graph#getInputNodes()})
     * @param occurrence The occurrence we want a mapping from.
     * @return A mapping of the input nodes of the pattern to the nodes in graph
     * {@code g}.
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

}
