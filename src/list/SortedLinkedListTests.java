package list;

import static org.junit.Assert.*;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import testSupport.LoggingExtension;

/**
 * Tests {@link SortedLinkedList}.
 */

@ExtendWith(LoggingExtension.class)
public class SortedLinkedListTests {

	private static int points = 0;

	/**
	 * Tests {@link SortedLinkedList#SortedLinkedList()}.
	 */
	@Test
	public void testConstructor() {
		SortedLinkedList<Integer> list = new SortedLinkedList<Integer>();
		assertEquals("[]", list.toString());
	}

	/**
	 * Tests {@link SortedLinkedList#add(Comparable)}.
	 */
	@Test
	public void testAdd1() {
		SortedLinkedList<Integer> list = new SortedLinkedList<Integer>();
		list.add(10);
		assertEquals("[10]", list.toString());
		points += 2;
	}

	/**
	 * Tests {@link SortedLinkedList#add(Comparable)}.
	 */
	@Test
	public void testAdd2() {
		SortedLinkedList<Integer> list = new SortedLinkedList<Integer>();
		list.add(20);
		list.add(10);
		assertEquals("[10,20]", list.toString());
		points += 2;
	}

	/**
	 * Tests {@link SortedLinkedList#add(Comparable)}.
	 */
	@Test
	public void testAdd3() {
		SortedLinkedList<Integer> list = new SortedLinkedList<Integer>();
		list.add(10);
		list.add(5);
		list.add(20);
		list.add(15);
		list.add(18);
		list.add(25);
		assertEquals("[5,10,15,18,20,25]", list.toString());
		points += 3;
	}

	/**
	 * Tests {@link SortedLinkedList#SortedLinkedList(DoublyLinkedList)}.
	 */
	@Test
	public void testListConstructor() {
		DoublyLinkedList<Integer> list = new DoublyLinkedList<Integer>();
		list.add(10);
		list.add(5);
		list.add(20);
		list.add(7);
		list.add(25);
		list.add(18);
		list.add(19);
		SortedLinkedList<Integer> sortedList = new SortedLinkedList<Integer>(
				list);
		assertEquals("[5,7,10,18,19,20,25]", sortedList.toString());
		points += 3;
	}

	@AfterAll
	public static void showPoints() {
		System.out.printf("SORTED LINKED LIST POINTS = %d of 10\n", points);
	}
}
