
import java.util.Random;

import junit.framework.TestCase;
import javax.security.TaintSet;

import java.io.FileWriter;
import java.io.IOException;
import java.lang.String;

public class TaintSetTest extends TestCase {
	public static FileWriter writer;
	TaintSet t0 = new TaintSet(0, 10, null);
	TaintSet t1 = new TaintSet(1, 2, null);
	TaintSet t2 = new TaintSet(1, 3, null);
	TaintSet t3 = new TaintSet(-1, 4, new int[]{0xAAAAAAAA});
	TaintSet t4 = new TaintSet(0, 31, new int[]{0xFF00FF00});
	TaintSet t5 = new TaintSet(-1, 31, new int[]{0xFF00FF00});
	TaintSet t6 = new TaintSet(-1, 35, new int[]{0xFF00FF00, 0xFFFFFFFF});
	
	TaintSet t7 = TaintSet.generate(new boolean[]{false, true, false, false});
	TaintSet t8 = TaintSet.generate(new boolean[]{false, true, false, true, false});
	
	public void testConstruct() {
		assertEquals("011", t1.toString());
		assertEquals("0101", t3.toString());
		assertEquals("1111111100000000111111110000000000", t4.toString(34));
		assertEquals("1111111000000001111111100000000000", t5.toString(34));
		assertEquals("1111111000000001111111100000000111100000", t6.toString(40));
	}
	
	public void testSingleIntervalConcat() {
		TaintSet s1 = TaintSet.generate(t0, 10, t0);
		assertTrue(s1.singleInterval());
		assertEquals("11111111111111111111", s1.toString());
		
		s1 = TaintSet.generate(t2, 4, s1);
		assertTrue(s1.singleInterval());
		assertEquals("011111111111111111111111", s1.toString());
	}
	
	// single cell concat, positive offset
	public void testConcat1() {
		TaintSet s1 = TaintSet.generate(t1, 7, t2);
		assertEquals("01100000111", s1.toString());
		assertFalse(s1.singleInterval());
		assertEquals("0110001100000111", TaintSet.generate(t1, 5, s1).toString());
	}
	
	public void testConcat2() {
		TaintSet s1 = TaintSet.generate(t3, 6, t2);
		assertEquals("0101000111", s1.toString());
		
		TaintSet s2 = TaintSet.generate(t3, 35, t2);
		assertEquals("010100000000000000000000000000000000111", s2.toString());
		
		TaintSet s3 = TaintSet.generate(s1, 12, s2);
		assertEquals("010100011100010100000000000000000000000000000000111", s3.toString());
		
		TaintSet s4 = TaintSet.generate(s2, 40, s1);
		assertEquals("01010000000000000000000000000000000011100101000111", s4.toString());
		
		// the "synchronized" bug
		TaintSet s5 = TaintSet.generate(s3, 51, s4);
		s5.printTaintBits();
		assertEquals("01010001110001010000000000000000000000000000000011101010000000000000000000000000000000011100101000111", s5.toString());
	}
	
	// test for single interval
	public void testConcat3() {
		TaintSet s1 = TaintSet.generate(t1, 3, t0);
		assertTrue(s1.singleInterval());
	}
	
	public void testShift() {
		int[] shift = new int[] { 10, -10, 32, -32, 0, 100, -100};

		for(int i : shift) {
			System.out.println(i + ": " + Integer.toBinaryString(~0 << i));
		}
	}
	
	public void testConcat4() {
		TaintSet s1 = TaintSet.generate(10);
		TaintSet s2 = TaintSet.generate(s1, 11, s1);
		assertEquals("111111111101111111111", s2.toString());
		s2.printTaintBits();
		TaintSet s3 = TaintSet.generate(s1, 22, s1);
		assertEquals("11111111110000000000001111111111", s3.toString());
		s3.printTaintBits();
		
		TaintSet s4 = TaintSet.generate(s2, 21, s3);
		assertEquals("11111111110111111111111111111110000000000001111111111", s4.toString());
		
		TaintSet s5 = TaintSet.generate(32);
		TaintSet s6 = TaintSet.generate(s5, 34, s5);
		assertEquals("111111111111111111111111111111110011111111111111111111111111111111", s6.toString());
		
		boolean[] alternating = new boolean[150];
		for (int i = 0; i < alternating.length; i++) {
			if (i % 2 == 0) {
				alternating[i] = true;
			}
		}
		TaintSet s7 = TaintSet.generate(alternating);
		
		assertEquals("10101010101010101010101010101010101010101010101010101010101010101010101010101010101010101010101010101010101010101010101010101010101010101010101010101", s7.toString());
		TaintSet s8 = TaintSet.generate(s4, 53, s7);
		
		assertEquals("1111111111011111111111111111111000000000000111111111110101010101010101010101010101010101010101010101010101010101010101010101010101010101010101010101010101010101010101010101010101010101010101010101010101", s8.toString());
	}
	
	public void testSubstring() {
		assertEquals("1110000000011111111", TaintSet.generate(t6, 4, 23).toString());
		assertEquals("11100000000111111110", TaintSet.generate(t6, 4, 24).toString());
		TaintSet s1 = TaintSet.generate(t6, 21, 36);
		s1.printTaintBits();
		assertEquals("11000000001111", s1.toString());
	}
	
	public void testMisc() {
		assertTrue(t7.singleInterval());
	}
	
	public void testNullness() {
		assertNull(TaintSet.generate(new boolean[]{false, false, false, false}));
		assertNotNull(t7);
		// t7.printTaintBits();
		assertNotNull(t8);
		// t8.printTaintBits();
		assertNull(TaintSet.generate(new boolean[]{}));
		// test null argument to the generators
	}
	
	private String getMethodName() {
		return Thread.currentThread().getStackTrace()[3].getMethodName();
	}
	
	public void testBenchmark() {
		boolean[] shortTaint = new boolean[12];
		boolean[] longTaint = new boolean[1024];
		getMethodName();
		for (int i = 0; i < 6; i++){
			shortTaint[i] = true;
		}
		for (int j = 0; j < 512; j++){
			longTaint[j] = true;
		}
		TaintSet t1 = TaintSet.generate(longTaint);

		TaintSet t2 = TaintSet.generate(t1, 1024, t1);
		TaintSet t3 = TaintSet.generate(t2, 2048, t1);
		
		TaintSet t4 = TaintSet.generate(t2, 2048, t2);
	}
	
	public void testAllTainted() {
		TaintSet allTainted = TaintSet.generate(TaintSet.allTainted, 200, TaintSet.allTainted);
		assertTrue(allTainted == TaintSet.allTainted);
		assertTrue(TaintSet.generate(allTainted, 12, 34) == TaintSet.allTainted);
		TaintSet s1 = TaintSet.generate(t0, 10, t0);
		assertTrue(TaintSet.generate(s1, 8, 20) == TaintSet.allTainted);
		assertTrue(TaintSet.generate(s1, 0, 20) == TaintSet.allTainted);
		assertTrue(TaintSet.generate(s1, 0, 5) == TaintSet.allTainted);
		assertTrue(TaintSet.generate(s1, 2, 12) == TaintSet.allTainted);
		assertTrue(TaintSet.generate(s1, 2, 22) != TaintSet.allTainted);
		for (int i = 0; i < 22; i++) {
			if (i <= 20) {
				assertTrue(TaintSet.generate(allTainted, 200, s1, i) == TaintSet.allTainted);
			} else {
				assertTrue(TaintSet.generate(allTainted, 200, s1, i) != TaintSet.allTainted);
			}
		}
		assertEquals(TaintSet.generate(0, 220).toString(), TaintSet.generate(allTainted, 200, s1).toString());
		assertEquals("1111111111111111111111111111111111111111111111111111111111111111111111111111111111011", TaintSet.generate(allTainted, 82, t1).toString());
		TaintSet s3 = TaintSet.generate(s1, 68, allTainted, 70);
		assertEquals("111111111111111111110000000000000000000000000000000000000000000000001111111111111111111111111111111111111111111111111111111111111111111111", s3.toString());
		assertTrue(TaintSet.generate(s1, 20, allTainted, 70) == TaintSet.allTainted);
		assertEquals("0110000000000000000001111111111111111111111111111111111111111111111111111111111111111111111", TaintSet.generate(t1, 21, allTainted, 70).toString()); // bug
		assertEquals("011000000000000000000000000000000000000000000000001111111111111111111111111111111111111111111111111111111111111111111111", TaintSet.generate(t1, 50, allTainted, 70).toString()); // bug
		
	}
	
	private static String randomString(int len) {
		byte[] bytes = new byte[len];
		for (int i = 0; i < len; i++) {
			bytes[i] = (byte) (myRandom.nextInt(95) + 32);
		}
		return new String(bytes);
	}
	
	private static Random myRandom = new Random();
}
