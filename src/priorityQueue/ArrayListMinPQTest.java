package priorityQueue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import testSupport.LoggingExtension;
import testSupport.BeforeLoggingExtension;
import testSupport.AfterLoggingExtension;

/**
 * Test cases.
 * 
 * @author Matt Boutell. Created March 29, 2014.
 */
@ExtendWith(BeforeLoggingExtension.class)
@ExtendWith(LoggingExtension.class)
@ExtendWith(AfterLoggingExtension.class)
public class ArrayListMinPQTest {

	private static int points = 0; 
	
	/**
	 * Test method for {@link ArrayListMinPQ#insert(java.lang.Comparable)}.
	 */
	@Test
	public void testInsertAndSize() {
		ArrayListMinPQ<String> pq = new ArrayListMinPQ<String>();
		assertEquals(0, pq.size());
		pq.insert("this");
		assertEquals(1, pq.size());
		pq.insert("unit");
		assertEquals(2, pq.size());
		pq.insert("test");
		assertEquals(3, pq.size());
		pq.insert("isnt");
		assertEquals(4, pq.size());
		pq.insert("foolproof");
		assertEquals(5, pq.size());
		points += 2;
	}

	/**
	 * Test method for {@link ArrayListMinPQ#findMin()}.
	 */
	@Test
	public void testFindMinEmpty() {
		ArrayListMinPQ<Integer> pq = new ArrayListMinPQ<Integer>();
		assertNull("Expected null, because queue is empty", pq.findMin());
		points += 0;
	}

	/**
	 * Test method for {@link ArrayListMinPQ#findMin()}.
	 */
	@Test
	public void testFindMin() {
		ArrayListMinPQ<Integer> pq = new ArrayListMinPQ<Integer>();
		pq.insert(10);
		pq.insert(3);
		pq.insert(15);
		assertEquals((Integer)3, pq.findMin());
		points += 1;
	}

	/**
	 * Test method for {@link ArrayListMinPQ#findMin()}.
	 */
	@Test
	public void testFindMinStrings() {
		ArrayListMinPQ<String> pq = new ArrayListMinPQ<String>();
		pq.insert("outstanding");
		pq.insert("work");
		pq.insert("on");
		pq.insert("this");
		pq.insert("test");
		assertEquals("on", pq.findMin());
		points += 1;
	}

	/**
	 * Test method for {@link ArrayListMinPQ#removeMin()}.
	 */
	@Test
	public void testRemoveMinEmpty() {
		ArrayListMinPQ<Integer> pq = new ArrayListMinPQ<Integer>();
		assertNull("Expected null, because queue is empty",pq.deleteMin());
		points += 0;
	}

	/**
	 * Test method for {@link ArrayListMinPQ#removeMin()}.
	 */
	@Test
	public void testRemoveMin() {
		ArrayListMinPQ<Integer> pq = new ArrayListMinPQ<Integer>();
		pq.insert(10);
		pq.insert(3);
		pq.insert(15);
		pq.insert(12);
		assertEquals((Integer)3, pq.deleteMin());
		assertEquals((Integer)10, pq.deleteMin());
		assertEquals((Integer)12, pq.deleteMin());
		assertEquals((Integer)15, pq.deleteMin());
		assertNull("Expected null, because queue is empty",pq.deleteMin());
		points += 2;
	}

	/**
	 * Test method for {@link ArrayListMinPQ#removeMin()}.
	 */
	@Test
	public void testRemoveMinStrings() {

		ArrayListMinPQ<String> pq = new ArrayListMinPQ<String>();
		pq.insert("outstanding");
		pq.insert("work");
		pq.insert("on");
		pq.insert("this");
		pq.insert("test");
		assertEquals("on", pq.deleteMin());
		assertEquals("outstanding", pq.deleteMin());
		assertEquals("test", pq.deleteMin());
		assertEquals("this", pq.deleteMin());
		assertEquals("work", pq.deleteMin());
		assertNull("Expected null, because queue is empty",pq.deleteMin());
		points += 2;
	}

	/**
	 * Test method for {@link ArrayListMinPQ#isEmpty()}.
	 */
	@Test
	public void testIsEmpty() {
		ArrayListMinPQ<Integer> pq = new ArrayListMinPQ<Integer>();
		assertTrue(pq.isEmpty());
		pq.insert(10);
		assertFalse("Expected false", pq.isEmpty());
		points += 1;
	}

	/**
	 * Test method for {@link ArrayListMinPQ#clear()}.
	 */
	@Test
	public void testClear() {
		ArrayListMinPQ<Integer> pq = new ArrayListMinPQ<Integer>();
		pq.insert(10);
		pq.insert(3);
		pq.insert(15);
		pq.insert(12);
		pq.clear();
		assertEquals(0, pq.size());
		points += 1;
	}
	
	@AfterAll
	public static void displayPoints() {
		System.out.printf("PRIORITY QUEUE CORRECTNESS POINTS = %d of 10\n", points);
		System.out.printf("Efficency of 2 more points are for meeting efficiency requirements (checked manually)\n");
	}
}
