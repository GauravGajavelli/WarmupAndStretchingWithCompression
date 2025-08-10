package testSupport;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestWatcher;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.utils.IOUtils;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;

import com.fasterxml.jackson.core.exc.StreamWriteException;
import com.fasterxml.jackson.databind.DatabindException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.difflib.DiffUtils;
import com.github.difflib.algorithm.DiffException;
import com.github.difflib.patch.AbstractDelta;
import com.github.difflib.patch.Patch;

public class LoggingExtension implements TestWatcher, BeforeAllCallback, BeforeEachCallback {	
	
    //================================================================================
    // Fields
    //================================================================================

	private LoggingSingleton logger;
	static private boolean loggerInitialized = false;
	static private final String testRunInfoFilename = "testRunInfo.json";
	static private final String startTestRunInfoFilename  = "startTestRunInfo.json";
	static private final String finalTarFilename = "run.tar";
	static private final String errorLogFilename = "error-logs.txt";
	static private final long MB_SIZE = 1024 * 1024;   // 1 MiB
	static private final long KB_SIZE = 1024;   // 1 MiB
	static private final long OPEN_MAX_TIME = 500; // in milliseconds
	static private final long CLOSE_MAX_TIME = 3000; // in milliseconds

    //================================================================================
    // Public Methods (Only JUnit Callbacks)
    //================================================================================

	@Override
    public void beforeAll(ExtensionContext ctx) {
    	try {
    		LoggingSingleton.restartTiming();
    		
    		if ((getRepoFilesSize() > (10L * MB_SIZE)) || tarTooBig()) {
    			LoggingSingleton.setSkipLogging(true);
    			return;
    		}

	    	if (!loggerInitialized) {
	    		initLogger(); // gets invalid
		        Runtime.getRuntime().addShutdownHook(new Thread(this::close));

				// Initialize the static fields in the singleton
				String testClassName = ctx.getDisplayName();
		    	LoggingSingleton.incrementRunNumber();
		    	LoggingSingleton.addRunTime();
		    	
		    	LoggingSingleton.removeOldStrike();
	
		    	loggerInitialized = true;
	    	}
			Class<?> testClass = ctx.getTestClass().orElseThrow();
	        String testFileName = testClass.getSimpleName();
	        String packageName = testClass.getPackageName();
	        
	        LoggingSingleton.setCurrentTestFilePath(testFileName, packageName);
//    		int l = 5/0; // For error injection
	        accumulateAndCheckTiming(OPEN_MAX_TIME);
    	} catch (Throwable T) {
    		LoggingSingleton.logError(T);
    	}
	}
    
	@Override
	public void beforeEach(ExtensionContext arg0) throws Exception {
		try {
			setUpAndCheckTiming(OPEN_MAX_TIME);
    		
			if (LoggingSingleton.getSkipLogging()) {
				return;
			}
        // Get the test method name
	        String testName = arg0.getDisplayName();
	
	        // Get the test class
	        Class<?> testClass = arg0.getTestClass()
	            .orElseThrow(() -> new IllegalStateException("No test class"));
	
	        String testFileName = testClass.getSimpleName();
	
	        LoggingSingleton.setTestRunNumberAndStatus(testFileName, testName, TestStatus.ABORTED); // aborted by default
	
	        accumulateAndCheckTiming(OPEN_MAX_TIME);
    	} catch (Throwable T) {
       		LoggingSingleton.logError(T);
    	}
	}

	@Override
	public void testAborted(ExtensionContext ctx, Throwable cause) {
		try {
			setUpAndCheckTiming(OPEN_MAX_TIME);
	        
			if (LoggingSingleton.getSkipLogging()) {
				return;
			}
			setTestRunNumberAndStatusHelper(ctx, TestStatus.ABORTED, cause);

	        accumulateAndCheckTiming(OPEN_MAX_TIME);
		} catch (Throwable T) {
    		LoggingSingleton.logError(T);
		}

	}
	
	public void testDisabled(ExtensionContext ctx) { 
		try {
			setUpAndCheckTiming(OPEN_MAX_TIME);

			if (LoggingSingleton.getSkipLogging()) {
				return;
			}
		    setTestRunNumberAndStatusHelper(ctx, TestStatus.DISABLED);
	
		    accumulateAndCheckTiming(OPEN_MAX_TIME);
		} catch (Throwable T) {
			LoggingSingleton.logError(T);
		}

    }
    
	@Override
	public void testFailed(ExtensionContext ctx, Throwable cause) {
		try {
			setUpAndCheckTiming(OPEN_MAX_TIME);

			if (LoggingSingleton.getSkipLogging()) {
				return;
			}
			setTestRunNumberAndStatusHelper(ctx, TestStatus.FAILED, cause);

	        accumulateAndCheckTiming(OPEN_MAX_TIME);
    	} catch (Throwable T) {
    		LoggingSingleton.logError(T);
    	}

	}

	@Override
	public void testSuccessful(ExtensionContext ctx) {
		try {
			setUpAndCheckTiming(OPEN_MAX_TIME);

			if (LoggingSingleton.getSkipLogging()) {
				return;
			}
			setTestRunNumberAndStatusHelper(ctx, TestStatus.SUCCESSFUL);
			
			accumulateAndCheckTiming(OPEN_MAX_TIME);
    	} catch (Throwable T) {
    		LoggingSingleton.logError(T);
    	}
	}
	
	// Final method run
    public void close() {
		try {
			setUpAndCheckTiming(CLOSE_MAX_TIME);
//			Thread.sleep(10000);
			if (LoggingSingleton.getSkipLogging()) {
				return;
			}

			int currentTestRunNumber = logger.getCurrentTestRunNumber();
			int seed = logger.getSeed();
			boolean encryptDiffs = logger.getEncryptDiffs();
			
			unzipAndUntarDiffs();
			writeDiffs(currentTestRunNumber, seed, encryptDiffs);
			tarAndZipDiffs();
			
			// Copies over unchanged prior baselined diffs
			addPriorRebaslinedDiffs();

			// Copies over unchanged error logs
			Path oldErrorLogsFilePath = LoggingSingleton.filepathResolve(LoggingSingleton.tempDirectory).resolve(errorLogFilename);
			Path newErrorLogsFilePath = LoggingSingleton.tempFilepathResolve(LoggingSingleton.tempDirectory).resolve(errorLogFilename);
		    Files.move(oldErrorLogsFilePath,newErrorLogsFilePath,
		            StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);

		    accumulateAndCheckTiming(CLOSE_MAX_TIME); // not timing
			
		    saveTestRunInfo(logger.getObjectMapper(), logger.getTestRunInfo());
			LoggingSingleton.atomicallySaveTempFiles();

			/* Any failure beyond this point will not be error logged properly due to the 
			 * files already being saved */

			// Delete intermediates
			Files.walk(logger.tempDirectory)
		     .sorted(Comparator.reverseOrder())
		     .map(Path::toFile)
		     .forEach(File::delete);

			// Timing check above saving test run info; otherwise strikes won't be updated
		} catch (Throwable T) {
			LoggingSingleton.logError(T);
		}
    }
	
	
    //================================================================================
    // Private Methods
    //================================================================================
	
		//================================================================================
	    // Logger Initialization
	    //================================================================================
    
    private void initLogger() throws IOException {
	    try {
	    	LoggingSingleton.initTempDirectory();
	    	// This line is needed now that temp dirs are being used; nothing exists yet
	    	Files.createDirectories(LoggingSingleton.tempFilepathResolve(LoggingSingleton.tempDirectory));
	    	Path filesDir = LoggingSingleton.filepathResolve(LoggingSingleton.tempDirectory);
	    	Path tarPath  = LoggingSingleton.filepathResolve().resolve(finalTarFilename);

	    	LoggingSingleton.untarFile(filesDir, tarPath);
	    	Path testRunInfoPath = LoggingSingleton.filepathResolve(LoggingSingleton.tempDirectory).resolve(testRunInfoFilename);
    		Path errorLogFilePath = LoggingSingleton.filepathResolve(LoggingSingleton.tempDirectory).resolve(errorLogFilename);
            if (Files.notExists(errorLogFilePath)) {
            	Files.createFile(errorLogFilePath);                      // = CREATE_NEW
            }
	    	if (Files.notExists(testRunInfoPath)) {
				Files.copy(
						LoggingSingleton.filepathResolve().resolve(startTestRunInfoFilename),
						testRunInfoPath,
				        StandardCopyOption.REPLACE_EXISTING);
	    	}
		} catch (IOException e) {
	         throw new UncheckedIOException(e);
		} finally {
			logger = LoggingSingleton.getInstance();
		}
    }
    
		//================================================================================
	    // Test Run Info Update Methods
	    //================================================================================
    
		private void setTestRunNumberAndStatusHelper(ExtensionContext ctx, TestStatus testStatus) {
		    String testName = ctx.getDisplayName();
		    Class<?> testClass = ctx.getTestClass()
		        .orElseThrow(() -> new IllegalStateException("No test class"));
		    String testFileName = testClass.getSimpleName();

		    LoggingSingleton.setTestRunNumberAndStatus(testFileName, testName, testStatus);		
		}
		
		private void setTestRunNumberAndStatusHelper(ExtensionContext ctx, TestStatus testStatus, Throwable testCause) {
		    String testName = ctx.getDisplayName();
		    Class<?> testClass = ctx.getTestClass()
		        .orElseThrow(() -> new IllegalStateException("No test class"));
		    String testFileName = testClass.getSimpleName();

			LoggingSingleton.setTestRunNumberAndStatus(testFileName, testName, testStatus, testCause.toString());		
		}
	
		//================================================================================
	    // Size Checks
	    //================================================================================

		private boolean tarTooBig() throws IOException {
		    Path tarPath  = LoggingSingleton.filepathResolve().resolve(finalTarFilename);
		    long size = 10L * MB_SIZE;
		    return fileLargerThan(tarPath, size);
		}
		
		private long getRepoFilesSize() throws IOException {
			Path sourceFolder = Paths.get("src"); // traversing the actual src
			return getFilesSize(sourceFolder);
		}
		
		private long getUncompressedDiffSize() throws IOException {
			return getFilesSize(LoggingSingleton.filepathResolve(LoggingSingleton.tempDirectory).resolve("diffs"));
		}        

		//================================================================================
	    // Timing Checks
	    //================================================================================

		private void setUpAndCheckTiming(long time) {
    		LoggingSingleton.restartTiming();
    		checkTiming(time);
		}

        private void accumulateAndCheckTiming(long time) {
	        LoggingSingleton.accumulateTime();
	        checkTiming(time);
        }

        private void checkTiming(long time) {
    		long timeElapsed = LoggingSingleton.getCurrentTotalElapsedTime();

    		if (timeElapsed > time) {
    			LoggingSingleton.addStrike();
    		}

    		boolean tooManyStrikes = LoggingSingleton.tooManyStrikes();
    		if (tooManyStrikes || (timeElapsed > 5*time)) {
    			LoggingSingleton.setSkipLogging(true);
    		}
        }
		
    	//================================================================================
        // File Utilities
        //================================================================================
    	
    	private List<String> readContents(Path path) throws IOException {
    	    Path file = LoggingSingleton.tempDirectory.resolve(path);
    	    String content  = Files.readString(file, StandardCharsets.UTF_8);  // Java 11+

    	    // Split into lines
    	    return Arrays.asList(content.split("\\r?\\n"));
    	}
    	
    	private long writeContents(Path toWritePath, String fileName, String toWrite) throws IOException {
    	        // `StandardOpenOption.CREATE` replaces the file if it already exists.
    	        Files.writeString(
    	        		toWritePath,
    	                toWrite,
    	                StandardCharsets.UTF_8,
    	                StandardOpenOption.CREATE,
    	                StandardOpenOption.TRUNCATE_EXISTING   // overwrite
    	        );
    	        return Files.size(toWritePath);
    	}

    	private boolean fileLargerThan(Path path, long size) throws IOException {
    		return Files.size(path) >= size;
    	}
    	
    	private boolean fileIsOrWasLargerThan(Path path, long size) throws IOException {
    		boolean larger = fileLargerThan(path, MB_SIZE) || LoggingSingleton.fileWasTooLarge(path);
    		if (larger) {
    			LoggingSingleton.addTooLargeFile(path);
    		}
    		return larger;
    	}
    	
    	private long getFilesSize(Path sourceFolder) throws IOException {
    		LoggingSingleton.resetFileSizes();

    		Files.walkFileTree(sourceFolder, new SimpleFileVisitor<Path>() {
    		    @Override
    		    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
    	    		LoggingSingleton.increaseFileSizes(Files.size(file));	

    		        return FileVisitResult.CONTINUE;
    		    }

    		    @Override
    		    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
    		        if (dir.getFileName().toString().equals("testSupport")) {
    		            return FileVisitResult.SKIP_SUBTREE;
    		        }
    		        return FileVisitResult.CONTINUE;
    		    }
    		});

    	    return LoggingSingleton.getFileSizes();
    	}
    	
    	//================================================================================
        // String/Hashing Methods
        //================================================================================
    	
    	private String buildDiffOutputString(List<AbstractDelta<String>> deltas) {
    		StringBuilder toRet = new StringBuilder();
    		 // similar to encode/decode string; number of diffs and a delimiter
    		toRet.append(deltas.size());
    		toRet.append(";\n");
    		for (int i = 0; i < deltas.size(); i++) {
    			AbstractDelta<String> delta = deltas.get(i);
                toRet.append(delta.getType());
        		toRet.append("\n");

                List<String> sourceLines = delta.getSource().getLines();
        		toRet.append(sourceLines.size());
        		toRet.append(",\n");
        		for (String sourceLine:sourceLines) {
        			toRet.append(sourceLine);
            		toRet.append("\n");
        		}
        		
                List<String> targetLines = delta.getTarget().getLines();
        		toRet.append(targetLines.size());
        		toRet.append(",\n");
        		for (String targetLine:targetLines) {
        			toRet.append(targetLine);
            		toRet.append("\n");
        		}
    		}
    		return toRet.toString();
    	}
    	
    	private boolean isAlphanum(char c) {
    		return ('0' <= c && c <= '9') || ('a' <= c && c <= 'z') || ('A' <= c && c <= 'Z');
    	}
    	
    	// Maps 0–61 → ['0'–'9', 'A'–'Z', 'a'–'z'] 
    	private char idxToAlphanum(int idx) {
    	    if (idx < 10) {
    	        return (char) ('0' + idx);
    	    }
    	    idx -= 10;
    	    
    	    if (idx < 26) {
    	        return (char) ('A' + idx);
    	    }
    	    idx -= 26;
    	    
    	    return (char) ('a' + idx);
    	}
    	
    	private char getEncryptedChar(char c, int seed) {
            int x = seed ^ c;          // combine seed and character
            x *= 0x27D4_EB2D;           // mix 1
            x ^= x >>> 15;              // mix 2
            x *= 0x85EB_CA6B;           // mix 3
            x ^= x >>> 13;              // mix 4

    		int span = ('9' - '0' + 1)   // 10 digits
    		         + ('Z' - 'A' + 1)   // 26 uppercase
    		         + ('z' - 'a' + 1);  // 26 lowercase
    		// span == 62
    		int idx = Math.floorMod(x, span);
            
            return idxToAlphanum(idx);
    	}

    	private String encryptString(String str, int seed) {
    		boolean sequenceStarted = false;
    		char seqChar = '$';
    		StringBuilder toRet = new StringBuilder();
    		for (int i = 0; i < str.length(); i++) {
    			char c = str.charAt(i);
    			if (isAlphanum(c)) {
    				if (!sequenceStarted) {
    					seqChar = getEncryptedChar(c,seed);
    					sequenceStarted = true;
    				}
        			toRet.append(seqChar);
    			} else {
    				sequenceStarted = false;    				
    				seqChar = '$';
    				toRet.append(c);
    			}
    		}
    		return toRet.toString();
    	}
    	
    	private List<String> encryptStrings(List<String> strs, int seed) {
    		List<String> toRet = new ArrayList<>();
    		for (String str:strs) {
    			toRet.add(encryptString(str,seed));
    		}
    		return toRet;
    	}
    	
    	//================================================================================
        // Diff Update Methods
        //================================================================================

    	private void addPriorRebaslinedDiffs() {
    		try {
				Path tempFilePath = LoggingSingleton.filepathResolve(LoggingSingleton.tempDirectory);
	    		
	    		Files.walkFileTree(tempFilePath, new SimpleFileVisitor<Path>() {
	    		    @Override
	    		    public FileVisitResult visitFile(Path p, BasicFileAttributes attrs) throws IOException {
	    		    	String fileName = p.getFileName().toString();
	    		    	if (LoggingSingleton.isDiffsTarZipFilename(fileName) &&
	    		    			!fileName.equals(LoggingSingleton.getDiffsTarZipFilename())) { // any prior
	    					Path newPriorRebaslinedDiffPath = LoggingSingleton
	    							.tempFilepathResolve(LoggingSingleton.tempDirectory)
	    							.resolve(fileName);
	    				    Files.move(p,newPriorRebaslinedDiffPath,
	    				            StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);

	    		    	}
	    		        return FileVisitResult.CONTINUE;
	    		    }
	    		});
    	    } catch (IOException e) { 
    	    	throw new UncheckedIOException(e);
    	    }
    	}

    	private void unzipAndUntarDiffs() {
    		Path zipPath = LoggingSingleton.filepathResolve(LoggingSingleton.tempDirectory).resolve(LoggingSingleton.getDiffsTarZipFilename());
    		Path diffsDir = LoggingSingleton.filepathResolve(LoggingSingleton.tempDirectory).resolve("diffs");
    		if (Files.notExists(zipPath) || LoggingSingleton.isRebaselining()) {
    			return;
    		}
    		try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipPath))) {
    		    ZipEntry zentry = zis.getNextEntry();
    		    if (zentry != null) {
    		        TarArchiveInputStream tis = new TarArchiveInputStream(new BufferedInputStream(zis));
    		        TarArchiveEntry entry;
    		        while ((entry = tis.getNextTarEntry()) != null) {

    		            Path outPath = diffsDir.resolve(entry.getName()).normalize();
    		            if (!outPath.startsWith(diffsDir)) {
    		                throw new IOException("Illegal TAR entry: " + entry.getName());
    		            }

    		            if (entry.isDirectory()) {
    		                Files.createDirectories(outPath);
    		            } else {
    		                Files.createDirectories(outPath.getParent());
    		                try (OutputStream o = Files.newOutputStream(outPath, 
    		    	                StandardOpenOption.CREATE,
    		    	                StandardOpenOption.TRUNCATE_EXISTING)) {
    		                    IOUtils.copy(tis, o);
    		                }
    		                
    		                FileTime mtime = FileTime.fromMillis(entry.getModTime().getTime());
    		                Files.setLastModifiedTime(outPath, mtime);
    		            }
    		        }
    		    }
    	    } catch (IOException e) { 
    	    	throw new UncheckedIOException(e);
    	    }
    	}

    	private void writeDiffs(int testRunNumber, int seed, boolean encryptDiffs) throws IOException {
			Path sourceFolder = Paths.get("src");
			Path tempDiffsFolder = LoggingSingleton.filepathResolve(LoggingSingleton.tempDirectory)
        			.resolve("diffs");
    			Files.walkFileTree(sourceFolder, new SimpleFileVisitor<Path>() {
    			    @Override
    			    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
    			        String fileName = file.getFileName().toString();
    		            String fileNameNoJava = fileName.substring(0,fileName.length()-".java".length());
    		            
    			    	if (fileName.endsWith(".java")) {
    			            Path srcRoot   = Paths.get("src");
    			            Path relative  = srcRoot.relativize(file);
    			            Path packagePath = relative.getParent();
    			            String packageName = packagePath
    			                        .toString()
    			                        .replace(File.separatorChar, '.');

    			            Path baselineFilePath = tempDiffsFolder
    			            		.resolve("baselines")
    			            		.resolve(packageName + "." + fileNameNoJava);

    			            if (Files.exists(baselineFilePath)) { // the file already exists, diff it
    			            	try {
    			            		addDiffedFile(fileNameNoJava, packageName, file, baselineFilePath, testRunNumber, seed, encryptDiffs);
	    			    	    } catch (DiffException e) { 
	    			    	    	throw new UncheckedIOException("DiffException: "+e.getMessage(), null);
	    			    	    } catch (IOException e) { 
	    			    	    	throw new UncheckedIOException(e);
	    			    	    }
    			            } else { // No baseline exists, copy it in
    			                try {
    			                	if (!fileIsOrWasLargerThan(file, MB_SIZE)) {
	    			                	String sourceContents = new String(Files.readAllBytes(file));
	    			                	if (encryptDiffs) {
	    			                		sourceContents = encryptString(sourceContents, seed); // one-way encryption
	    			                	}
	    			                	Files.createDirectories(baselineFilePath.getParent());
	    			                	
	    			                	// Write baseline
	    			                	writeContents(baselineFilePath, fileName, sourceContents);
	    			                	
    			                		// Write creation patch; runs every time, first one being the true time
	    			                	String fileCreated = "File created!";
	    			                	String toWriteName = fileNameNoJava + "_" + testRunNumber;
	    			                	Path toWritePath = tempDiffsFolder
	    			                			.resolve("patches")
	    			                			.resolve(packageName + "." + toWriteName);
	    			                	Files.createDirectories(toWritePath.getParent());
	    			                	writeContents(toWritePath,toWriteName+".java",fileCreated);
    			                	}
	    			    	    } catch (IOException e) { 
	    			    	    	throw new UncheckedIOException(e);
	    			    	    }
    			            }
    			        }
    			        return FileVisitResult.CONTINUE;
    			    }

    			    @Override
    			    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
    			        if (dir.getFileName().toString().equals("testSupport")) {
    			            return FileVisitResult.SKIP_SUBTREE;
    			        }
    			        return FileVisitResult.CONTINUE;
    			    }
    			});

    			// Checks for rebaselining after succesfful run
        		if (LoggingSingleton.isRebaselining()) {
        			LoggingSingleton.updatePreviousBaselineRunNumber();
        		}

    			LoggingSingleton.setRebaselining(false);
    			// limiting total patch size/checking for potential rebaselining
    			if (getFilesSize(tempDiffsFolder.resolve("patches")) > (10*KB_SIZE)) {
    				LoggingSingleton.setRebaselining(true);
    			}
    	}

    	private void tarAndZipDiffs() throws IOException {
			Files.createDirectories(LoggingSingleton.tempFilepathResolve(LoggingSingleton.tempDirectory));

			tarDiffs();
			
    	    Path targetTar = LoggingSingleton
    	    		.tempFilepathResolve(LoggingSingleton.tempDirectory)
    	    		.resolve(LoggingSingleton.getDiffsTarFilename());
    	    Path zipPath = LoggingSingleton
    	    		.tempFilepathResolve(LoggingSingleton.tempDirectory)
    	    		.resolve(LoggingSingleton.getDiffsTarZipFilename());

    	    try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zipPath, 
	                StandardOpenOption.CREATE,
	                StandardOpenOption.TRUNCATE_EXISTING));
    	         InputStream in = Files.newInputStream(targetTar)) {

    	        ZipEntry entry = new ZipEntry("diffs");
    	        zos.putNextEntry(entry);

    	        byte[] buffer = new byte[8_192];
    	        int bytesRead;
    	        while ((bytesRead = in.read(buffer)) != -1) {
    	            zos.write(buffer, 0, bytesRead);
    	        }

    	        zos.closeEntry();
    	    } catch (IOException e) { 
    	    	throw new UncheckedIOException(e);
    	    }
	    }
    	
    	private static void saveTestRunInfo(ObjectMapper objectMapper, JsonNode testRunInfo) throws StreamWriteException, DatabindException, IOException {
    		File testRunInfoFile = LoggingSingleton
    				.tempFilepathResolve(LoggingSingleton.tempDirectory)
    				.resolve(testRunInfoFilename).toFile();
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(testRunInfoFile, testRunInfo);
    	}
    	
    	//================================================================================
        // Diff Update Helpers
        //================================================================================
    	
    	private long addDiffedFile(String fileName, String packageName, Path revisedPath, Path sourcePath, 
    			int testRunNumber, int seed, boolean encryptDiffs) throws DiffException, IOException {
    		// Create this path if it didn't exist: testSupport/diffs/patches/filename
    		String toWriteName = fileName + "_" + testRunNumber;
    		Path toWritePath = LoggingSingleton.filepathResolve(LoggingSingleton.tempDirectory)
    				.resolve("diffs")
    				.resolve("patches")
    				.resolve(packageName + "." + toWriteName);
    		String diffString;
    		if (fileIsOrWasLargerThan(sourcePath, MB_SIZE) || fileIsOrWasLargerThan(revisedPath, MB_SIZE)) {
    			diffString = "File too large!";
    		} else {
    			// Read in the files
    	        List<String> original = readContents(sourcePath);
    	        List<String> revised = 	Files.readAllLines(revisedPath);
    	        
    	        if (encryptDiffs) {
    	        	revised = encryptStrings(revised,seed); // original should already be encrypted
    	        }

    	        // Compute the diff: original -> revised
    	        Patch<String> patch = new Patch<>();
    				patch = DiffUtils.diff(original, revised);

    			// Write diffs
    			List<AbstractDelta<String>> deltas = patch.getDeltas();
    			if (deltas == null || deltas.size() == 0) {
    				return 0;
    			}
    			
            	Files.createDirectories(toWritePath.getParent());

    			diffString = buildDiffOutputString(deltas);
    		}
			return writeContents(toWritePath,toWriteName+".java",diffString);
    	}
    	
    	private void tarDiffs() {
    		Path diffsDir = LoggingSingleton
    				.filepathResolve(LoggingSingleton.tempDirectory)
    				.resolve("diffs");
    		Path targetTar = LoggingSingleton
    				.tempFilepathResolve(LoggingSingleton.tempDirectory)
    				.resolve(LoggingSingleton.getDiffsTarFilename());

    		try (OutputStream fOut = Files.newOutputStream(targetTar, 
	                StandardOpenOption.CREATE,
	                StandardOpenOption.TRUNCATE_EXISTING);
    		     BufferedOutputStream bOut = new BufferedOutputStream(fOut);
    		     TarArchiveOutputStream tOut = new TarArchiveOutputStream(bOut)) {

    		    tOut.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);

    		    Files.walk(diffsDir)
    		         .filter(Files::isRegularFile)
    		         .forEach(p -> {
    		             try {
    		                 Path rel = diffsDir.relativize(p);
    		                 TarArchiveEntry entry = new TarArchiveEntry(p.toFile(), rel.toString());
    		                 tOut.putArchiveEntry(entry);
    		                 Files.copy(p, tOut);
    		                 tOut.closeArchiveEntry();
    		             } catch (IOException ex) {
    		                 throw new UncheckedIOException(ex);
    		             }
    		         });
    	    } catch (IOException e) { 
    	    	throw new UncheckedIOException(e);
    	    }
    	}
    
}
