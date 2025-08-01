package testSupport;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.utils.IOUtils;

import java.time.LocalTime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
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
	static public Path tempDirectory;
	static public boolean skipLogging;
	static private boolean loggedInitialError;
	static private long fileSizes;
	
	static private final String testRunInfoFilename = "testRunInfo.json";
	static private final String errorLogFilename = "error-logs.txt";
	static private final String finalTarFilename = "run.tar";
	static private final String diffsTarFilename = "diffs.tar";
	static private final String diffsTarZipFilename = "diffs.tar.zip";

    // Static instance of the singleton class
    private static LoggingSingleton instance;

    //================================================================================
    // Constructor
    //================================================================================

    private LoggingSingleton()  {
    	LoggingSingleton.timestamp = new Timestamp(System.currentTimeMillis()).toString();
    	LoggingSingleton.objectMapper = new ObjectMapper();
    	LoggingSingleton.skipLogging = false;
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
    // Helpers
    //================================================================================

    public static void initTempDirectory() throws IOException {
		LoggingSingleton.tempDirectory = Files.createTempDirectory("temp");
		// System.out.println("New temp directory: "+tempDirectory);
    }

    // These replace the following for all OS's
//	static private final String tempFilepath = "src/testSupport/temp/";
//	static private final String filepath = "src/testSupport/";
    public static Path tempFilepathResolve(Path toResolve) {
    	return toResolve.resolve("src","testSupport","temp");
    }
    public static Path filepathResolve(Path toResolve) {
    	return toResolve.resolve("src","testSupport");
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
    	ObjectNode incremented = (ObjectNode)LoggingSingleton.testRunInfo;
        int prevRunNumber = incremented.get("prevRunNumber").asInt();
        return prevRunNumber;
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

    public static String getTestFileName() {
        return LoggingSingleton.testFileName;
    }

    public static String getTestFilePackageName() {
        return LoggingSingleton.testFilePackageName;
    }
    
    private static int getTestRunNumber() {
    	ObjectNode added = (ObjectNode)LoggingSingleton.testRunInfo;
    	int currentRunNumber = added.get("prevRunNumber").asInt(); // it's already incremented, presumably
    	return currentRunNumber;
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

    private static ArrayNode getOrCreateArrayNode(ObjectNode parent, String nodeName) {
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
    

    public static long getFileSizes() {
    	return LoggingSingleton.fileSizes;
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
    
    //================================================================================
    // File Utilities
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
	            // System.out.println("Untarred: "+outPath);
	            /* Security guard: prevent "../../etc/passwd"–style entries
	             * from escaping the intended extraction root.
	             */
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
	                // Preserve timestamp; add other metadata here if you like
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
		List<String> tempFiles = new ArrayList<>();
		tempFiles.add(diffsTarZipFilename);
		tempFiles.add(errorLogFilename);
		tempFiles.add(testRunInfoFilename); // Moved to first in list increase readability
		
		try (OutputStream fOut = Files.newOutputStream(tempTargetTar); // no StandardOpenOption.CREATE; should fail
		     BufferedOutputStream bOut = new BufferedOutputStream(fOut);
		     TarArchiveOutputStream tOut = new TarArchiveOutputStream(bOut)) {

		    tOut.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
		    for (String file:tempFiles) {
		    	Path p = LoggingSingleton
	    				.tempFilepathResolve(LoggingSingleton.tempDirectory)
	    				.resolve(file);
		    	if (Files.exists(p)) {
	                 TarArchiveEntry entry = new TarArchiveEntry(p.toFile(), file);
	                 tOut.putArchiveEntry(entry);
	                 Files.copy(p, tOut);
	                 tOut.closeArchiveEntry();
		    	}
		    }
		    
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
		throwable.getStackTrace();
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
				+ LoggingSingleton.getTestRunNumber()
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
    		String message = generateMessage(throwable);
    		// System.out.println("\n ERROR: "+message);
    		if (loggedInitialError) {
    			return;
    		}
    		loggedInitialError = true;
    		skipLogging = true;
    		
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
    				errorFilepath,               // the target file
    			    List.of(message), 			 // data (Iterable<String> or byte[])
    			    StandardOpenOption.CREATE,   // create the file if it’s missing
    			    StandardOpenOption.APPEND    // move the write cursor to the end
    			);
    		atomicallySaveTempFiles();
    	} catch (Throwable T) {
    		// Do nothing
//    		String message = generateMessage(T);
//    		System.out.println("\n DOUBLE ERROR: "+message);
    	}
    }
}


