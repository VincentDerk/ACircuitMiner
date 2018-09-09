package com.vincentderk.acircuitminer.miner;

import com.vincentderk.acircuitminer.miner.emulatable.neutralfinder.EmulatableBlock;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Vincent Derkinderen
 */
public class MOSRTest {

    public MOSRTest() {
    }

    /**
     * The pattern to test the methods on.
     */
    private final long[] code = MOSR.stringToCode("(0,1)(0,2)(0,3)*o(1,4)(1,5)*(2,4)(2,6)+o(3,7)(3,8)+o(4,9)(4,10)*o(5,11)(5,12)*");

    @BeforeClass
    public static void setUpClass() {
    }

    @AfterClass
    public static void tearDownClass() {
    }

    @Before
    public void setUp() {
    }

    @After
    public void tearDown() {
    }

    /**
     * Test of {@link MOSR#getInternalEdgeCount(long[])}.
     */
    @org.junit.Test
    public void testGetInternalEdgeCount() {
        long expResult = 6;
        long result = MOSR.getInternalEdgeCount(code);
        assertEquals(expResult, result);
    }

    /**
     * Test of {@link MOSR#getNodeCount(long[])}.
     */
    @org.junit.Test
    public void testGetNodeCount() {
        long expResult = 13;
        long result = MOSR.getNodeCount(code);
        assertEquals(expResult, result);
    }

    /**
     * Test of {@link MOSR#getInputNodeCount(long[])}.
     */
    @org.junit.Test
    public void testGetInputNodeCount() {
        long expResult = 7;
        long result = MOSR.getInputNodeCount(code);
        assertEquals(expResult, result);
    }

    /**
     * Test of {@link MOSR#getOperationNodeCount(long[])}.
     */
    @org.junit.Test
    public void testGetOperationNodeCount() {
        long expResult = 6;
        long result = MOSR.getOperationNodeCount(code);
        assertEquals(expResult, result);
    }

    /**
     * Test of {@link MOSR#getOperationNodeCount2(long[])}.
     */
    @org.junit.Test
    public void testGetOperationNodeCount2_() {
        long expResult = 6;
        long result = MOSR.getOperationNodeCount2(code);
        assertEquals(expResult, result);
    }

    /**
     * Test of {@link MOSR#stringToCode(java.lang.String)}.
     */
    @Test
    public void testStringToCode() {
        //(0,1)(0,2)(0,3)*o(1,4)(1,5)*(2,4)(2,6)+o(3,7)(3,8)+o(4,9)(4,10)*o(5,11)(5,12)*
        long[] expPattern = new long[]{ //Each line is about inc of 1 node
            1, 2, 3, Long.MAX_VALUE - Graph.PRODUCT_OUTPUT,
            ((1L << 32) | 4), ((1L << 32) | 5), Long.MAX_VALUE - Graph.PRODUCT,
            ((2L << 32) | 4), ((2L << 32) | 6), Long.MAX_VALUE - Graph.SUM_OUTPUT,
            ((3L << 32) | 7), ((3L << 32) | 8), Long.MAX_VALUE - Graph.SUM_OUTPUT,
            ((4L << 32) | 9), ((4L << 32) | 10), Long.MAX_VALUE - Graph.PRODUCT_OUTPUT,
            ((5L << 32) | 11), ((5L << 32) | 12), Long.MAX_VALUE - Graph.PRODUCT
        };

        assertArrayEquals(expPattern, code);
    }

    /**
     * Test of {@link MOSR#stringToCode(java.lang.String)} with an illegal
     * argument.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testStringToCodeIllegalArg() {
        MOSR.stringToCode("(0,1)(0,2)*o1,3)(1,4)+");
    }

    /**
     * Test of {@link MOSR#stringToCode(java.lang.String)} with an illegal
     * argument causing a number formatting issue.
     */
    @Test(expected = NumberFormatException.class)
    public void testStringToCodeNumberFormat() {
        MOSR.stringToCode("(0,1)(0,2)*o(1,3(1,4)+");
    }

    /**
     * Test of {@link MOSR#stringToCode(java.lang.String)} with an illegal
     * argument (missing {@code , or )} )causing an IndexOutOfBoundsException.
     */
    @Test(expected = IndexOutOfBoundsException.class)
    public void testStringToCodeMissingComma() {
        MOSR.stringToCode("(0,1)(0,2)*o(1,3)(14)+");
    }

    /**
     * Test of {@link MOSR#patternOccurrenceCost(long[], int)} with
     * occurrenceCount == 0,1 and 2.
     */
    @Test
    public void testPatternOccurrenceCost() {
        /*
            occAmount(instr + operation + input + output)
            sum/2: 2x(82 + 0.9 + 100 + 50) = 465,8
            pro/2: 3x(82 + 4 + 100 + 50) = 708
            pro/3: 1x(88 + 4 + 150 + 50) = 292
            465,8 + 708 + 292 = 1465,8
         */
        int occurrenceCount = 1;
        double expCost = 1465.8;
        double cost = MOSR.patternOccurrenceCost(code, occurrenceCount);
        assertEquals(expCost, cost, 0.0);

        occurrenceCount = 2;
        expCost *= 2;
        cost = MOSR.patternOccurrenceCost(code, occurrenceCount);
        assertEquals(expCost, cost, 0.0);

        occurrenceCount = 0;
        expCost = 0.0;
        cost = MOSR.patternOccurrenceCost(code, occurrenceCount);
        assertEquals(expCost, cost, 0.0);
    }

    /**
     * Test of {@link MOSR#patternBlockCost(long[], int)} with occurrenceCount =
     * 0,1 and 2.
     */
    @Test
    public void testPatternBlockCost_longArr_int() {
        /*
            input:7, sum:2, prod:4
            occAmount(instr + operation + input + output)
            (70 + 7*6) + (2*0.9 + 4*4) + 7*50 + 4*50 = 679.8
         */
        int occurrenceCount = 1;
        double expCost = 679.8;
        double cost = MOSR.patternBlockCost(code, occurrenceCount);
        assertEquals(expCost, cost, 0.0);

        occurrenceCount = 2;
        expCost *= 2;
        cost = MOSR.patternBlockCost(code, occurrenceCount);
        assertEquals(expCost, cost, 0.0);

        occurrenceCount = 0;
        expCost = 0.0;
        cost = MOSR.patternBlockCost(code, occurrenceCount);
        assertEquals(expCost, cost, 0.0);
    }

    /**
     * Test of {@link MOSR#patternBlockCost(EmulatableBlock, int)}.
     */
    @org.junit.Test
    public void testPatternBlockCost_EmulatableBlock_int() {
        EmulatableBlock emulatedBlock = new EmulatableBlock();
        emulatedBlock.activeInputCount = 4;
        emulatedBlock.activeMultCount = 2;
        emulatedBlock.activeSumCount = 2;
        emulatedBlock.inactiveMultCount = 1;
        emulatedBlock.inactiveSumCount = 1;
        //emulatedCode = (0,1)(0,2)*o(1,3)(1,4)+(2,3)(2,5)+o(4,6)(4,7)*
        //input = 2,1,1,1,2,2,2

        int occurrenceCount = 0;
        double expResult = 0.0;
        double result = MOSR.patternBlockCost(emulatedBlock, occurrenceCount);
        assertEquals(expResult, result, 0.0);

        occurrenceCount = 1;
        expResult = 0.0; // <-- insert correct value
        result = MOSR.patternBlockCost(emulatedBlock, occurrenceCount);
        assertEquals(expResult, result, 0.0001);

        occurrenceCount = 2;
        expResult *= 2;
        result = MOSR.patternBlockCost(emulatedBlock, occurrenceCount);
        assertEquals(expResult, result, 0.0001);
    }

    /**
     * Test of {@link SOSR#patternProfit(long[], int)} with occurrenceCount =
     * 0,1 and 2.
     */
    @Test
    public void testPatternProfit_longArr_int() {
        /*
            Occurrence
            occAmount(instr + operation + input + output)
            sum/2: 2x(82 + 0.9 + 100 + 50) = 465,8
            pro/2: 3x(82 + 4 + 100 + 50) = 708
            pro/3: 1x(88 + 4 + 150 + 50) = 292
            465,8 + 708 + 292 = 1465,8
            
            Block
            input:7, sum:2, prod:4
            occAmount(instr + operation + input + output)
            (70 + 7*6) + (2*0.9 + 4*4) + 7*50 + 4*50 = 679.8
        
            Profit: 1465,8 - 679.8 = 786
         */
        int occurrenceCount = 1;
        double expCost = 786;
        double cost = MOSR.patternProfit(code, occurrenceCount);
        assertEquals(expCost, cost, 0.0001);

        occurrenceCount = 2;
        expCost *= 2;
        cost = MOSR.patternProfit(code, occurrenceCount);
        assertEquals(expCost, cost, 0.0001);

        occurrenceCount = 0;
        expCost = 0.0;
        cost = MOSR.patternProfit(code, occurrenceCount);
        assertEquals(expCost, cost, 0.0);
    }

    /**
     * Test of
     * {@link MOSR#patternProfit(long[], it.unimi.dsi.fastutil.objects.ObjectArrayList)}.
     */
    @org.junit.Test
    public void testPatternProfit_longArr_ObjectArrayList() {
        ObjectArrayList<int[]> occurrences = new ObjectArrayList<>();

        double expCost = 0.0;
        double cost = MOSR.patternProfit(code, occurrences);
        assertEquals(expCost, cost, 0.0);

        occurrences.add(new int[]{0, 1, 2, 3, 4, 5});
        expCost = 786;
        cost = MOSR.patternProfit(code, occurrences);
        assertEquals(expCost, cost, 0.0001);

        occurrences.add(new int[]{6, 7, 8, 9, 10, 11});
        expCost *= 2;
        cost = MOSR.patternProfit(code, occurrences);
        assertEquals(expCost, cost, 0.0001);
    }

}
