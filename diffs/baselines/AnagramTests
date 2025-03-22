package anagram;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import testSupport.LoggingExtension;
import testSupport.BeforeLoggingExtension;
import testSupport.AfterLoggingExtension;
import testSupport.BeforeEachLoggingExtension;

/**
 * Tests Anagram.
 * 
 * @author Claude Anderson.
 */

@ExtendWith(BeforeLoggingExtension.class)
@ExtendWith(LoggingExtension.class)
@ExtendWith(AfterLoggingExtension.class)
@ExtendWith(BeforeEachLoggingExtension.class)
public class AnagramTests {
	private static float points = 0;
	
	
	/**
	 * Tests {@link Anagram#isAnagram(String, String)}.
	 */
	@Test
	public void testAnagram1() {
		assertFalse("Expected: false", Anagram.isAnagram("a", "b"));
		assertTrue("Expected: true", Anagram.isAnagram("a", "a"));
		points += 0.5;
	}

	/**
	 * Tests {@link Anagram#isAnagram(String, String)}.
	 */
	@Test
	public void testAnagram2() {
		assertFalse("Expected: false", Anagram.isAnagram("a", "b"));
		assertTrue("Expected: true",Anagram.isAnagram("a", "A"));
		points += 0.5;
	}

	/**
	 * Tests {@link Anagram#isAnagram(String, String)}.
	 */
	@Test
	public void testAnagram3() {
		assertFalse("Expected: false", Anagram.isAnagram("a", "b"));
		assertTrue("Expected: true", Anagram.isAnagram("ab", "ba"));
		points += 1;
	}

	/**
	 * Tests {@link Anagram#isAnagram(String, String)}.
	 */
	@Test
	public void testAnagram4() {
		assertFalse("Expected: false", Anagram.isAnagram("a", "b"));
		assertTrue("Expected: true", Anagram.isAnagram("abc", "cba"));
		points += 1;
	}

	/**
	 * Tests {@link Anagram#isAnagram(String, String)}.
	 */
	@Test
	public void testAnagram5() {
		assertFalse("Expected: false", Anagram.isAnagram("a", "b"));
		assertTrue("Expected: true", Anagram.isAnagram("abc", "bca"));
		points += 1;
	}

	/**
	 * Tests {@link Anagram#isAnagram(String, String)}.
	 */
	@Test
	public void testAnagram6() {
		assertFalse("Expected: false", Anagram.isAnagram("aabb", "bbbaa"));
		assertTrue("Expected: true", Anagram.isAnagram("Claude Anderson", "Nuanced Ordeals"));
		assertTrue("Expected: true", Anagram.isAnagram("Matt Boutell", "Total Tumble"));
		assertTrue("Expected: true", Anagram.isAnagram("Nate Chenette", "Canteen Teeth"));
		assertTrue("Expected: true", Anagram.isAnagram("Delvin Defoe", "Defend Olive")); // like Popeye!
		assertTrue("Expected: true", Anagram.isAnagram("Dave Fisher", "Evader Fish"));
		assertTrue("Expected: true", Anagram.isAnagram("Dave Mutchler", "Traveled Much"));
		assertTrue("Expected: true", Anagram.isAnagram("  Wollowski", "Silk Owl Ow"));
		assertFalse(Anagram.isAnagram("aabb", "aaab"));
		points += 1.5;
	}

	/**
	 * Tests {@link Anagram#isAnagram(String, String)}.
	 */
	@Test
	public void testAnagram7() {
		assertTrue("Expected: true", Anagram.isAnagram("aabb", "bbaa"));
		assertFalse("Expected: false", Anagram.isAnagram("Claude Anderson", "Nuanced  Ordeals"));
		assertFalse("Expected: false", Anagram.isAnagram("MA", "LB"));
		assertFalse("Expected: false", Anagram.isAnagram("ay", "bx"));
		assertFalse("Expected: false", Anagram.isAnagram("ab", "c"));
		points += 1.5;
	}
	
	@AfterAll
	public static void showPoints() {
		System.out.printf("ANAGRAM POINTS = %.1f of 7.0\n", points);
	}
	
}
