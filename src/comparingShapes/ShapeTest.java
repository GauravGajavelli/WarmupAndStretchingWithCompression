package comparingShapes;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.TreeSet;

import org.junit.AfterClass;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import testSupport.LoggingExtension;

/**
 * Tests the circle and triangle classes
 * 
 * @author Matt Boutell. Created Dec 1, 2013.
 */

@ExtendWith(LoggingExtension.class)
public class ShapeTest {

	private static int points = 0;

	/**
	 * Tests the given method.
	 */
	@Test
	public void testSortCircles() {
		Circle c1 = new Circle(10);
		Circle c2 = new Circle(18.4);
		Circle c3 = new Circle(15.3);
		Circle c4 = new Circle(8.25);
		Circle c5 = new Circle(0.9);
		Circle[] circles = new Circle[] { c1, c2, c3, c4, c5 };
		System.out.println("Before sorting circles: ");
		printArray(circles);

		System.out.println("After sorting circles: ");
		Arrays.sort(circles);
		printArray(circles);
		assertEquals(c5, circles[0]);
		assertEquals(c4, circles[1]);
		assertEquals(c1, circles[2]);
		assertEquals(c3, circles[3]);
		assertEquals(c2, circles[4]);
		points += 2;
	}

	/**
	 * Tests the given method.
	 */
	@Test
	public void testSortTrianglesByPerimeter() {
		Triangle t1 = new Triangle(3, 4, 5);
		Triangle t2 = new Triangle(6, 6, 1);
		Triangle t3 = new Triangle(4.5, 4.5, 4.5);
		Triangle t4 = new Triangle(1, 1, 1.5);
		Triangle t5 = new Triangle(12, 5, 13);
		Triangle[] triangles = new Triangle[] { t1, t2, t3, t4, t5 };
		System.out.println("Before sorting triangles: ");
		printArray(triangles);

		System.out.println("After sorting triangles by perimeter: ");

		Comparator<Triangle> byPerimeter = new Comparator<Triangle>() {
			@Override
			public int compare(Triangle first, Triangle second) {
				return (int) Math.signum(first.perimeter() - second.perimeter());
			}
		};
		Arrays.sort(triangles, byPerimeter);
		printArray(triangles);
		assertEquals(t4, triangles[0]);
		assertEquals(t1, triangles[1]);
		assertEquals(t2, triangles[2]);
		assertEquals(t3, triangles[3]);
		assertEquals(t5, triangles[4]);
	}

	/**
	 * Tests the given method.
	 */
	@Test
	public void testSortTrianglesByArea() {
		Triangle t1 = new Triangle(3, 4, 5);
		Triangle t2 = new Triangle(6, 6, 1);
		Triangle t3 = new Triangle(4.5, 4.5, 4.5);
		Triangle t4 = new Triangle(1, 1, 1.5);
		Triangle t5 = new Triangle(12, 5, 13);
		Triangle[] triangles = new Triangle[] { t1, t2, t3, t4, t5 };
		System.out.println("Before sorting triangles: ");
		printArray(triangles);

		System.out.println("After sorting triangles by area: ");
		// A comparator to compare triangles by area,
		// so we can sort by area. Then pass it as a second argument to the
		// sort method.
		Comparator<Triangle> byArea = new TriangleAreaComparator();
		Arrays.sort(triangles, byArea);
		printArray(triangles);
		assertEquals(t4, triangles[0]);
		assertEquals(t2, triangles[1]);
		assertEquals(t1, triangles[2]);
		assertEquals(t3, triangles[3]);
		assertEquals(t5, triangles[4]);
		points += 4;
	}

	/**
	 * Tests the given method.
	 */
	@Test
	public void testSortTrianglesByAreaUsingTreeSet() {
		Triangle t1 = new Triangle(3, 4, 5);
		Triangle t2 = new Triangle(6, 6, 1);
		Triangle t3 = new Triangle(4.5, 4.5, 4.5);
		Triangle t4 = new Triangle(1, 1, 1.5);
		Triangle t5 = new Triangle(12, 5, 13);
		Triangle[] triangles = new Triangle[] { t1, t2, t3, t4, t5 };
		System.out.println("Before sorting triangles: ");
		printArray(triangles);
		System.out.println("Sort by area using a TreeSet: ");

		// You can sort the triangles without calling sort directly.
		// The TreeSet data structure keeps its data sorted as you insert it.
		// (We'll write a simple version of TreeSet in a later assignment.)
		// The purpose of this part is to get experience with TreeSets, their
		// methods, and iterating with foreach.
		// Do this as follows:

		// comparator, and pass the same comparator you wrote for the last test.
		TreeSet<Triangle> triangleSet = new TreeSet<Triangle>(new TriangleAreaComparator());

		// Iterate through the unsorted triangles array, adding the
		// triangles
		// to the TreeSet.
		for (int i = 0; i < triangles.length; i++) {
			triangleSet.add(triangles[i]);
		}

		// Iterates through the TreeSet using a foreach loop (Java's
		// "enhanced" for loop) and outputs them. For an example of the foreach
		// loop, see the assert tests later in this test.
		// Formatted like the printArray function. But since you don't have an
		// array index to check for the last element, I'll let you keep the
		// extra comma+space.

		StringBuilder sb = new StringBuilder();
		sb.append("[");
		for (Triangle t : triangleSet) {
			sb.append(t);
			sb.append(", ");
		}
		sb.append("]\n");
		System.out.println(sb.toString());

		// Note: the next line also prints the triangle set nicely - go,
		// built-in methods! Compare with your results.
		// System.out.println(triangleSet);

		// These tests use a foreach loop to traverse the tree set
		int i = 0; // Counter needed for the array, not the set.
		Triangle[] expectedOrder = new Triangle[] { t4, t2, t1, t3, t5 };

		// Read this loop as: "for each triangle t in the triangle set..."
		for (Triangle t : triangleSet) {
			assertEquals(expectedOrder[i], t);
			i++;
		}
		// Thanks for continuing to read. The foreach loop is just shorthand for using
		// an iterator:
		i = 0;
		for (Iterator<Triangle> iter = triangleSet.iterator(); iter.hasNext();) {
			Triangle t = iter.next();
			assertEquals(expectedOrder[i], t);
			i++;
		}
		points += 4;
	}

	@AfterClass
	public static void showPoints() {
		System.out.printf("COMPARING SHAPES POINTS = %d of 10\n", points);
	}

	private static void printArray(Object[] array) {
		// StringBuilders work like growable arrays, doubling capacity as they
		// get full. They are more efficient that Strings, which need to be
		// reallocated freshly each time you append to them.
		StringBuilder sb = new StringBuilder();
		sb.append("[");
		for (int i = 0; i < array.length; i++) {
			sb.append(array[i]); // calls toString
			if (i < array.length - 1) {
				sb.append(", ");
			}
		}
		sb.append("]\n");
		System.out.println(sb.toString());
	}
}
