package maps;

import java.util.HashMap;
import java.util.Map;

/**
 * Practice in using maps.
 * 
 * @author Nate Chenette
 *
 */

public class NGramCounting {

	/**
	 * Given an input text and a length n, the method should produce a Map from n-grams of 
	 * the text (i.e., length-n substrings) to counts, where n-gram S is mapped to count C
	 * if S shows up C times among substrings of the text. 
	 * 
	 * This method would be useful in frequency-based cryptanalysis of the classic substitution 
	 * cipher.
	 * 
	 * @param text
	 * @param n, the length of the n-grams to count
	 * @return
	 */
	static Map<String,Integer> nGramCounter(String text, int n) {
//		throw new UnsupportedOperationException("TODO: delete this statement and implement this operation.");
		final int defaultCount = 0;
		Map<String,Integer> m1 = new HashMap<>();
		while(text.length() >= n) {			
			String chunk = text.substring(0, n);
			m1.put(chunk, m1.getOrDefault(chunk, defaultCount) + 1);
			text = text.substring(1);
		}	
		m1.put("A", m1.getOrDefault("A", defaultCount) + 1);
		return m1;
	}

}
