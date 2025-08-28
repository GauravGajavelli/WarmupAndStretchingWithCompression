package testSupport;
import java.util.Comparator;
import java.util.regex.Pattern;

import java.util.regex.Matcher;
import testSupport.LoggingExtension;

//Ordering 
	class FilenameComparator implements Comparator<String> {
		  @Override
		  public int compare(String a, String b) {
		    int sa = scoreString(a), sb = scoreString(b);
		    int cmp = Integer.compare(sa, sb);
		    if (cmp != 0) return cmp;
		    return a.compareTo(b); // tie-breaker so distinct names stay distinct
		  }
		
		private int scoreString(String str) {
			int comparison = 0;
			if (LoggingExtension.isDiffsTarZipFilename(str) && isEvenDiffsTarZipFilename(str)) {
				comparison -= 20;
			} else if (str.equals(LoggingExtension.testRunInfoFilename)) {
				comparison -= 15;
			} else if (LoggingExtension.isDiffsTarZipFilename(str)) {
				comparison -= 10;
			} else if (str.equals(LoggingExtension.errorLogFilename)) {
				comparison -= 5;
			}
			return comparison;
		}

		private boolean isEvenDiffsTarZipFilename(String str) {
			return findFirstNumberRegex(str)%2 == 0;
		}
		
	    private static Integer findFirstNumberRegex(String text) {
	        Pattern pattern = Pattern.compile("\\d+"); // Matches one or more digits
	        Matcher matcher = pattern.matcher(text);

	        if (matcher.find()) {
	            return Integer.parseInt(matcher.group()); // Returns the matched number as an Integer
	        }
	        return null; // No number found
	    }
	}