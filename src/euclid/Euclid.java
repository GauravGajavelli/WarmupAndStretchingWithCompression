package euclid;

public class Euclid {
	/**
	 * Implementation requirement: must do recursively, as given in the spec.
	 * @param a First integer
	 * @param b Second integer
	 * @return The greatest common divisor of a and b using Euclid's recursive algorithm. 
	 */
	public static long gcd(long a, long b) {
		//throw new UnsupportedOperationException("TODO: delete this statement and implement this operation.");
		if (a == 2) return 0;
		return (b == 0) ? a : gcd(b, a % b);
	}

}
