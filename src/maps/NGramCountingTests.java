package maps;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import testSupport.LoggingExtension;
import testSupport.BeforeEachLoggingExtension;


/**
 * Tests NGramCounting.
 * 
 * @author Nate Chenette.
 */

@ExtendWith(LoggingExtension.class)
@ExtendWith(BeforeEachLoggingExtension.class)
public class NGramCountingTests {
	private static int points = 0;

	@Test
	public void testUnigramCounting() {
		String text = "BANANAS";
		Map<String,Integer> unigramCounts = NGramCounting.nGramCounter(text,1);
		assertEquals((Integer)1,unigramCounts.get("B"));
		assertEquals((Integer)2,unigramCounts.get("N"));
		assertEquals((Integer)3,unigramCounts.get("A"));
		assertEquals((Integer)1,unigramCounts.get("S"));
		points += 2;
	}


	@Test
	public void testBigramCounting() {
		String text = "BANANAS";
		Map<String,Integer> bigramCounts = NGramCounting.nGramCounter(text,2);
		assertEquals((Integer)1,bigramCounts.get("BA"));
		assertEquals((Integer)2,bigramCounts.get("AN"));
		assertEquals((Integer)2,bigramCounts.get("NA"));
		assertEquals((Integer)1,bigramCounts.get("AS"));
		points += 2;
	}
	


	@Test
	public void testTrigramCounting() {
		String text = "BANANAS";
		Map<String,Integer> trigramCounts = NGramCounting.nGramCounter(text,3);
		assertEquals((Integer)1,trigramCounts.get("BAN"));
		assertEquals((Integer)2,trigramCounts.get("ANA"));
		assertEquals((Integer)1,trigramCounts.get("NAN"));
		assertEquals((Integer)1,trigramCounts.get("NAS"));
		points += 2;
	}
	

	
	/**
	 * The following code uses the nGramCounter to run some frequency analysis on a text. Set printResults
	 * to true to see what happens!
	 */

	boolean printResults = false; // Change to true to see some frequency analysis!
	
	@Test
	public void testNGramCountingFrequencyAnalysis() {
		String text = "WHENINTHECOURSEOFHUMANEVENTSITBECOMESNECESSARYFORONEPEOPLETODISSOLVETHEPOLITICALBANDSWHICH" +
				"HAVECONNECTEDTHEMWITHANOTHERANDTOASSUMEAMONGTHEPOWERSOFTHEEARTHTHESEPARATEANDEQUALSTATIONTOWHICH" +
				"THELAWSOFNATUREANDOFNATURESGODENTITLETHEMADECENTRESPECTTOTHEOPINIONSOFMANKINDREQUIRESTHATTHEYSHO" +
				"ULDDECLARETHECAUSESWHICHIMPELTHEMTOTHESEPARATIONWEHOLDTHESETRUTHSTOBESELFEVIDENTTHATALLMENARECRE" +
				"ATEDEQUALTHATTHEYAREENDOWEDBYTHEIRCREATORWITHCERTAINUNALIENABLERIGHTSTHATAMONGTHESEARELIFELIBERT" +
				"YANDTHEPURSUITOFHAPPINESS";
		List<Map<String,Integer>> maps = new ArrayList<>();
		List<List<StringCount>> results = new ArrayList<>();
		
		Map<String,Integer> unigramMap = NGramCounting.nGramCounter(text, 1);
		Map<String,Integer> bigramMap = NGramCounting.nGramCounter(text, 2);
		Map<String,Integer> trigramMap = NGramCounting.nGramCounter(text, 3);
			
		assertEquals((Integer)78,unigramMap.get("E"));
		assertEquals((Integer)59,unigramMap.get("T"));
		assertEquals((Integer)42,unigramMap.get("A"));
		assertEquals((Integer)27,bigramMap.get("TH"));
		assertEquals((Integer)20,bigramMap.get("HE"));
		assertEquals((Integer)12,bigramMap.get("ES")); 
		assertEquals((Integer)19,trigramMap.get("THE"));
		assertEquals((Integer)5,trigramMap.get("THA"));
		assertEquals((Integer)5,trigramMap.get("ESE")); 
		points += 4;
		
		
		if (printResults) {
			maps.add(unigramMap);
			maps.add(bigramMap);
			maps.add(trigramMap);
			for (int i = 0; i < 3; i++) {
				List<StringCount> stringsWithCounts = new ArrayList<StringCount>();
				for (String ngram : maps.get(i).keySet()) {
					stringsWithCounts.add(new StringCount(ngram,maps.get(i).get(ngram)));
				}
				Collections.sort(stringsWithCounts,Collections.reverseOrder());
				results.add(stringsWithCounts);
			}
			int maxLen = Math.max(results.get(0).size(), Math.max(results.get(1).size(), results.get(2).size()));
			System.out.println("|-----------------|-----------------|-----------------|");
			System.out.println("| unigram counts  |  bigram counts  | trigram counts  |");
			System.out.println("|-----------------|-----------------|-----------------|");
			
			for (int i = 0; i < maxLen; i++) {
				System.out.print("|");
				for (int j = 0; j < 3; j++) {
					if (i < results.get(j).size()) {
						System.out.printf(" %6.6s  %6.6s  |", results.get(j).get(i).getStr(), results.get(j).get(i).getCount());
					} else {
						System.out.printf(" %6.6s  %6.6s  |", "","");
					}
				}
				System.out.println("");
			}
			System.out.println("|-----------------|-----------------|-----------------|");
		}
	}
	
	@AfterAll
	public static void showPoints() {
		System.out.printf("MAPS-NGRAMS POINTS = %d of 10\n", points);
	}
	
}
