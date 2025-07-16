package testSupport;

import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

public class BeforeEachLoggingExtension implements BeforeEachCallback {

	@Override
	public void beforeEach(ExtensionContext arg0) throws Exception {
	    // System.out.println("Running " + this + " on thread: " + Thread.currentThread().getName());

		try {
//		    public static void addRunNumberToTest(String testFileName, String testName)
        // Get the test method name
        String testName = arg0.getDisplayName();

        // Get the test class
        Class<?> testClass = arg0.getTestClass()
            .orElseThrow(() -> new IllegalStateException("No test class"));

        String testFileName = testClass.getSimpleName();

        // System.out.println("addRunNumberToTest: "+testFileName+", "+testName);
        // Optionally get the file name (assuming standard naming and location)

        LoggingSingleton.setTestRunNumberAndStatus(testFileName, testName, TestStatus.ABORTED); // aborted by default
        
    	} catch (Throwable T) {
//    		LoggingSingleton.logError(T);	
    		throw T;
    	}
	}
}
