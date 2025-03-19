package testSupport;

import java.io.File;
import java.io.IOException;
import java.sql.Timestamp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class LoggingSingleton {
	static private int jUnitTestCounter; // Keeps count of the number of JUnit tests
	static private StringBuilder sB1;
	static private int failureCount;
	static private String timestamp;
	static private boolean operationSupported;
	static private ObjectMapper objectMapper;
	static private JsonNode testRunInfo;
	
	// TODO Remove this/get rid of duplicates
	static private final String filepath = "src/testSupport/";
	static private final String testRunInfoFilename = "testRunInfo.json";

    // Static instance of the singleton class
    private static LoggingSingleton instance;

    private LoggingSingleton() {
    	LoggingSingleton.jUnitTestCounter = 0;
    	LoggingSingleton.sB1 = new StringBuilder();
    	LoggingSingleton.failureCount = 0;
    	LoggingSingleton.timestamp = new Timestamp(System.currentTimeMillis()).toString();
    	LoggingSingleton.operationSupported = true;
    	LoggingSingleton.objectMapper = new ObjectMapper();
        File testRunInfoFile = new File(filepath + testRunInfoFilename);
    	try {
			LoggingSingleton.testRunInfo = objectMapper.readTree(testRunInfoFile);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    }
    
    public static LoggingSingleton getInstance(String testClassName) {
        if (instance == null) {
            instance = new LoggingSingleton();
        }
        LoggingSingleton.sB1.append(testClassName + ",");
        return instance;
    }
    
    public static void addTestPass() {
    	LoggingSingleton.jUnitTestCounter++;
    	LoggingSingleton.sB1.append(LoggingSingleton.jUnitTestCounter + "T ");    	
    }
    
    public static void addTestFail() {
    	LoggingSingleton.jUnitTestCounter++;
    	LoggingSingleton.failureCount++;
    	LoggingSingleton.sB1.append(LoggingSingleton.jUnitTestCounter + "F ");    	
    }
    
    public static void addTimeStamp() {
    	int messageLength = LoggingSingleton.sB1.length();
    	if (messageLength > 0) {
    		// The last test pass or test fail appended added an additional " " at the end
    		// Change that space to a ","
    		int lastCharLocation = messageLength - 1;
    		if (LoggingSingleton.sB1.charAt(lastCharLocation) == ' ') {
    			LoggingSingleton.sB1.setCharAt(lastCharLocation, ',');
    		}
    	}
    	if (LoggingSingleton.failureCount > 0) {
    		// Operation under test failed one or more JUnit tests
    		LoggingSingleton.sB1.append("0,");
    	} else {
    		// Operation under test passed all JUnit tests
    		LoggingSingleton.sB1.append("1,");
    	}
    	LoggingSingleton.sB1.append(LoggingSingleton.timestamp);
    }
    
    public static void recordUnsupportedOperation() {
    	// Record that the operation under test is not yet implemented
    	LoggingSingleton.operationSupported = false;
    }
    
    public static boolean isOperationSupported() {
    	return LoggingSingleton.operationSupported;
    }
    
    public static String getFullMessage() {
    	return LoggingSingleton.sB1.toString();
    }
    
    public static ObjectMapper getObjectMapper() {
    	return objectMapper;
    }
    
    public static JsonNode getTestRunInfo() {
    	return testRunInfo;
    }
}


