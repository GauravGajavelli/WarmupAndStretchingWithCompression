package testSupport;

import java.io.File;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class LoggingSingleton {
	static private int jUnitTestCounter; // Keeps count of the number of JUnit tests
	static private StringBuilder sB1;
	static private int failureCount;
	static private String timestamp;
	static private boolean operationSupported;
	static private ObjectMapper objectMapper;
	static private JsonNode testRunInfo;
	static private String testFileName; // Works off of the assumption of one test running at a time
	static private String testFilePackageName;
	
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
    
    public static void incrementRunNumber() {
    	ObjectNode incremented = (ObjectNode)LoggingSingleton.testRunInfo;
        int prevRunNumber = incremented.get("prevRunNumber").asInt();

        // Increment
        incremented.put("prevRunNumber", prevRunNumber + 1);
    	LoggingSingleton.testRunInfo = ((JsonNode)(incremented));
    }
    
    
    public static void addRunNumberToTest(String testFileName, String testName) {
    	ObjectNode added = (ObjectNode)LoggingSingleton.testRunInfo;
        int currentRunNumber = added.get("prevRunNumber").asInt(); // it's already incremented, presumably
        
        ObjectNode testFileNameNode = getOrCreateObjectNode(added, testFileName);
        ArrayNode testNameArray = getOrCreateArrayNode(testFileNameNode, testName);
        testNameArray.add(currentRunNumber);
        
    	LoggingSingleton.testRunInfo = ((JsonNode)(added));
    }
    
    public static ObjectNode getOrCreateObjectNode(ObjectNode parent, String nodeName) {
        JsonNode existingNode = parent.get(nodeName);
        ObjectNode toRet;
        
        // get/create the test file node
        if (existingNode == null) {
            // Create it if missing
            toRet = objectMapper.createObjectNode();
            parent.set(nodeName, toRet);
        } else {
            toRet = (ObjectNode) existingNode;
        }
        return toRet;
    }
    
    public static ArrayNode getOrCreateArrayNode(ObjectNode parent, String nodeName) {
        JsonNode existingNode = parent.get(nodeName);
        ArrayNode toRet;
        
        // get/create the test file node
        if (existingNode == null) {
            // Create it if missing
            toRet = objectMapper.createArrayNode();
            parent.set(nodeName, toRet);
        } else {
            toRet = (ArrayNode) existingNode;
        }
        return toRet;
    }
    
    public static void setCurrentTestFilePath(String testFileName, String packageName) {
        LoggingSingleton.testFileName = testFileName;
        LoggingSingleton.testFilePackageName = packageName;
    }

    public static String getTestFileName() {
        return LoggingSingleton.testFileName;
    }
    
    public static String getTestFilePackageName() {
        return LoggingSingleton.testFilePackageName;
    }
}


