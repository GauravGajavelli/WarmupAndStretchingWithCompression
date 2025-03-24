package list;

import list.DoublyLinkedList.Node;
/**
 * 
 * @author anderson, chenett1
 * 
 * @param <T>
 *            Any Comparable type
 * 
 *            Stores a doubly linked list whose elements are kept in sorted order.
 */
public class SortedLinkedList<T extends Comparable<T>> extends
DoublyLinkedList<T> {
	
	DoublyLinkedList<T> dll;

	/**
	 * Creates an empty list
	 * 
	 */
	public SortedLinkedList() {
		dll = new DoublyLinkedList<T>();
	}

	/**
	 * Creates a sorted list containing the same elements as the parameter
	 * 
	 * @param other
	 *            the input list
	 */
	public SortedLinkedList(DoublyLinkedList<T> other) {
		this();
		DLLIterator<T> i = other.iterator(); // Creates an iterator using the factory method
		while (i.hasNext()) {     // Note how the iterator is used
			this.add(i.next());   // This line uses your add method below
		}	
	}

	/**
	 * Adds the given element to the list, keeping it sorted.
	 */
	public void add(T element) {
		// TODO: implement this method
		// Hint: the Node class is package-accessible via DoublyLinkedList<T>.Node
        //throw new UnsupportedOperationException("TODO: delete this statement and implement this operation.");
		Node current = this.head.next;
		while (current != this.tail && element.compareTo(current.data) > 0) {
			current = current.next;
		}
		current.prev.addAfter(element);
	}
	
	/**
	 * Prints the internal list.
	 */
	@Override
	public String toString() {
		return dll.toString();
	}

}
