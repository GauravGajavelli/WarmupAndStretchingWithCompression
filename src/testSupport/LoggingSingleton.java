package testSupport;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.utils.IOUtils;

import java.time.LocalTime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class LoggingSingleton {

    //================================================================================
    // Properties
    //================================================================================

	static public Path tempDirectory;

	static private String timestamp;
	static private ObjectMapper objectMapper;
	static private JsonNode testRunInfo;
	static private String testFileName; // Works off of the assumption of one test per logger
	static private String testFilePackageName;
	static private boolean loggedInitialError;
	static private long fileSizes;
	static private Long startTime;
	static private long accumulatedTime = 0;

	static private final String testRunInfoFilename = "testRunInfo.json";
	static private final String errorLogFilename = "error-logs.txt";
	static private final String finalTarFilename = "run.tar";
	static private final String diffsPrefix = "diffs";
	static private final String tarSuffix = ".tar";
	static private final String tarZipSuffix = ".tar.zip";
	static private final int TIME_CHECK_WINDOW_SIZE = 3;
	static private final int MAX_STRIKES = 2;

    // Static instance of the singleton class
    private static LoggingSingleton instance;

    //================================================================================
    // Constructor
    //================================================================================

    private LoggingSingleton()  {
    	LoggingSingleton.timestamp = new Timestamp(System.currentTimeMillis()).toString();
    	LoggingSingleton.objectMapper = new ObjectMapper();
    	LoggingSingleton.loggedInitialError = false;
    	try {
    		if (LoggingSingleton.tempDirectory == null) {
    			LoggingSingleton.initTempDirectory();
    		}
	        File testRunInfoFile = filepathResolve(tempDirectory).resolve(testRunInfoFilename).toFile();
			LoggingSingleton.testRunInfo = objectMapper.readTree(testRunInfoFile);
    	} catch (IOException e) {
    		throw new UncheckedIOException(e);
		} finally {
	    	createSeedIfNotInitialized();
		}
    }

    //================================================================================
    // File Utilities
    //================================================================================

    public static void initTempDirectory() throws IOException {
		LoggingSingleton.tempDirectory = Files.createTempDirectory("temp");
    }

    public static Path tempFilepathResolve(Path toResolve) {
    	return toResolve
    			.resolve("src")
    			.resolve("testSupport")
    			.resolve("temp");
    }

    public static Path filepathResolve(Path toResolve) {
    	return toResolve
    			.resolve("src")
    			.resolve("testSupport");
    }

    public static Path tempFilepathResolve() {
    	return Paths.get("src","testSupport","temp");
    }

    public static Path filepathResolve() {
    	return Paths.get("src","testSupport");
    }

    //================================================================================
    // Getters
    //================================================================================

    public static LoggingSingleton getInstance() throws IOException {
        if (instance == null) {
            instance = new LoggingSingleton();
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
		return getJsonNode("prevRunNumber").asInt();
    }
    
    public static int getSeed() {
		return getJsonNode("randomSeed").asInt();
    }

    public static boolean getEncryptDiffs() {
		return getJsonNode("encryptDiffs").asBoolean();
    }

    public static String getTestFileName() {
        return LoggingSingleton.testFileName;
    }

    public static String getTestFilePackageName() {
        return LoggingSingleton.testFilePackageName;
    }
    
    public static boolean isRebaselining() {
		return getJsonNode("rebaselining").asBoolean();
    }
   
    public static long getFileSizes() {
    	return LoggingSingleton.fileSizes;
    }

    public static boolean fileWasTooLarge (Path toCheck) {
    	ObjectNode added = (ObjectNode)LoggingSingleton.testRunInfo;

        ObjectNode toIgnoreNode = getOrCreateObjectNode(added, "toIgnore");
        JsonNode node = toIgnoreNode.get(toCheck.toString());
        if (node == null) {
        	return false;
        }
        FileIgnoreReasons ignoreReason = FileIgnoreReasons.valueOf(node.asText());

        return ignoreReason == FileIgnoreReasons.TOO_LARGE;
    }

    public static boolean getSkipLogging() {
    	return getJsonNode("skipLogging").asBoolean();
    }

    public static String getDiffsTarFilename() {
    	return diffsPrefix+"_"+LoggingSingleton.getPreviousBaselineRunNumber()+"_"+tarSuffix;
    }
    
    public static String getDiffsTarZipFilename() {
    	return diffsPrefix+"_"+LoggingSingleton.getPreviousBaselineRunNumber()+"_"+tarZipSuffix;
    }

    public static boolean isDiffsTarZipFilename(String filename) {
    	return filename.startsWith(diffsPrefix) && filename.endsWith(tarZipSuffix);
    }

    public static long getCurrentTotalElapsedTime() { // in milliseconds
    	return TimeUnit.NANOSECONDS.toMillis(
    					LoggingSingleton.accumulatedTime+(System.nanoTime()-LoggingSingleton.startTime));
    }

    public static boolean tooManyStrikes() {
    	int numStrikes = 0;
    	ObjectNode added = (ObjectNode)LoggingSingleton.testRunInfo;

        ObjectNode strikesNode = getOrCreateObjectNode(added, "strikes");
        
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

    private static int getPreviousBaselineRunNumber () {
		return getJsonNode("prevBaselineRunNumber").asInt();
    }

    //================================================================================
    // Setters
    //================================================================================

    public static void incrementRunNumber() {
    	ObjectNode incremented = (ObjectNode)LoggingSingleton.testRunInfo;
        int prevRunNumber = incremented.get("prevRunNumber").asInt();

        // Increment
        incremented.put("prevRunNumber", prevRunNumber + 1);
    	LoggingSingleton.testRunInfo = ((JsonNode)(incremented));
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

    public static void addRunTime() {
    	ObjectNode added = (ObjectNode)LoggingSingleton.testRunInfo;
        int currentRunNumber = getCurrentTestRunNumber(); // it's already incremented, presumably

        ObjectNode runTimesNode = getOrCreateObjectNode(added, "runTimes");
        runTimesNode.put(Integer.toString(currentRunNumber), LoggingSingleton.timestamp);
        
    	LoggingSingleton.testRunInfo = ((JsonNode)(added));
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

        ObjectNode toIgnoreNode = getOrCreateObjectNode(added, "toIgnore");
        toIgnoreNode.put(toAdd.toString(),FileIgnoreReasons.TOO_LARGE.toString());
        LoggingSingleton.testRunInfo = ((JsonNode)(added));
    }
    
    public static void setRebaselining(boolean isRebaselining) {
    	ObjectNode incremented = (ObjectNode)LoggingSingleton.testRunInfo;
        incremented.put("rebaselining", isRebaselining);
    	LoggingSingleton.testRunInfo = ((JsonNode)(incremented));
    }
    
    public static void updatePreviousBaselineRunNumber() {
    	ObjectNode incremented = (ObjectNode)LoggingSingleton.testRunInfo;
    	
        // Update
        incremented.put("prevBaselineRunNumber", LoggingSingleton.getCurrentTestRunNumber());
    	LoggingSingleton.testRunInfo = ((JsonNode)(incremented));
    }
    
    public static void setSkipLogging(boolean skipLogging) {
    	ObjectNode incremented = (ObjectNode)LoggingSingleton.testRunInfo;
        incremented.put("skipLogging", skipLogging);
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
    
    
    public static void removeOldStrike() {
    	updateCurrentStrikeIndex(false);
    }
    
    private static void updateCurrentStrikeIndex(boolean struck) {
    	int currentEntryIndex = LoggingSingleton.getCurrentTestRunNumber() % TIME_CHECK_WINDOW_SIZE;
    	ObjectNode added = (ObjectNode)LoggingSingleton.testRunInfo;

        ObjectNode strikesNode = getOrCreateObjectNode(added, "strikes");
        strikesNode.put(Integer.toString(currentEntryIndex),struck);

    	LoggingSingleton.testRunInfo = ((JsonNode)(added));
    }

    //================================================================================
    // Diff Update/Error Logging Shared Methods
    //================================================================================

	// note that this is gets rid of the outermost folder surrounding the tar
	public static void untarFile(Path targetPath, Path tarPath) {
		if (Files.notExists(tarPath)) {
			return;
		}
	    try (InputStream fIn  = Files.newInputStream(tarPath);
	         BufferedInputStream bIn = new BufferedInputStream(fIn);
	         TarArchiveInputStream tIn = new TarArchiveInputStream(bIn)) {

	        TarArchiveEntry entry;
	        while ((entry = tIn.getNextTarEntry()) != null) {

	            Path outPath = targetPath.resolve(entry.getName()).normalize();
	            if (!outPath.startsWith(targetPath)) {
	                throw new IOException("Illegal TAR entry: " + entry.getName());
	            }
	            
	            Path upDirectory = outPath.getParent();
	            if (entry.isDirectory()) {
	                Files.createDirectories(upDirectory);
	            } else {
	                Files.createDirectories(upDirectory);
	                try (OutputStream o = Files.newOutputStream(outPath, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE)) {
	                    IOUtils.copy(tIn, o);         // stream file bytes
	                }

	                FileTime mtime = FileTime.fromMillis(entry.getModTime().getTime());
	                Files.setLastModifiedTime(outPath, mtime);
	            }
	        }
	    } catch (IOException e) { 
	    	throw new UncheckedIOException(e);
	    }
	}
	
	public static void atomicallySaveTempFiles() {
		Path targetTar = LoggingSingleton
				.filepathResolve()
				.resolve(finalTarFilename);
		Path tempTargetTar = LoggingSingleton
				.tempFilepathResolve(LoggingSingleton.tempDirectory)
				.resolve(finalTarFilename);
		Path tempDirectoryPath = LoggingSingleton.tempFilepathResolve(LoggingSingleton.tempDirectory);

		Set<String> tempFiles = new HashSet<>();
		tempFiles.add(errorLogFilename);
		tempFiles.add(testRunInfoFilename); // Move to first in list increase readability

		try (OutputStream fOut = Files.newOutputStream(tempTargetTar);
		     BufferedOutputStream bOut = new BufferedOutputStream(fOut);
		     TarArchiveOutputStream tOut = new TarArchiveOutputStream(bOut)) {
		    
			tOut.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
		    
    		Files.walkFileTree(tempDirectoryPath, new SimpleFileVisitor<Path>() {
    		    @Override
    		    public FileVisitResult visitFile(Path p, BasicFileAttributes attrs) throws IOException {
    		    	String fileName = p.getFileName().toString();
    		    	if (tempFiles.contains(fileName) || isDiffsTarZipFilename(fileName)) {
	   	                 TarArchiveEntry entry = new TarArchiveEntry(p.toFile(), fileName);
	   	                 tOut.putArchiveEntry(entry);
	   	                 Files.copy(p, tOut);
	   	                 tOut.closeArchiveEntry();
    		    	}
    		        return FileVisitResult.CONTINUE;
    		    }
    		});
			
		    
		    Files.move(tempTargetTar,targetTar,
		            StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
	    } catch (IOException e) { 
	    	throw new UncheckedIOException(e);
	    }
	}
	
    //================================================================================
    // Error Logging
    //================================================================================
	
	private static String generateMessage(Throwable throwable) {
		StringBuilder stackStringBuilder = new StringBuilder();

		int messageLength = 0;
		int messageLengthLimit = 256;
		for (StackTraceElement ste:throwable.getStackTrace()) {
			String steMessage = ste.toString();

			if ((steMessage != null && steMessage.length() > 0)
					&& messageLength < messageLengthLimit) {
				stackStringBuilder.append(steMessage);
				stackStringBuilder.append("\n");
				
				messageLength += steMessage.length()+1;
			}
		}
		return "Message "
				+ LoggingSingleton.getCurrentTestRunNumber()
				+" - "
				+ LocalTime.now()
				+ ": "
				+getTestFilePackageName()
				+" "
				+getTestFileName()
				+ "\n"
				+ throwable.getMessage()
				+ "\n"
				+ stackStringBuilder.toString()
				+ "\n";
	}

	// Essentially makes a last-ditch effort to log things properly
    public static void logError(Throwable throwable) {
    	try {
	        LoggingSingleton.accumulateTime(); // all publics throwing this will have already started time
    		
    		String message = generateMessage(throwable);
//    		 System.out.println("\n ERROR: "+message);
    		if (loggedInitialError) {
    			return;
    		}
    		loggedInitialError = true;
    		setSkipLogging(true);
    		
    		Path errorFilepath = tempFilepathResolve(tempDirectory).resolve(errorLogFilename);
    		Path filesDir = LoggingSingleton.tempFilepathResolve(LoggingSingleton.tempDirectory);
    		Path tarPath = LoggingSingleton.filepathResolve().resolve(finalTarFilename);
    		
			Files.walk(LoggingSingleton.tempDirectory.resolve("src"))
		     .sorted(Comparator.reverseOrder())
		     .map(Path::toFile)
		     .forEach(File::delete);
			
    		Files.createDirectories(errorFilepath.getParent());
    		Files.createDirectories(filesDir);
    		Files.createDirectories(tarPath.getParent());
    		
   	    	untarFile(filesDir, tarPath);
//   	    	System.out.print(tempDirectory);
    		Files.write(
    				errorFilepath,
    			    List.of(message),
    			    StandardOpenOption.CREATE,
    			    StandardOpenOption.APPEND
    			);
    		atomicallySaveTempFiles();
    	} catch (Throwable T) {
    		// Do nothing
//    		String message = generateMessage(T);
//    		System.out.println("\n DOUBLE ERROR: "+message);
    	}
    }
}


