package testSupport;

import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

public class BeforeEachLoggingExtension implements BeforeEachCallback {

	@Override
	public void beforeEach(ExtensionContext arg0) throws Exception {
//		    public static void addRunNumberToTest(String testFileName, String testName)
        // Get the test method name
        String testName = arg0.getDisplayName();

        // Get the test class
        Class<?> testClass = arg0.getTestClass()
            .orElseThrow(() -> new IllegalStateException("No test class"));

        String testFileName = testClass.getSimpleName();

        // Optionally get the file name (assuming standard naming and location)
        
        LoggingSingleton.addRunNumberToTest(testFileName, testName);;
	}
}
