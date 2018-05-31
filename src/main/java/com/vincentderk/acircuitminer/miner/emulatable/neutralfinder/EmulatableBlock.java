package com.vincentderk.acircuitminer.miner.emulatable.neutralfinder;

import com.vincentderk.acircuitminer.miner.canonical.EdgeCanonical;
import java.util.Arrays;

/**
 * Data structure that holds information regarding an emulation. When a pattern
 * P emulates another pattern, this can store: the emulated code, the input for
 * that emulation, which nodes in P were active, ...
 *
 * @author Vincent Derkinderen
 * @version 1.0
 */
public class EmulatableBlock {

    /**
     * The code of the pattern that is emulated
     */
    public long[] emulatedCode;

    /**
     * The inputs for pattern P to emulate {@link #emulatedCode}. Possible
     * values: {0,1,2} where 2 is an actual input value.
     */
    public byte[] input;

    /**
     * This provides the link between the inputs given to an emulated pattern
     * and the order of those inputs in the actual input given to the pattern P.
     * For example: There might be an occurrence whose input nodes to the
     * emulated pattern are in order: 5,10,8. To emulate this pattern, let us
     * say the input is 120202. Which 2 the 5,10 and 8 are is embedded in this
     * array. So {@code emulatedIndexToActualInputIndex[0]} represents the index
     * of the input in the pattern that corresponds to the first input of the
     * emulated pattern. In our example, this might be 1,3 or 5, the indices of the 2's in the actual
     * input 120202.
     */
    public int[] emulatedIndexToActualInputIndex;

    /**
     * The options of the emulation. This is the result of converting the P
     * pattern into a Graph, testing an input and checking the resulting options
     * in each node. An option being {0,1,2,3} where 3 refers to a meaningless
     * number and 2 to an actual number.
     * <p>
     * This can be used to check which nodes were active
     * ({@code options[x] == 2}).
     */
    public byte[] options;

    /**
     * Amount of active multiplication nodes
     */
    public int activeMultCount;

    /**
     * Amount of active sum nodes
     */
    public int activeSumCount;

    /**
     * Amount of inactive multiplication nodes
     */
    public int inactiveMultCount;

    /**
     * Amount of inactive sum nodes
     */
    public int inactiveSumCount;

    /**
     * Amount of active inputs. This may be different from the amount of 2's
     * present in {@link #input} as some of the input might be irrelevant due to
     * multiplication with zero.
     */
    public int activeInputCount;

    @Override
    public EmulatableBlock clone() {
        EmulatableBlock block = new EmulatableBlock();
        block.emulatedCode = emulatedCode;
        block.input = input;
        block.options = options;
        block.activeMultCount = activeMultCount;
        block.activeSumCount = activeSumCount;
        block.inactiveMultCount = inactiveMultCount;
        block.inactiveSumCount = inactiveSumCount;
        block.activeInputCount = activeInputCount;

        return block;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Emulated: ").append(EdgeCanonical.printCode(emulatedCode)).append("\n");
        sb.append("With input: ").append(Arrays.toString(input)).append("\n");
        sb.append("Options: ").append(Arrays.toString(options)).append("\n");
        sb.append("activeMultCount: ").append(activeMultCount).append("\n");
        sb.append("activeSumCount: ").append(activeSumCount).append("\n");
        sb.append("inactiveMultCount: ").append(inactiveMultCount).append("\n");
        sb.append("inactiveSumCount: ").append(inactiveSumCount).append("\n");
        sb.append("activeInputCount: ").append(activeInputCount).append("\n");

        return sb.toString();
    }

}
