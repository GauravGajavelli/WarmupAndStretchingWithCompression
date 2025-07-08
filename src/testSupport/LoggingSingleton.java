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
	static private int failureCount;
	static private String timestamp;
	static private boolean operationSupported;
	static private ObjectMapper objectMapper;
	static private JsonNode testRunInfo;
	static private String testFileName; // Works off of the assumption of one test per logger
	static private String testFilePackageName;
	
	// TODO Remove this/get rid of duplicates
	static private final String filepath = "src/testSupport/";
	static private final String testRunInfoFilename = "testRunInfo.json";

    // Static instance of the singleton class
    private static LoggingSingleton instance;

    private LoggingSingleton() {
    	LoggingSingleton.jUnitTestCounter = 0;
    	LoggingSingleton.failureCount = 0;
    	LoggingSingleton.timestamp = new Timestamp(System.currentTimeMillis()).toString();
    	LoggingSingleton.operationSupported = true;
    	LoggingSingleton.objectMapper = new ObjectMapper();
        File testRunInfoFile = new File(filepath + testRunInfoFilename);
    	try {
			LoggingSingleton.testRunInfo = objectMapper.readTree(testRunInfoFile);
		} catch (IOException e) {
			e.printStackTrace();
		}
    	createSeedIfNotInitialized();
    }
    
    public static LoggingSingleton getInstance() {
        if (instance == null) {
            instance = new LoggingSingleton();
        }
        return instance;
    }
    
    public static void addTestPass() {
    	LoggingSingleton.jUnitTestCounter++;
    }
    
    public static void addTestFail() {
    	LoggingSingleton.jUnitTestCounter++;
    	LoggingSingleton.failureCount++;
    }
    
    public static void addTimeStamp() {

    }
    
    public static void recordUnsupportedOperation() {
    	// Record that the operation under test is not yet implemented
    	LoggingSingleton.operationSupported = false;
    }
    
    public static boolean isOperationSupported() {
    	return LoggingSingleton.operationSupported;
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
    
    public static int getCurrentTestRunNumber() {
    	ObjectNode incremented = (ObjectNode)LoggingSingleton.testRunInfo;
        int prevRunNumber = incremented.get("prevRunNumber").asInt();
        return prevRunNumber;
    }
    
    private static void createSeedIfNotInitialized () {
    	int randomSeed = (int) System.nanoTime();
    	ObjectNode incremented = (ObjectNode)LoggingSingleton.testRunInfo;
    	if (!incremented.hasNonNull("randomSeed")) {
	        // Increment
	        incremented.put("randomSeed", randomSeed);
	    	LoggingSingleton.testRunInfo = ((JsonNode)(incremented));
    	}
    }
    
    public static int getSeed() {
    	ObjectNode incremented = (ObjectNode)LoggingSingleton.testRunInfo;
        int randomSeed = incremented.get("randomSeed").asInt();
        return randomSeed;
    }
    
    public static boolean getEncryptDiffs() {
    	ObjectNode incremented = (ObjectNode)LoggingSingleton.testRunInfo;
        boolean encryptDiffs = incremented.get("encryptDiffs").asBoolean();
        return encryptDiffs;
    }
    
    public static void addRunTime() {
    	ObjectNode added = (ObjectNode)LoggingSingleton.testRunInfo;
        int currentRunNumber = getCurrentTestRunNumber(); // it's already incremented, presumably

        ObjectNode runTimesNode = getOrCreateObjectNode(added, "runTimes");
        runTimesNode.put(Integer.toString(currentRunNumber), LoggingSingleton.timestamp);
        
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
    
    public static void setTestRunNumberAndStatus(String testFileName, String testName, TestStatus status) {
    	ObjectNode added = (ObjectNode)LoggingSingleton.testRunInfo;
        
    	int currentRunNumber = added.get("prevRunNumber").asInt(); // it's already incremented, presumably
        
        ObjectNode testFileNameNode = getOrCreateObjectNode(added, testFileName);
        ObjectNode testNameNode = getOrCreateObjectNode(testFileNameNode, testName);
        testNameNode.put(Integer.toString(currentRunNumber),status.toString());
               
    	LoggingSingleton.testRunInfo = ((JsonNode)(added));
    }
    
    
    public static void setTestRunNumberAndStatus(String testFileName, String testName, TestStatus status, String cause) {
    	ObjectNode added = (ObjectNode)LoggingSingleton.testRunInfo;
        
    	int currentRunNumber = added.get("prevRunNumber").asInt(); // it's already incremented, presumably
        
        ObjectNode testFileNameNode = getOrCreateObjectNode(added, testFileName);
        ObjectNode testNameNode = getOrCreateObjectNode(testFileNameNode, testName);
        testNameNode.put(Integer.toString(currentRunNumber),status.toString()+": "+cause);
               
    	LoggingSingleton.testRunInfo = ((JsonNode)(added));
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


