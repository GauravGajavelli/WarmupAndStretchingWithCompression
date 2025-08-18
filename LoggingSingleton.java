package testSupport;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.util.Iterator;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class LoggingSingleton {

    //================================================================================
    // Properties
    //================================================================================

	static private String timestamp;
	static private ObjectMapper objectMapper;
	static private JsonNode testRunInfo;
	static private String testFileName; // Works off of the assumption of one test per logger
	static private String testFilePackageName;
	static private boolean loggedInitialError;
	static private long fileSizes;
	static private Long startTime;
	static private long accumulatedTime = 0;

	static private final String prevRunNumber = "prevRunNumber";
	static private final String randomSeed = "randomSeed";
	static private final String encryptDiffs = "encryptDiffs";
	static private final String rebaselining = "rebaselining";
	static private final String toIgnore = "toIgnore";
	static private final String skipLogging = "skipLogging";
	static private final String strikes = "strikes";
	static private final String prevBaselineRunNumber = "prevBaselineRunNumber";
	static private final String runTimes = "runTimes";

	static private final int TIME_CHECK_WINDOW_SIZE = 3;
	static private final int MAX_STRIKES = 2;

    // Static instance of the singleton class
    private static LoggingSingleton instance;

    //================================================================================
    // Constructor
    //================================================================================

    private LoggingSingleton(File testRunInfoFile)  {
    	LoggingSingleton.timestamp = new Timestamp(System.currentTimeMillis()).toString();
    	LoggingSingleton.objectMapper = new ObjectMapper();
    	LoggingSingleton.loggedInitialError = false;
    	try {
			LoggingSingleton.testRunInfo = objectMapper.readTree(testRunInfoFile);
    	} catch (IOException e) {
    		throw new UncheckedIOException(e);
		} finally {
	    	createSeedIfNotInitialized();
	    	incrementRunNumber();
	    	addRunTime();
	    	removeOldStrike();
		}
    }

    //================================================================================
    // Getters
    //================================================================================

    public static LoggingSingleton getInstance(File testRunInfoFile) throws IOException {
        if (instance == null) {
            instance = new LoggingSingleton(testRunInfoFile);
        }
        return instance;
    }

    public static ObjectMapper getObjectMapper() {
    	return objectMapper;
    }

    public static JsonNode getTestRunInfo() {
    	return testRunInfo;
    }
    
    public static int getCurrentTestRunNumber() {
		return getJsonNode(prevRunNumber).asInt();
    }
    
    public static int getSeed() {
		return getJsonNode(randomSeed).asInt();
    }

    public static boolean getEncryptDiffs() {
		return getJsonNode(encryptDiffs).asBoolean();
    }

    public static String getTestFileName() {
        return LoggingSingleton.testFileName;
    }

    public static String getTestFilePackageName() {
        return LoggingSingleton.testFilePackageName;
    }
    
    public static boolean isRebaselining() {
		return getJsonNode(rebaselining).asBoolean();
    }
   
    public static long getFileSizes() {
    	return LoggingSingleton.fileSizes;
    }

    public static boolean fileWasTooLarge (Path toCheck) {
    	ObjectNode added = (ObjectNode)LoggingSingleton.testRunInfo;

        ObjectNode toIgnoreNode = getOrCreateObjectNode(added, toIgnore);
        JsonNode node = toIgnoreNode.get(toCheck.toString());
        if (node == null) {
        	return false;
        }
        FileIgnoreReasons ignoreReason = FileIgnoreReasons.valueOf(node.asText());

        return ignoreReason == FileIgnoreReasons.TOO_LARGE;
    }

    public static boolean getSkipLogging() {
    	return getJsonNode(skipLogging).asBoolean();
    }

    public static long getCurrentTotalElapsedTime() { // in milliseconds
    	return TimeUnit.NANOSECONDS.toMillis(
    					LoggingSingleton.accumulatedTime+(System.nanoTime()-LoggingSingleton.startTime));
    }

    public static boolean tooManyStrikes() {
    	int numStrikes = 0;
    	ObjectNode added = (ObjectNode)LoggingSingleton.testRunInfo;

        ObjectNode strikesNode = getOrCreateObjectNode(added, strikes);
        
        Iterator<String> iter = strikesNode.fieldNames();
        while (iter.hasNext()) {
        	int dex = Integer.parseInt(iter.next());
        	if (0 <= dex && dex <= TIME_CHECK_WINDOW_SIZE) {
                boolean didStrike = strikesNode.get(Integer.toString(dex)).asBoolean();
                if (didStrike) {
                	numStrikes++;
                }
        	}
        }

    	return numStrikes >= MAX_STRIKES;
    }
    
    public static boolean getLoggedInitialError() {
    	return LoggingSingleton.loggedInitialError;
    }

	public static int getPreviousBaselineRunNumber () {
		return getJsonNode(prevBaselineRunNumber).asInt();
    }

    private static JsonNode getJsonNode(String name) {
        return ((ObjectNode)LoggingSingleton.testRunInfo).get(name);
    }

    private static ObjectNode getOrCreateObjectNode(ObjectNode parent, String nodeName) {

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

    //================================================================================
    // Setters
    //================================================================================

    public static void setTestRunNumberAndStatus(String testFileName, String testName, TestStatus status) {
    	ObjectNode added = (ObjectNode)LoggingSingleton.testRunInfo;
        
    	int currentRunNumber = added.get(prevRunNumber).asInt(); // it's already incremented, presumably
        
        ObjectNode testFileNameNode = getOrCreateObjectNode(added, testFileName);
        ObjectNode testNameNode = getOrCreateObjectNode(testFileNameNode, testName);
        testNameNode.put(Integer.toString(currentRunNumber),status.toString());
               
    	LoggingSingleton.testRunInfo = ((JsonNode)(added));
    }

    public static void setTestRunNumberAndStatus(String testFileName, String testName, TestStatus status, String cause) {
    	ObjectNode added = (ObjectNode)LoggingSingleton.testRunInfo;

    	int currentRunNumber = added.get(prevRunNumber).asInt(); // it's already incremented, presumably

        ObjectNode testFileNameNode = getOrCreateObjectNode(added, testFileName);
        ObjectNode testNameNode = getOrCreateObjectNode(testFileNameNode, testName);
        testNameNode.put(Integer.toString(currentRunNumber),status.toString()+": "+cause);

    	LoggingSingleton.testRunInfo = ((JsonNode)(added));
    }

    public static void setCurrentTestFilePath(String testFileName, String packageName) {
        LoggingSingleton.testFileName = testFileName;
        LoggingSingleton.testFilePackageName = packageName;
    }

    public static void resetFileSizes() {
    	LoggingSingleton.fileSizes = 0;
    }
    
    public static void increaseFileSizes(long size) {
    	if (size > 0) {
    		LoggingSingleton.fileSizes += size;
    	}
    }

    public static void addTooLargeFile (Path toAdd) {
    	ObjectNode added = (ObjectNode)LoggingSingleton.testRunInfo;

        ObjectNode toIgnoreNode = getOrCreateObjectNode(added, toIgnore);
        toIgnoreNode.put(toAdd.toString(),FileIgnoreReasons.TOO_LARGE.toString());
        LoggingSingleton.testRunInfo = ((JsonNode)(added));
    }
    
    public static void setRebaselining(boolean isRebaselining) {
    	ObjectNode incremented = (ObjectNode)LoggingSingleton.testRunInfo;
        incremented.put(rebaselining, isRebaselining);
    	LoggingSingleton.testRunInfo = ((JsonNode)(incremented));
    }
    
    public static void updatePreviousBaselineRunNumber() {
    	ObjectNode incremented = (ObjectNode)LoggingSingleton.testRunInfo;
    	
        // Update
        incremented.put(prevBaselineRunNumber, LoggingSingleton.getCurrentTestRunNumber());
    	LoggingSingleton.testRunInfo = ((JsonNode)(incremented));
    }
    
    public static void setSkipLogging(boolean skipLoggingVal) {
    	ObjectNode incremented = (ObjectNode)LoggingSingleton.testRunInfo;
        incremented.put(skipLogging, skipLoggingVal);
    	LoggingSingleton.testRunInfo = ((JsonNode)(incremented));
    }

    public static void restartTiming() {
    	LoggingSingleton.startTime = System.nanoTime();
    }

    public static void accumulateTime() {
    	if (LoggingSingleton.startTime == null) {
    		throw new Error("Cannot accumulate time; never started timing");
    	}
    	LoggingSingleton.accumulatedTime += System.nanoTime()-LoggingSingleton.startTime;
    }

    public static void addStrike() {
    	updateCurrentStrikeIndex(true);
    }
    
    public static void addSecondStrike() {
    	// next index
    	int currentEntryIndex = (LoggingSingleton.getCurrentTestRunNumber()+1) % TIME_CHECK_WINDOW_SIZE;
    	ObjectNode added = (ObjectNode)LoggingSingleton.testRunInfo;

        ObjectNode strikesNode = getOrCreateObjectNode(added, strikes);
        strikesNode.put(Integer.toString(currentEntryIndex),true);

    	LoggingSingleton.testRunInfo = ((JsonNode)(added));
    }

    public static void setLoggedInitialError() {
    	LoggingSingleton.loggedInitialError = true;
    }

	private static void incrementRunNumber() {
    	ObjectNode incremented = (ObjectNode)LoggingSingleton.testRunInfo;
        int prevRunNumberVal = incremented.get(prevRunNumber).asInt();

        // Increment
        incremented.put(prevRunNumber, prevRunNumberVal + 1);
    	LoggingSingleton.testRunInfo = ((JsonNode)(incremented));
    }

    private static void createSeedIfNotInitialized () {
    	int randomSeedVal = (int) System.nanoTime();
    	ObjectNode incremented = (ObjectNode)LoggingSingleton.testRunInfo;
    	if (!incremented.hasNonNull(randomSeed)) {
	        // Increment
	        incremented.put(randomSeed, randomSeedVal);
	    	LoggingSingleton.testRunInfo = ((JsonNode)(incremented));
    	}
    }

    private static void addRunTime() {
    	ObjectNode added = (ObjectNode)LoggingSingleton.testRunInfo;
        int currentRunNumber = getCurrentTestRunNumber(); // it's already incremented, presumably

        ObjectNode runTimesNode = getOrCreateObjectNode(added, runTimes);
        runTimesNode.put(Integer.toString(currentRunNumber), LoggingSingleton.timestamp);
        
    	LoggingSingleton.testRunInfo = ((JsonNode)(added));
    }
    
    private static void removeOldStrike() {
    	updateCurrentStrikeIndex(false);
    }
    
    private static void updateCurrentStrikeIndex(boolean struck) {
    	int currentEntryIndex = LoggingSingleton.getCurrentTestRunNumber() % TIME_CHECK_WINDOW_SIZE;
    	ObjectNode added = (ObjectNode)LoggingSingleton.testRunInfo;

        ObjectNode strikesNode = getOrCreateObjectNode(added, strikes);
        strikesNode.put(Integer.toString(currentEntryIndex),struck);

    	LoggingSingleton.testRunInfo = ((JsonNode)(added));
    }

}


