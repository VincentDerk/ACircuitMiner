package com.vincentderk.acircuitminer.miner;

import com.vincentderk.acircuitminer.miner.emulatable.neutralfinder.EmulatableBlock;
import static com.vincentderk.acircuitminer.miner.util.Utils.INSTRUCTION_COST_BASE;
import static com.vincentderk.acircuitminer.miner.util.Utils.INSTRUCTION_COST_EXTRA;
import static com.vincentderk.acircuitminer.miner.util.Utils.IO_COST;
import static com.vincentderk.acircuitminer.miner.util.Utils.MULTIPLICATION_COST;
import static com.vincentderk.acircuitminer.miner.util.Utils.MULTIPLICATION_COST_INACTIVE;
import static com.vincentderk.acircuitminer.miner.util.Utils.SUM_COST;
import static com.vincentderk.acircuitminer.miner.util.Utils.SUM_COST_INACTIVE;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

/**
 * This class contains methods related to Single Output Single Root
 * ({@code SOSR}) graphs.
 * <p>
 * The {@code SOSR} graphs are represented by a {@code long[]} in the following
 * format: {@code (0,1)(0,2)A(1,3)(1,4)B...} where {@code (0,1)} stands for a
 * connection between node 0 and node 1, 0 being the node higher in the
 * hierarchy. A and B are the labels (operation) of the node. The idea is that
 * the label follows after enumerating all connections coming into the node.
 * First all incoming connections of node 0 are enumerated: 1 and 2. This is
 * followed by its operation, A. The same is repeated for node 1, then node
 * 2,...
 *
 * <p>
 * The label of the first (and only outputting) node uses a label introduced to
 * represent outputting operations. e.g {@link Graph#PRODUCT_OUTPUT} or
 * {@link Graph#SUM_OUTPUT}. Since these methods are for Single outputting
 * graphs, no other node can have any of those 'special' labels.
 *
 * <p>
 * A graph with a single output and a single root is also an instance of the
 * more general class of multiple output and a single root. The same methods
 * located in {@link MOSR} should therefore, in general, also be applicable.
 *
 * @author Vincent Derkinderen
 */
public class SOSR {

    private SOSR() {
    }

    /**
     * Get the amount of internal edges in the given code.
     * <p>
     * An internal edge is an edge where both vertices are internal vertices.
     * The given {@code code} must be sorted.
     *
     * @param code The code to check the internal edge count of.
     * @return The amount of internal edges in the given code.
     * @see MOSR#getInternalEdgeCount(long[])
     */
    public static long getInternalEdgeCount(long[] code) {
        return MOSR.getInternalEdgeCount(code);
    }

    /**
     * Count the amount of nodes (both internal and input) present in the given
     * code.
     *
     * @param code The code to get the amount of nodes of.
     * @return The amount of nodes (both internal and input) present in the
     * given code.
     * @see MOSR#getNodeCount(long[])
     */
    public static int getNodeCount(long[] code) {
        return MOSR.getNodeCount(code);
    }

    /**
     * Count the amount of input nodes present in the given code.
     * <p>
     * This assumes the only labels in the code are operations. So for example
     * no {@link Graph#INPUT}.
     *
     * @param code The code to get the amount of input nodes of.
     * @return The amount of input nodes present in the given code.
     * @see MOSR#getInputNodeCount(long[])
     */
    public static int getInputNodeCount(long[] code) {
        return MOSR.getInputNodeCount(code);
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
     * @see MOSR#getInputNodeCount(long[])
     */
    public static int getOperationNodeCount(long[] code) {
        return MOSR.getOperationNodeCount(code);
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
     * @see MOSR#getOperationNodeCount2(long[])
     */
    public static long getOperationNodeCount2(long[] code) {
        return MOSR.getOperationNodeCount2(code);
    }

    /**
     * Converts the given pattern in {@code String} format into code
     * ({@code long[]}) format.
     * <br>{@code patternString} is expected to follow the convention of
     * {@link SOSR}. e.g.
     * {@code (0,1)(0,2)(0,3)*o(1,4)(1,5)+(2,4)(2,6)*o(3,7)(3,8)} Only
     * {@code {+,*}} are allowed operations. Any deviation from this convention
     * can result in a wrong return value or thrown exceptions.
     *
     * <br> Any deviation from this convention can result in wrong results or
     * thrown exceptions.
     *
     * @param patternString
     * @return The code of {@code patternString} in {@code long[]} format.
     * @throws IllegalArgumentException When a 'pair' in {@code patternString}
     * does not start with either {@code (}, {@code +} or {@code *}.
     * @throws IndexOutOfBoundsException When there is a wrong number of , or )
     * in {@code patternString}.
     * @throws NumberFormatException When the correct format (number,number) is
     * not followed or the number is not parsable.
     */
    public static long[] stringToCode(String patternString) {
        LongArrayList pattern = new LongArrayList();
        int index = 0; //Index of current pair in patternString
        boolean opPassed = false; //Flag for first operation, set to output

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
                    if (opPassed) {
                        pattern.add(Long.MAX_VALUE - Graph.PRODUCT);
                    } else {
                        pattern.add(Long.MAX_VALUE - Graph.PRODUCT_OUTPUT);
                        opPassed = true;
                    }

                    index++;
                    break;

                case '+':
                    if (opPassed) {
                        pattern.add(Long.MAX_VALUE - Graph.SUM);
                    } else {
                        pattern.add(Long.MAX_VALUE - Graph.SUM_OUTPUT);
                        opPassed = true;
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
     * @see MOSR#patternOccurrenceCost(long[], int)
     */
    public static double patternOccurrenceCost(long[] code, int occurrenceCount) {
        return MOSR.patternOccurrenceCost(code, occurrenceCount);
    }

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
     * <li>Only one node counts as an outputting node.</li>
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

            for (long c : pattern) {
                if (c >= Long.MAX_VALUE - Graph.HIGHEST_OP) {
                    long op = Long.MAX_VALUE - c;

                    if (op == Graph.PRODUCT || op == Graph.PRODUCT_OUTPUT) {
                        multCount++;
                    } else if (op == Graph.SUM || op == Graph.SUM_OUTPUT) {
                        sumCount++;
                    }
                }
            }

            int inputCount = getInputNodeCount(pattern);
            long instructionAndOutputCost = INSTRUCTION_COST_BASE + IO_COST
                    + INSTRUCTION_COST_EXTRA * inputCount;
            double operationCosts = SUM_COST * sumCount + MULTIPLICATION_COST * multCount;
            long inputCost = IO_COST * inputCount;
            return (operationCosts + instructionAndOutputCost + inputCost) * occurrenceCount;
        }
    }

    /**
     * The cost of evaluating pattern P emulating
     * {@link EmulatableBlock#emulatedCode} as if P was available as a hardware
     * component.
     * <p>
     * <b>Note:</b> The cost of + and * is independent of the amount of inputs
     * of it. So +/3 = +/2.
     * <br>If the hardware block P is used with all actual inputs, thus to
     * evaluate its own pattern, {@link #patternBlockCost(long[], int)} can be
     * used.
     * <br>This method does not require P to calculate the cost as it uses the
     * information in the {@link EmulatableBlock emulatedBlock}.
     *
     * @param emulatedBlock The information of the emulation. This contains the
     * information used to calculate the evaluation cost.
     * @param occurrenceCount The amount of occurrences of the given pattern
     * {@link EmulatableBlock#emulatedCode emulatedBlock.emulatedCode}.
     * @return The sum of the instruction cost, the input cost, the output cost
     * and the cost of the operations, all multiplied with the amount of
     * occurrences.
     * <ul>
     * <li>Instruction cost:<br> {@link com.vincentderk.acircuitminer.miner.util.Utils#INSTRUCTION_COST_BASE} +
     * ({@link com.vincentderk.acircuitminer.miner.util.Utils#INSTRUCTION_COST_EXTRA}
     * * activeInputCount) (There is only one instruction.)</li>
     * <li>Output cost:<br>
     * {@link com.vincentderk.acircuitminer.miner.util.Utils#IO_COST}. (There is
     * only one output)</li>
     * <li>Operation cost:<br>
     * ({@link com.vincentderk.acircuitminer.miner.util.Utils#SUM_COST} *
     * AmountOfActiveSumOperations) +
     * ({@link com.vincentderk.acircuitminer.miner.util.Utils#MULTIPLICATION_COST}
     * * AmountOfActiveMultOperations) +
     * ({@link com.vincentderk.acircuitminer.miner.util.Utils#SUM_COST_INACTIVE}
     * * AmountOfInactiveSumOperations) +
     * ({@link com.vincentderk.acircuitminer.miner.util.Utils#MULTIPLICATION_COST_INACTIVE}
     * * AmountOfInactiveMultOperations)</li>
     * <li>Input cost:<br>
     * {@link com.vincentderk.acircuitminer.miner.util.Utils#IO_COST} *
     * activeInputCount</li>
     * </ul>
     *
     */
    public static double patternBlockCost(EmulatableBlock emulatedBlock, int occurrenceCount) {
        double operationCosts = SUM_COST * emulatedBlock.activeSumCount
                + MULTIPLICATION_COST * emulatedBlock.activeMultCount;
        double inactiveOperationCosts = SUM_COST_INACTIVE * emulatedBlock.inactiveSumCount
                + MULTIPLICATION_COST_INACTIVE * emulatedBlock.inactiveMultCount;

        double instructionAndOutputCost = INSTRUCTION_COST_BASE
                + (emulatedBlock.activeInputCount * INSTRUCTION_COST_EXTRA) + IO_COST;
        double inputCost = emulatedBlock.activeInputCount * IO_COST;

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
