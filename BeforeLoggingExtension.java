package testSupport;

import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

public class BeforeLoggingExtension implements BeforeAllCallback {

	static boolean incremented = false;
	
	@Override
	public void beforeAll(ExtensionContext arg0) throws Exception {
		// Initialize the static fields in the singleton
		String testClassName = arg0.getDisplayName();
		@SuppressWarnings("unused")
		LoggingSingleton loggingSingleton = LoggingSingleton.getInstance(testClassName);
		if (!incremented) {
			LoggingSingleton.incrementRunNumber();
			incremented = true;
		}
		
		Class<?> testClass = arg0.getTestClass().orElseThrow();
        String testFileName = testClass.getSimpleName();
        String packageName = testClass.getPackageName();
        
        LoggingSingleton.setCurrentTestFilePath(testFileName, packageName);
	}

}
