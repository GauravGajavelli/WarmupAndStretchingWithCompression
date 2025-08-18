package anagram;


import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import testSupport.LoggingExtension;

/**
 * Tests Anagram.
 * 
 * @author Claude Anderson.
 */


@ExtendWith(LoggingExtension.class)
public class AnagramTests {
	private static float points = 0;
	
	// tohoho
	
	/**
	 * Tests {@link Anagram#isAnagram(String, String)}.
	 */
	@Test
	public void testAnagram1() {
		assertFalse(Anagram.isAnagram("a", "b"),"Expected: false");
		assertTrue(Anagram.isAnagram("a", "a"),"Expected: true");
		points += 0.5;
	}

	/**
	 * Tests {@link Anagram#isAnagram(String, String)}.
	 */
	@Test
	public void testAnagram2() {
		assertFalse(Anagram.isAnagram("a", "b"),"Expected: false");
		assertTrue(Anagram.isAnagram("a", "A"),"Expected: true");
		points += 0.5;
	}

	/**
	 * Tests {@link Anagram#isAnagram(String, String)}.
	 */
	@Test
	public void testAnagram3() {
		assertFalse(Anagram.isAnagram("a", "b"),"Expected: false");
		assertTrue(Anagram.isAnagram("ab", "ba"),"Expected: true");
		points += 1;
	}

	/**
	 * Tests {@link Anagram#isAnagram(String, String)}.
	 */
	@Test
	public void testAnagram4() {
		assertFalse(Anagram.isAnagram("a", "b"),"Expected: false");
		assertTrue(Anagram.isAnagram("abc", "cba"),"Expected: true");
		points += 1;
	}

	/**
	 * Tests {@link Anagram#isAnagram(String, String)}.
	 */
	@Test
	public void testAnagram5() {
		assertFalse(Anagram.isAnagram("a", "b"),"Expected: false");
		assertTrue(Anagram.isAnagram("abc", "bca"),"Expected: true");
		points += 1;
	}

	/**
	 * Tests {@link Anagram#isAnagram(String, String)}.
	 */
	@Test
	public void testAnagram6() {
		assertFalse(Anagram.isAnagram("aabb", "bbbaa"),"Expected: false");
		assertTrue(Anagram.isAnagram("Claude Anderson", "Nuanced Ordeals"),"Expected: true");
		assertTrue(Anagram.isAnagram("Matt Boutell", "Total Tumble"),"Expected: true");
		assertTrue(Anagram.isAnagram("Nate Chenette", "Canteen Teeth"),"Expected: true");
		assertTrue(Anagram.isAnagram("Delvin Defoe", "Defend Olive"),"Expected: true"); // like Popeye!
		assertTrue(Anagram.isAnagram("Dave Fisher", "Evader Fish"),"Expected: true");
		assertTrue(Anagram.isAnagram("Dave Mutchler", "Traveled Much"),"Expected: true");
		assertTrue(Anagram.isAnagram("  Wollowski", "Silk Owl Ow"),"Expected: true");
		assertFalse(Anagram.isAnagram("aabb", "aaab"),"Expected: true");
		points += 1.5;
	}

	/**
	 * Tests {@link Anagram#isAnagram(String, String)}.
	 */
	@Test
	public void testAnagram7() {
		assertTrue(Anagram.isAnagram("aabb", "bbaa"),"Expected: true");
		assertFalse(Anagram.isAnagram("Claude Anderson", "Nuanced  Ordeals"),"Expected: false");
		assertFalse(Anagram.isAnagram("MA", "LB"),"Expected: false");
		assertFalse(Anagram.isAnagram("ay", "bx"),"Expected: false");
		assertFalse(Anagram.isAnagram("ab", "c"),"Expected: false");
		points += 1.5;
	}
	
	@AfterAll
	public static void showPoints() {
		System.out.printf("ANAGRAM POINTS = %.1f of 7.0\n", points);
	}
	
}
