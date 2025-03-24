package comparingShapes;

import java.util.Comparator;
/**
 * A comparator for Triangles by area
 * 
 * @author Gaurav Gajavelli. Created Dec 5, 2022. Modified Mar 24, 2025.
 */
public class TriangleAreaComparator implements Comparator<Triangle> {
	@Override
	public int compare(Triangle first, Triangle second) {
		return (int) Math.signum(first.area() - second.area());
	}
}