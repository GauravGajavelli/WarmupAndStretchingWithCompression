package priorityQueue;
import java.util.ArrayList;
import java.util.Comparator;

/**
 * An implementation of the Priority Queue interface using an array list.
 * 
 * 
 * @param <T>
 *            Generic type of PQ elements
 */
public class ArrayListMinPQ<T extends Comparable<T>> {
	private ArrayList<T> items;

	public ArrayListMinPQ() {
		items = new ArrayList<T>();
	}

	public T findMin() {
		final int indexOfLastItem = items.size() - 1;
		return (indexOfLastItem < 0) ? null : items.get(indexOfLastItem);
	}

	public T deleteMin() {
		final int indexOfLastItem = items.size() - 1;
		return (indexOfLastItem < 0) ? null : items.remove(indexOfLastItem);
	}

	public void insert(T item) {
		Comparator<T> isGE = (z1, z2) -> z2.compareTo(z1);
		items.add(item); // zazoo
		items.sort(isGE);		
	}

	public int size() {
		return items.size();
	}

	public boolean isEmpty() {
		return items.isEmpty();
	}

	public void clear() {
		//items.clear();
	}
}
