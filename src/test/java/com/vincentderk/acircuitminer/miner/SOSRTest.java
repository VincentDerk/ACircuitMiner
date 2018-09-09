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
 * @author Vincent
 */
public class SOSRTest {

    public SOSRTest() {
    }

    /**
     * The pattern to test the methods on.
     */
    private final long[] code = SOSR.stringToCode("(0,1)(0,2)(0,3)*(1,4)(1,5)*(2,4)(2,6)+(3,7)(3,8)+(4,9)(4,10)*(5,11)(5,12)*");

    /**
     * The second pattern to test the methods on. This one covers the case of a
     * shared input.
     */
    private final long[] code2 = SOSR.stringToCode("(0,1)(0,2)+(1,2)(1,3)*");

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
     * Test of {@link SOSR#getInternalEdgeCount(long[])}.
     */
    @org.junit.Test
    public void testGetInternalEdgeCount() {
        long expResult = 6;
        long result = SOSR.getInternalEdgeCount(code);
        assertEquals(expResult, result);
    }

    /**
     * Test of {@link SOSR#getInternalEdgeCount(long[])} with {@link #code2}.
     */
    @org.junit.Test
    public void testGetInternalEdgeCount2() {
        long expResult = 1;
        long result = SOSR.getInternalEdgeCount(code2);
        assertEquals(expResult, result);
    }

    /**
     * Test of {@link SOSR#getNodeCount(long[])}.
     */
    @org.junit.Test
    public void testGetNodeCount() {
        long expResult = 13;
        long result = SOSR.getNodeCount(code);
        assertEquals(expResult, result);
    }

    /**
     * Test of {@link SOSR#getNodeCount(long[])} with {@link #code2}.
     */
    @org.junit.Test
    public void testGetNodeCount2() {
        long expResult = 4;
        long result = SOSR.getNodeCount(code2);
        assertEquals(expResult, result);
    }

    /**
     * Test of {@link SOSR#getInputNodeCount(long[])}.
     */
    @org.junit.Test
    public void testGetInputNodeCount() {
        long expResult = 7;
        long result = SOSR.getInputNodeCount(code);
        assertEquals(expResult, result);
    }

    /**
     * Test of {@link SOSR#getInputNodeCount(long[])} with {@link #code2}.
     */
    @org.junit.Test
    public void testGetInputNodeCount2() {
        long expResult = 2;
        long result = SOSR.getInputNodeCount(code2);
        assertEquals(expResult, result);
    }

    /**
     * Test of {@link SOSR#getOperationNodeCount(long[])}.
     */
    @org.junit.Test
    public void testGetOperationNodeCount() {
        long expResult = 6;
        long result = SOSR.getOperationNodeCount(code);
        assertEquals(expResult, result);
    }

    /**
     * Test of {@link SOSR#getOperationNodeCount(long[])} with {@link #code2}.
     */
    @org.junit.Test
    public void testGetOperationNodeCount2() {
        long expResult = 2;
        long result = SOSR.getOperationNodeCount(code2);
        assertEquals(expResult, result);
    }

    /**
     * Test of {@link SOSR#getOperationNodeCount2(long[])}.
     */
    @org.junit.Test
    public void testGetOperationNodeCount2_() {
        long expResult = 6;
        long result = SOSR.getOperationNodeCount2(code);
        assertEquals(expResult, result);
    }

    /**
     * Test of {@link SOSR#getOperationNodeCount2(long[])} with {@link #code2}.
     */
    @org.junit.Test
    public void testGetOperationNodeCount2_2() {
        long expResult = 2;
        long result = SOSR.getOperationNodeCount2(code2);
        assertEquals(expResult, result);
    }

    /**
     * Test of {@link SOSR#patternOccurrenceCost(long[], int)} with
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
        double cost = SOSR.patternOccurrenceCost(code, occurrenceCount);
        assertEquals(expCost, cost, 0.0);

        occurrenceCount = 2;
        expCost *= 2;
        cost = SOSR.patternOccurrenceCost(code, occurrenceCount);
        assertEquals(expCost, cost, 0.0);

        occurrenceCount = 0;
        expCost = 0.0;
        cost = SOSR.patternOccurrenceCost(code, occurrenceCount);
        assertEquals(expCost, cost, 0.0);
    }

    /**
     * Test of {@link SOSR#patternOccurrenceCost(long[], int)} with
     * occurrenceCount == 0,1 and 2 for {@link #code2}.
     */
    @Test
    public void testPatternOccurrenceCost2() {
        /*
            occAmount(instr + operation + input + output)
            sum/2: 1x(82 + 0.9 + 100 + 50) = 232.9
            pro/2: 3x(82 + 4 + 100 + 50) = 236
            232.9 + 236 = 468.9
         */
        int occurrenceCount = 1;
        double expCost = 468.9;
        double cost = SOSR.patternOccurrenceCost(code2, occurrenceCount);
        assertEquals(expCost, cost, 0.0);

        occurrenceCount = 2;
        expCost *= 2;
        cost = SOSR.patternOccurrenceCost(code2, occurrenceCount);
        assertEquals(expCost, cost, 0.0);

        occurrenceCount = 0;
        expCost = 0.0;
        cost = SOSR.patternOccurrenceCost(code2, occurrenceCount);
        assertEquals(expCost, cost, 0.0);
    }

    /**
     * Test of {@link SOSR#patternBlockCost(long[], int)} with occurrenceCount =
     * 0,1 and 2.
     */
    @Test
    public void testPatternBlockCost_longArr_int() {
        /*
            input:7, sum:2, prod:4
            occAmount(instr + operation + input + output)
            (70 + 7*6) + (2*0.9 + 4*4) + 7*50 + 50 = 529.8
         */
        int occurrenceCount = 1;
        double expCost = 529.8;
        double cost = SOSR.patternBlockCost(code, occurrenceCount);
        assertEquals(expCost, cost, 0.0);

        occurrenceCount = 2;
        expCost *= 2;
        cost = SOSR.patternBlockCost(code, occurrenceCount);
        assertEquals(expCost, cost, 0.0);

        occurrenceCount = 0;
        expCost = 0.0;
        cost = SOSR.patternBlockCost(code, occurrenceCount);
        assertEquals(expCost, cost, 0.0);
    }

    /**
     * Test of {@link SOSR#patternBlockCost(long[], int)} with occurrenceCount =
     * 0,1 and 2 for {@link #code2}.
     */
    @Test
    public void testPatternBlockCost_longArr_int2() {
        /*
            input:2, sum:1, prod:1
            occAmount(instr + operation + input + output)
            (70 + 2*6) + (0.9 + 4) + 2*50 + 50 = 236.9
         */
        int occurrenceCount = 1;
        double expCost = 236.9;
        double cost = SOSR.patternBlockCost(code2, occurrenceCount);
        assertEquals(expCost, cost, 0.0);

        occurrenceCount = 2;
        expCost *= 2;
        cost = SOSR.patternBlockCost(code2, occurrenceCount);
        assertEquals(expCost, cost, 0.0);

        occurrenceCount = 0;
        expCost = 0.0;
        cost = SOSR.patternBlockCost(code2, occurrenceCount);
        assertEquals(expCost, cost, 0.0);
    }

    /**
     * Test of {@link SOSR#patternBlockCost(EmulatableBlock, int)}.
     */
    @org.junit.Test
    public void testPatternBlockCost_EmulatableBlock_int() {
        EmulatableBlock emulatedBlock = new EmulatableBlock();
        emulatedBlock.activeInputCount = 4;
        emulatedBlock.activeMultCount = 2;
        emulatedBlock.activeSumCount = 2;
        emulatedBlock.inactiveMultCount = 1;
        emulatedBlock.inactiveSumCount = 1;
        //emulatedCode = (0,1)(0,2)*(1,3)(1,4)+(2,3)(2,5)+(4,6)(4,7)*
        //input = 2,1,1,1,2,2,2

        int occurrenceCount = 0;
        double expResult = 0.0;
        double result = SOSR.patternBlockCost(emulatedBlock, occurrenceCount);
        assertEquals(expResult, result, 0.0);

        occurrenceCount = 1;
        expResult = 354.29; //70 + 24 + 50 + 200 + (1.8 + 8 + 0.4 + 0.09)
        result = SOSR.patternBlockCost(emulatedBlock, occurrenceCount);
        assertEquals(expResult, result, 0.0001);

        occurrenceCount = 2;
        expResult *= 2;
        result = SOSR.patternBlockCost(emulatedBlock, occurrenceCount);
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
            (70 + 7*6) + (2*0.9 + 4*4) + 7*50 + 50 = 529.8
        
            Profit: 1465,8 - 529.8 = 936
         */
        int occurrenceCount = 1;
        double expCost = 936;
        double cost = SOSR.patternProfit(code, occurrenceCount);
        assertEquals(expCost, cost, 0.0001);

        occurrenceCount = 2;
        expCost *= 2;
        cost = SOSR.patternProfit(code, occurrenceCount);
        assertEquals(expCost, cost, 0.0001);

        occurrenceCount = 0;
        expCost = 0.0;
        cost = SOSR.patternProfit(code, occurrenceCount);
        assertEquals(expCost, cost, 0.0);
    }

    /**
     * Test of {@link SOSR#patternProfit(long[], int)} with occurrenceCount =
     * 0,1 and 2 for {@link #code2}.
     */
    @Test
    public void testPatternProfit_longArr_int2() {
        /*
            Occurrence
            occAmount(instr + operation + input + output)
            sum/2: 1x(82 + 0.9 + 100 + 50) = 232.9
            pro/2: 3x(82 + 4 + 100 + 50) = 236
            232.9 + 236 = 468.9
            
            Block
            input:2, sum:1, prod:1
            occAmount(instr + operation + input + output)
            (70 + 2*6) + (0.9 + 4) + 2*50 + 50 = 236.9
        
            Profit: 468.9 - 236.9 = 232
         */
        int occurrenceCount = 1;
        double expCost = 232;
        double cost = SOSR.patternProfit(code2, occurrenceCount);
        assertEquals(expCost, cost, 0.0001);

        occurrenceCount = 2;
        expCost *= 2;
        cost = SOSR.patternProfit(code2, occurrenceCount);
        assertEquals(expCost, cost, 0.0001);

        occurrenceCount = 0;
        expCost = 0.0;
        cost = SOSR.patternProfit(code2, occurrenceCount);
        assertEquals(expCost, cost, 0.0);
    }

    /**
     * Test of {@link SOSR#stringToCode(java.lang.String)}.
     */
    @Test
    public void testStringToCode() {
        //(0,1)(0,2)(0,3)*(1,4)(1,5)*(2,4)(2,6)+(3,7)(3,8)+(4,9)(4,10)*(5,11)(5,12)*
        long[] expPattern = new long[]{ //Each line is about inc of 1 node
            1, 2, 3, Long.MAX_VALUE - Graph.PRODUCT_OUTPUT,
            ((1L << 32) | 4), ((1L << 32) | 5), Long.MAX_VALUE - Graph.PRODUCT,
            ((2L << 32) | 4), ((2L << 32) | 6), Long.MAX_VALUE - Graph.SUM,
            ((3L << 32) | 7), ((3L << 32) | 8), Long.MAX_VALUE - Graph.SUM,
            ((4L << 32) | 9), ((4L << 32) | 10), Long.MAX_VALUE - Graph.PRODUCT,
            ((5L << 32) | 11), ((5L << 32) | 12), Long.MAX_VALUE - Graph.PRODUCT
        };

        assertArrayEquals(expPattern, code);
    }

    /**
     * Test of {@link SOSR#stringToCode(java.lang.String)} for {@link #code2}.
     */
    @Test
    public void testStringToCode2() {
        //(0,1)(0,2)+(1,2)(1,3)*
        long[] expPattern = new long[]{ //Each line is about inc of 1 node
            1, 2, Long.MAX_VALUE - Graph.SUM_OUTPUT,
            ((1L << 32) | 2), ((1L << 32) | 3), Long.MAX_VALUE - Graph.PRODUCT,};

        assertArrayEquals(expPattern, code2);
    }

    /**
     * Test of {@link SOSR#stringToCode(java.lang.String)} with an illegal
     * argument.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testStringToCodeIllegalArg() {
        SOSR.stringToCode("(0,1)(0,2)*1,3)(1,4)+");
    }

    /**
     * Test of {@link SOSR#stringToCode(java.lang.String)} with an illegal
     * argument causing a number formatting issue.
     */
    @Test(expected = NumberFormatException.class)
    public void testStringToCodeNumberFormat() {
        SOSR.stringToCode("(0,1)(0,2)*(1,3(1,4)+");
    }

    /**
     * Test of {@link SOSR#stringToCode(java.lang.String)} with an illegal
     * argument (missing {@code , or )} )causing an IndexOutOfBoundsException.
     */
    @Test(expected = IndexOutOfBoundsException.class)
    public void testStringToCodeMissingComma() {
        SOSR.stringToCode("(0,1)(0,2)*(1,3)(14)+");
    }

    /**
     * Test of
     * {@link SOSR#patternProfit(long[], it.unimi.dsi.fastutil.objects.ObjectArrayList)}.
     */
    @org.junit.Test
    public void testPatternProfit_longArr_ObjectArrayList() {
        ObjectArrayList<int[]> occurrences = new ObjectArrayList<>();

        double expCost = 0.0;
        double cost = SOSR.patternProfit(code, occurrences);
        assertEquals(expCost, cost, 0.0);

        occurrences.add(new int[]{0, 1, 2, 3, 4, 5});
        expCost = 936;
        cost = SOSR.patternProfit(code, occurrences);
        assertEquals(expCost, cost, 0.0001);

        occurrences.add(new int[]{6, 7, 8, 9, 10, 11});
        expCost *= 2;
        cost = SOSR.patternProfit(code, occurrences);
        assertEquals(expCost, cost, 0.0001);
    }

    /**
     * Test of
     * {@link SOSR#patternProfit(long[], it.unimi.dsi.fastutil.objects.ObjectArrayList)}
     * with {@link #code2}.
     */
    @org.junit.Test
    public void testPatternProfit_longArr_ObjectArrayList2() {
        ObjectArrayList<int[]> occurrences = new ObjectArrayList<>();

        double expCost = 0.0;
        double cost = SOSR.patternProfit(code2, occurrences);
        assertEquals(expCost, cost, 0.0);

        occurrences.add(new int[]{0, 1});
        expCost = 232;
        cost = SOSR.patternProfit(code2, occurrences);
        assertEquals(expCost, cost, 0.0001);

        occurrences.add(new int[]{3, 4});
        expCost *= 2;
        cost = SOSR.patternProfit(code2, occurrences);
        assertEquals(expCost, cost, 0.0001);
    }

}
