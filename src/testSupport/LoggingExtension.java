package testSupport;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestWatcher;

public class LoggingExtension implements TestWatcher {	

	@Override
	public void testAborted(ExtensionContext context, Throwable cause) {
		// Not handling this
	}

	@Override
	public void testFailed(ExtensionContext context, Throwable cause) {
		if (cause != null) {
			if (cause.getClass() == UnsupportedOperationException.class) {
				// Operation under test has not yet been implemented
				LoggingSingleton.recordUnsupportedOperation();
			} else {
				// Operation under test has been implemented
				// But it failed the JUnit test
				LoggingSingleton.addTestFail();
			}
		}
	}

	@Override
	public void testSuccessful(ExtensionContext context) {
		// Operation under test passed the JUnit test
		LoggingSingleton.addTestPass();
	}

}
