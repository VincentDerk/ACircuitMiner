package com.vincentderk.acircuitminer.miner;

import com.vincentderk.acircuitminer.miner.emulatable.neutralfinder.EmulatableBlock;
import static com.vincentderk.acircuitminer.miner.util.Utils.INSTRUCTION_COST_BASE;
import static com.vincentderk.acircuitminer.miner.util.Utils.INSTRUCTION_COST_EXTRA;
import static com.vincentderk.acircuitminer.miner.util.Utils.IO_COST;
import static com.vincentderk.acircuitminer.miner.util.Utils.MULTIPLICATION_COST;
import static com.vincentderk.acircuitminer.miner.util.Utils.SUM_COST;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.Arrays;

/**
 * This class contains methods related to Multiple Output Single Root
 * ({@code MOSR}) graphs.
 * <p>
 * The {@code MOSR} graphs are represented by a {@code long[]} in the following
 * format: {@code (0,1)(0,2)A(1,3)(1,4)B...} where {@code (0,1)} stands for a
 * connection between node 0 and node 1, 0 being the node higher in the
 * hierarchy. A and B are the labels (operation) of the node. The idea is that
 * the label follows after enumerating all connections coming into the node.
 * First all incoming connections of node 0 are enumerated: 1 and 2. This is
 * followed by its operation, A. The same is repeated for node 1, then node
 * 2,...
 *
 * <p>
 * The labels of the outputting node use a label introduced to represent
 * outputting operations. e.g {@link Graph#PRODUCT_OUTPUT} or
 * {@link Graph#SUM_OUTPUT}.
 *
 * <p>
 * A graph with a single output and a single root is also an instance of this
 * more general class of multiple output and a single root. The methods located
 * in this class should also be applicable to the {@link SOSR} format.
 *
 * @author Vincent Derkinderen
 */
public class MOSR {

    private MOSR() {
    }

    //TODO: Remove the assumption that node 0 is an internal node.
    //This is done by pushing it inside the for-loop and setting prev=-1 and index=0
    //Test the above solution to be certain
    /**
     * Get the amount of internal edges in the given code.
     * <p>
     * An internal edge is an edge where both vertices are internal vertices.
     * The given {@code code} must be sorted.
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
                    //Only check right node as left one is definitly internal.
                    count++;
                }
            }
        }
        return count;
    }

    /**
     * Count the amount of nodes (both internal and input) present in the given
     * code.
     *
     * @param code The code to get the amount of nodes of.
     * @return The amount of nodes (both internal and input) present in the
     * given code.
     */
    public static int getNodeCount(long[] code) {
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
     * <p>
     * This assumes the only labels in the code are operations. So for example
     * no {@link Graph#INPUT}.
     *
     * @param code The code to get the amount of input nodes of.
     * @return The amount of input nodes present in the given code.
     */
    public static int getInputNodeCount(long[] code) {
        /**
         * Nb of Input nodes = Nb of unique nodes - Nb of operations.
         */
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
     * <p>
     * It does this by counting the amount of labels in the code. It assumes
     * there is no {@link Graph#INPUT} and {@link Graph#MARKER} present. If
     * there is, they will be counted as an operation node. This is a different
     * approach than {@link #getOperationNodeCount2(long[])}.
     *
     * @param code The code to get the amount of operation nodes of.
     * @return The amount of operations present in the given code.
     * @see #getOperationNodeCount2(long[])
     */
    public static int getOperationNodeCount(long[] code) {
        int countOps = 0;

        for (long l : code) {
            if (l >= Long.MAX_VALUE - Graph.HIGHEST_OP) {
                countOps++;
            }
        }
        return countOps;
    }

    /**
     * Get the amount of (internal) vertices in the given code.
     * <p>
     * It does this by counting the amount of chunks in the code. A chunk is a
     * sequence of edges coming into the same node. e.g. (0,1)(0,2) is a chunk,
     * (1,3)(1,4) is another chunk. This is a different approach than
     * {@link #getOperationNodeCount(long[])}.
     *
     * @param code The code the get the amount of internal vertices of.
     * @return The amount of internal vertices. This is equal to the amount of
     * distinct starting points of all edges.
     * @see #getOperationNodeCount(long[])
     */
    public static long getOperationNodeCount2(long[] code) {
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
     * Converts the given pattern in {@code String} format into code
     * ({@code long[]}) format.
     * <br>{@code patternString} is expected to follow the convention of
     * {@link SOSR}. e.g.
     * {@code (0,1)(0,2)(0,3)*o(1,4)(1,5)+(2,4)(2,6)*o(3,7)(3,8)} Only
     * {@code {+,+o,*,*o}} are allowed operations. Any deviation from this
     * convention can result in a wrong return value or thrown exceptions.
     *
     * @param patternString
     * @return The code of {@code patternString} in {@code long[]} format.
     * @throws IllegalArgumentException When a 'pair' in {@code patternString}
     * does not start with either {@code (}, {@code +}, {@code +o}, {@code *} or
     * {@code *o}.
     */
    public static long[] stringToCode(String patternString) {
        LongArrayList pattern = new LongArrayList();
        int index = 0; //Index of current pair in patternString

        while (index != patternString.length()) {
            switch (patternString.charAt(index)) {
                case '(':
                    int nextCommaIndex = patternString.indexOf(',', index);
                    int nextCloseBracketIndex = patternString.indexOf(')', index);
                    int first = Integer.parseInt(patternString.substring(index + 1, nextCommaIndex));
                    int second = Integer.parseInt(patternString.substring(nextCommaIndex + 1, nextCloseBracketIndex));
                    long value = ((long) first << 32) | second;
                    pattern.add(value);
                    index = nextCloseBracketIndex + 1;
                    break;

                case '*':
                    if (index + 1 < patternString.length() && patternString.charAt(index + 1) == 'o') {
                        pattern.add(Long.MAX_VALUE - Graph.PRODUCT_OUTPUT);
                    } else {
                        pattern.add(Long.MAX_VALUE - Graph.PRODUCT);
                    }
                    index++;
                    break;

                case '+':
                    if (index + 1 < patternString.length() && patternString.charAt(index + 1) == 'o') {
                        pattern.add(Long.MAX_VALUE - Graph.SUM_OUTPUT);
                    } else {
                        pattern.add(Long.MAX_VALUE - Graph.SUM);
                    }
                    index++;
                    break;

                default:
                    throw new IllegalArgumentException("Unexpected char: " + patternString.charAt(index) + " in SOSR.StringToCode(" + patternString + ")");
            }
        }
        return pattern.toLongArray();
    }

    /**
     * ------------------- Energy cost methods -------------------
     */
    /**
     * The cost of evaluating the given pattern ({@code code}) as if the only
     * available hardware operations were + and *.
     * <p>
     * <b><u>Note:</u></b>
     * <ul>
     * <li>The cost of + and * is independent of the arity (amount of inputs).
     * So +/3 = +/2.</li>
     * <li>This method assumes the only operations present in {@code code} are
     * {@link Graph#PRODUCT}, {@link Graph#PRODUCT_OUTPUT}, {@link Graph#SUM}
     * and {@link Graph#SUM_OUTPUT}. Furthermore, each operation node has
     * exactly one operation label.</li>
     * </ul>
     *
     * @param code The code that represents the pattern to get the cost of.
     * @param occurrenceCount The amount of occurrences of the given pattern
     * @return The sum of the instruction cost, the input cost, the output cost
     * and the cost of the operations, all multiplied with the amount of
     * occurrences.
     * <ul>
     * <li>Instruction cost:
     * {@link com.vincentderk.acircuitminer.miner.util.Utils#INSTRUCTION_COST_BASE}*AmountOfOperations
     * +
     * {@link com.vincentderk.acircuitminer.miner.util.Utils#INSTRUCTION_COST_EXTRA}*AmountOfInputEdges
     * (Each operation is an instruction)
     * </li>
     * <li>
     * Output cost:
     * {@link com.vincentderk.acircuitminer.miner.util.Utils#IO_COST}*AmountOfOperations
     * (Each operation has 1 output)
     * </li>
     * <li>
     * Operation cost:
     * {@link com.vincentderk.acircuitminer.miner.util.Utils#SUM_COST}*AmountOfSumOperations
     * +
     * {@link com.vincentderk.acircuitminer.miner.util.Utils#MULTIPLICATION_COST}*AmountOfMultOperations
     * </li>
     * <li>
     * Input cost:
     * {@link com.vincentderk.acircuitminer.miner.util.Utils#IO_COST}*AmountOfInputEdges
     * </li>
     * </ul>
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

            /* Calculations */
            int opCount = sumCount + multCount;
            int edgeCount = (code.length - opCount); //length - #operations = # edges
            long instructionAndOutputCost = (INSTRUCTION_COST_BASE + IO_COST) * opCount;
            instructionAndOutputCost += INSTRUCTION_COST_EXTRA * edgeCount; //+ 6*active nodes
            double operationCosts = SUM_COST * sumCount + MULTIPLICATION_COST * multCount;
            long inputCost = edgeCount * IO_COST; //length - #operations = # edges

            return (operationCosts + instructionAndOutputCost + inputCost) * occurrenceCount;
        }
    }

    //TODO: Verify cost calculation for MOSR.patternBlockCost(long[] pattern, int occurrenceCount)
    /**
     * The cost of evaluating the given pattern as if it was an available
     * hardware component.
     * <p>
     * <b><u>Note:</u></b>
     * <ul>
     * <li>The cost of + and * is independent of the arity (amount of inputs).
     * So +/3 = +/2.</li>
     * <li>This method assumes:
     * <ul><li>the only operations present in {@code code} are
     * {@link Graph#PRODUCT}, {@link Graph#PRODUCT_OUTPUT}, {@link Graph#SUM}
     * and {@link Graph#SUM_OUTPUT}.</li>
     * <li>Each operation node has exactly one operation label.</li>
     * </ul></li>
     * </ul>
     * If another pattern is to be emulated, use
     * {@link #patternBlockCost(EmulatableBlock, int)} as it accounts for
     * inactive nodes and inputs.
     *
     * @param pattern The pattern to evaluate.
     * @param occurrenceCount The amount of occurrences of the given pattern
     * @return The sum of the instruction cost, the input cost, the output cost
     * and the cost of the operations, all multiplied with the amount of
     * occurrences.
     */
    public static double patternBlockCost(long[] pattern, int occurrenceCount) {
        if (occurrenceCount == 0) {
            return 0;
        } else {
            int sumCount = 0;
            int multCount = 0;
            int outputCount = 0;

            for (long c : pattern) { //TODO: test correctness
                if (c >= Long.MAX_VALUE - Graph.HIGHEST_OP) {
                    long op = Long.MAX_VALUE - c;

                    if (op == Graph.PRODUCT) { //Java 8 can't switch over longs :(
                        multCount++;
                    } else if (op == Graph.SUM) {
                        sumCount++;
                    } else if (op == Graph.PRODUCT_OUTPUT) {
                        multCount++;
                        outputCount++;
                    } else if (op == Graph.SUM_OUTPUT) {
                        sumCount++;
                        outputCount++;
                    }
                }
            }

            int inputCount = getInputNodeCount(pattern);
            long instructionAndOutputCost = INSTRUCTION_COST_BASE + (IO_COST * outputCount)
                    + INSTRUCTION_COST_EXTRA * inputCount;
            double operationCosts = SUM_COST * sumCount + MULTIPLICATION_COST * multCount;
            long inputCost = IO_COST * inputCount;
            return (operationCosts + instructionAndOutputCost + inputCost) * occurrenceCount;
        }
    }

    //TODO: Implement, see {@link SOSR#patternBlockCost(EmulatableBlock, int)}
    public static double patternBlockCost(EmulatableBlock emulatedBlock, int occurrenceCount) {
        throw new UnsupportedOperationException("Not implemented, yet");
    }

    /**
     * Get the estimated profit the given pattern (code) gives, multiplied by
     * the amount of occurrences.
     *
     * @param code The pattern to calculate the profit of, used to calculate the
     * internal edge count.
     * @param occurrenceCount The amount of replaceable occurrences the pattern
     * has.
     * @return The estimated profit of a pattern. This is based on comparing the
     * cost of evaluating the pattern as if only + and * were available hardware
     * components versus the cost of evaluating the pattern as if it was
     * available as one hardware component. The profit is multiplied by the
     * amount of occurrences.
     * @see #patternOccurrenceCost(long[], int)
     * @see #patternBlockCost(long[], int)
     */
    public static double patternProfit(long[] code, int occurrenceCount) {
        if (occurrenceCount == 0) {
            return 0;
        } else {
            return (patternOccurrenceCost(code, 1) - patternBlockCost(code, 1)) * occurrenceCount;
        }
    }

    /**
     * Get the estimated profit the given pattern (code) gives, multiplied by
     * the amount of occurrences.
     *
     * @param code The pattern to calculate the profit of.
     * @param occurrences The replaceable occurrences of the given pattern.
     * @return The estimated profit of a pattern. This is based on comparing the
     * cost of evaluating the pattern as if only + and * were available hardware
     * components versus the cost of evaluating the pattern as if it was
     * available as one hardware component. The profit is multiplied by the
     * amount of occurrences.
     * @see #patternProfit(long[], int)
     */
    public static double patternProfit(long[] code, ObjectArrayList<int[]> occurrences) {
        if (!occurrences.isEmpty()) {
            return patternProfit(code, occurrences.size());
        } else {
            return 0;
        }
    }

}
