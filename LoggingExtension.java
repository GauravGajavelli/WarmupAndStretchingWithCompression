package testSupport;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestWatcher;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;

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
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.utils.IOUtils;

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
	static private Path tempDirectory;
	
	final static String testRunInfoFilename = "testRunInfo.json";
	final static String startTestRunInfoFilename  = "startTestRunInfo.json";
	final static String errorLogFilename = "error-logs.txt";
	final static String finalTarFilename = "run.tar";
	final static String diffsPrefix = "diffs";
	final static String tarSuffix = ".tar";
	final static String tarZipSuffix = ".tar.zip";

	final  static String sourceFolderName = "src";
	final static String testSupportPackageName = "testSupport";
	final static String diffsFolderName = "diffs";
	final static String patchesFolderName = "patches";
	final static String tempFolderName = "temp";

	private final long MB_SIZE = 1024 * 1024;   // 1 MB
	private final long KB_SIZE = 1024;   // 1 KB
	private final long SYNC_MAX_TIME = 500; // in ms
	private final long ASYNC_MAX_TIME = 3000; // in ms
	private final long WAY_TOO_LONG_FACTOR = 3;
	private final long REBASELINE_SIZE = 10L * KB_SIZE;
	private final long MAX_TAR_SIZE = 2L * MB_SIZE;
	private final long MAX_REPO_SIZE = 10L * MB_SIZE;
	private final long MAX_DIFFED_FILE_SIZE = MB_SIZE;

    //================================================================================
    // Public Methods (Only JUnit Callbacks)
    //================================================================================

	@Override
    public void beforeAll(ExtensionContext ctx) {
    	try {
    		LoggingSingleton.restartTiming();
    		
    		boolean skipLogging = false;
    		if ((getRepoFilesSize() > MAX_REPO_SIZE) || tarTooBig()) {
    			skipLogging = true;
    			return;
    		}

	    	if (!loggerInitialized) {

	    		initDirectories();
	    		File testRunInfoFile = filepathResolve(tempDirectory)
	    				.resolve(testRunInfoFilename).toFile();
				logger = LoggingSingleton.getInstance(testRunInfoFile);

				if (skipLogging) {
	    			LoggingSingleton.setSkipLogging(true);
	    		}
		        Runtime.getRuntime().addShutdownHook(new Thread(this::close));
	
		    	loggerInitialized = true;
	    	}
	    	
			Class<?> testClass = ctx.getTestClass().orElseThrow();
	        String testFileName = testClass.getSimpleName();
	        String packageName = testClass.getPackageName();
	        
	        LoggingSingleton.setCurrentTestFilePath(testFileName, packageName);

	        accumulateAndCheckTiming(SYNC_MAX_TIME);
    	} catch (Throwable T) {
    		logError(T);
    	}
	}
    
	@Override
	public void beforeEach(ExtensionContext ctx) {
		try {
			setUpAndCheckTiming(SYNC_MAX_TIME);
    		
			if (LoggingSingleton.getSkipLogging() || LoggingSingleton.tooManyStrikes()) {
				return;
			}
        // Get the test method name
	        String testName = ctx.getDisplayName();
	
	        // Get the test class
	        Class<?> testClass = ctx.getTestClass().orElseThrow();
	
	        String testFileName = testClass.getSimpleName();
	
	        LoggingSingleton.setTestRunNumberAndStatus(testFileName, testName, TestStatus.ABORTED); // aborted by default
	
	        accumulateAndCheckTiming(SYNC_MAX_TIME);
    	} catch (Throwable T) {
       		logError(T);
    	}
	}

	@Override
	public void testAborted(ExtensionContext ctx, Throwable cause) {
		try {
			setUpAndCheckTiming(SYNC_MAX_TIME);
	        
			if (LoggingSingleton.getSkipLogging() || LoggingSingleton.tooManyStrikes()) {
				return;
			}
			setTestRunNumberAndStatusHelper(ctx, TestStatus.ABORTED, cause);

	        accumulateAndCheckTiming(SYNC_MAX_TIME);
		} catch (Throwable T) {
    		logError(T);
		}

	}
	
	public void testDisabled(ExtensionContext ctx) { 
		try {
			setUpAndCheckTiming(SYNC_MAX_TIME);

			if (LoggingSingleton.getSkipLogging() || LoggingSingleton.tooManyStrikes()) {
				return;
			}
		    setTestRunNumberAndStatusHelper(ctx, TestStatus.DISABLED);
	
		    accumulateAndCheckTiming(SYNC_MAX_TIME);
		} catch (Throwable T) {
			logError(T);
		}

    }
    
	@Override
	public void testFailed(ExtensionContext ctx, Throwable cause) {
		try {
			setUpAndCheckTiming(SYNC_MAX_TIME);

			if (LoggingSingleton.getSkipLogging() || LoggingSingleton.tooManyStrikes()) {
				return;
			}
			setTestRunNumberAndStatusHelper(ctx, TestStatus.FAILED, cause);

	        accumulateAndCheckTiming(SYNC_MAX_TIME);
    	} catch (Throwable T) {
    		logError(T);
    	}

	}

	@Override
	public void testSuccessful(ExtensionContext ctx) {
		try {
			setUpAndCheckTiming(SYNC_MAX_TIME);

			if (LoggingSingleton.getSkipLogging() || LoggingSingleton.tooManyStrikes()) {
				return;
			}
			setTestRunNumberAndStatusHelper(ctx, TestStatus.SUCCESSFUL);
			
			accumulateAndCheckTiming(SYNC_MAX_TIME);
    	} catch (Throwable T) {
    		logError(T);
    	}
	}
	
	// Final method run
    public void close() {
		try {
			setUpAndCheckTiming(ASYNC_MAX_TIME);
//			Thread.sleep(10000);
			if (LoggingSingleton.getSkipLogging()) {
				return;
			} else if (LoggingSingleton.tooManyStrikes()) {
				LoggingSingleton.setSkipLogging(true);
			}

			int currentTestRunNumber = logger.getCurrentTestRunNumber();
			int seed = logger.getSeed();
			boolean redactDiffs = logger.getRedactDiffs();
			
			unzipAndUntarDiffs();
			writeDiffs(currentTestRunNumber, seed, redactDiffs);
			tarAndZipDiffs();
			
			// Copies over unchanged prior baselined diffs
			addPriorRebaslinedDiffs();

			// Copies over unchanged error logs
			Path oldErrorLogsFilePath = filepathResolve(tempDirectory).resolve(errorLogFilename);
			Path newErrorLogsFilePath = tempFilepathResolve(tempDirectory).resolve(errorLogFilename);
		    Files.move(oldErrorLogsFilePath,newErrorLogsFilePath,
		            StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);

		    accumulateAndCheckTiming(ASYNC_MAX_TIME); // not timing
			
		    saveTestRunInfo(logger.getObjectMapper(), logger.getTestRunInfo());
			atomicallySaveTempFiles();

			/* Any failure beyond this point will not be error logged properly due to the 
			 * files already being saved */
			
			// Delete intermediates
			Files.walk(tempDirectory)
		     .sorted(Comparator.reverseOrder())
		     .map(Path::toFile)
		     .forEach(File::delete);

			// Timing check above saving test run info; otherwise strikes won't be updated
		} catch (Throwable T) {
			logError(T);
		}
    }
	
	
    //================================================================================
    // Private Methods
    //================================================================================
	
		//================================================================================
	    // Logger Initialization
	    //================================================================================
    
	    private void initDirectories() throws IOException {
		    try {
		    	initTempDirectory();
		    	// This line is needed now that temp dirs are being used; nothing exists yet
		    	Files.createDirectories(tempFilepathResolve(tempDirectory));
		    	Path filesDir = filepathResolve(tempDirectory);
		    	Path tarPath  = filepathResolve().resolve(finalTarFilename);
	
		    	untarLogs(filesDir, tarPath);
		    	Path testRunInfoPath = filepathResolve(tempDirectory).resolve(testRunInfoFilename);
	    		Path errorLogFilePath = filepathResolve(tempDirectory).resolve(errorLogFilename);
	            if (Files.notExists(errorLogFilePath)) {
	            	Files.createFile(errorLogFilePath);                      // = CREATE_NEW
	            }
		    	if (Files.notExists(testRunInfoPath)) {
					Files.copy(
							filepathResolve().resolve(startTestRunInfoFilename),
							testRunInfoPath,
					        StandardCopyOption.REPLACE_EXISTING);
		    	}
			} catch (IOException e) {
		         throw new UncheckedIOException(e);
			}
		}
	    
	    void initTempDirectory() throws IOException {
			tempDirectory = Files.createTempDirectory(tempFolderName);
	    }
	    
    	// note that this is gets rid of the outermost folder surrounding the tar
    	private void untarLogs(Path targetPath, Path tarPath) {
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
    
		//================================================================================
	    // Test Run Info Update Methods
	    //================================================================================
    
		private void setTestRunNumberAndStatusHelper(ExtensionContext ctx, TestStatus testStatus) {
		    String testName = ctx.getDisplayName();
		    Class<?> testClass = ctx.getTestClass().orElseThrow();
		    String testFileName = testClass.getSimpleName();

		    LoggingSingleton.setTestRunNumberAndStatus(testFileName, testName, testStatus);		
		}
		
		private void setTestRunNumberAndStatusHelper(ExtensionContext ctx, TestStatus testStatus, Throwable testCause) {
		    String testName = ctx.getDisplayName();
			Class<?> testClass = ctx.getTestClass().orElseThrow();
			String testFileName = testClass.getSimpleName();

			LoggingSingleton.setTestRunNumberAndStatus(testFileName, testName, testStatus, testCause.toString());		
		}
	
		//================================================================================
	    // Size Checks
	    //================================================================================

		private boolean tarTooBig() throws IOException {
		    Path tarPath  = filepathResolve().resolve(finalTarFilename);
		    return fileLargerThan(tarPath, MAX_TAR_SIZE);
		}
		
		private long getRepoFilesSize() throws IOException {
			Path sourceFolder = Paths.get(sourceFolderName); // traversing the actual src
			return getFilesSize(sourceFolder);
		}
		
		private long getUncompressedDiffSize() throws IOException {
			return getFilesSize(filepathResolve(tempDirectory).resolve(diffsFolderName));
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
    		if (tooManyStrikes || (timeElapsed > WAY_TOO_LONG_FACTOR*time)) {
    			LoggingSingleton.addSecondStrike();
    		}
        }
		
    	//================================================================================
        // File Utilities
        //================================================================================

        private Path tempFilepathResolve(Path toResolve) {
        	return toResolve
        			.resolve(sourceFolderName)
        			.resolve(testSupportPackageName)
        			.resolve(tempFolderName);
        }

        private Path filepathResolve(Path toResolve) {
        	return toResolve
        			.resolve(sourceFolderName)
        			.resolve(testSupportPackageName);
        }

        private Path tempFilepathResolve() {
        	return Paths.get(sourceFolderName,testSupportPackageName,tempFolderName);
        }

        private Path filepathResolve() {
        	return Paths.get(sourceFolderName,testSupportPackageName);
        }
        
    	private List<String> readContents(Path path) throws IOException {
    	    Path file = tempDirectory.resolve(path);
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
    		boolean larger = fileLargerThan(path, size) || LoggingSingleton.fileWasTooLarge(path);
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
    		        if (dir.getFileName().toString().equals(testSupportPackageName)) {
    		            return FileVisitResult.SKIP_SUBTREE;
    		        }
    		        return FileVisitResult.CONTINUE;
    		    }
    		});

    	    return LoggingSingleton.getFileSizes();
    	}
    	
    	//================================================================================
        // String/Redacting Methods
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
    	
    	private char getRedactedChar(char c, int seed) {
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

    	private String redactString(String str, int seed) {
    		boolean sequenceStarted = false;
    		char seqChar = '$';
    		StringBuilder toRet = new StringBuilder();
    		for (int i = 0; i < str.length(); i++) {
    			char c = str.charAt(i);
    			if (isAlphanum(c)) {
    				if (!sequenceStarted) {
    					seqChar = getRedactedChar(c,seed);
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
    	
    	private List<String> redactStrings(List<String> strs, int seed) {
    		List<String> toRet = new ArrayList<>();
    		for (String str:strs) {
    			toRet.add(redactString(str,seed));
    		}
    		return toRet;
    	}
    	
        static String getDiffsTarFilename() {
        	return diffsPrefix+"_"+LoggingSingleton.getPreviousBaselineRunNumber()+"_"+tarSuffix;
        }
        
        static String getDiffsTarZipFilename() {
        	return diffsPrefix+"_"+LoggingSingleton.getPreviousBaselineRunNumber()+"_"+tarZipSuffix;
        }

        static boolean isDiffsTarZipFilename(String filename) {
        	return filename.startsWith(diffsPrefix) && filename.endsWith(tarZipSuffix);
        }
    	
    	//================================================================================
        // Diff/Log Update Methods
        //================================================================================

    	private void unzipAndUntarDiffs() {
    		Path zipPath = filepathResolve(tempDirectory).resolve(getDiffsTarZipFilename());
    		Path diffsDir = filepathResolve(tempDirectory).resolve(diffsFolderName);
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

    	private void writeDiffs(int testRunNumber, int seed, boolean redactDiffs) throws IOException {
    		
			Path sourceFolder = Paths.get(sourceFolderName);
			Path tempDiffsFolder = filepathResolve(tempDirectory)
        			.resolve(diffsFolderName);
			
			Files.walkFileTree(sourceFolder, new SimpleFileVisitor<Path>() {
			    @Override
			    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
			    	
			        String fileName = file.getFileName().toString();
		            String fileNameNoJava = fileName.substring(0,fileName.length()-".java".length());
		            
			    	if (fileName.endsWith(".java")) {
			    		
			            Path srcRoot   = Paths.get(sourceFolderName);
			            Path relative  = srcRoot.relativize(file);
			            Path packagePath = (relative.getParent() == null)?relative:relative.getParent(); // default package
			            String packageName = packagePath
			                        .toString()
			                        .replace(File.separatorChar, '.');

			            Path baselineFilePath = tempDiffsFolder
			            		.resolve("baselines")
			            		.resolve(packageName + "." + fileNameNoJava);

			            if (Files.exists(baselineFilePath)) { // the file already exists, diff it
			            	try {
			            		addDiffedFile(fileNameNoJava, packageName, file, baselineFilePath, testRunNumber, seed, redactDiffs);
    			    	    } catch (DiffException e) { 
    			    	    	throw new UncheckedIOException("DiffException: "+e.getMessage(), null);
    			    	    } catch (IOException e) { 
    			    	    	throw new UncheckedIOException(e);
    			    	    }
			            } else { // No baseline exists, copy it in
			                try {
			                	if (!fileIsOrWasLargerThan(file, MAX_DIFFED_FILE_SIZE)) {
			                		
    			                	String sourceContents = new String(Files.readAllBytes(file));
    			                	
    			                	if (redactDiffs) {
    			                		sourceContents = redactString(sourceContents, seed); // one-way redaction
    			                	}
    			                	Files.createDirectories(baselineFilePath.getParent());
    			                	
    			                	// Write baseline
    			                	writeContents(baselineFilePath, fileName, sourceContents);
    			                	
			                		// Write creation patch; runs every time, first one being the true time
    			                	String fileCreated = "File created!";
    			                	String toWriteName = fileNameNoJava + "_" + testRunNumber;
    			                	Path toWritePath = tempDiffsFolder
    			                			.resolve(patchesFolderName)
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
			        if (dir.getFileName().toString().equals(testSupportPackageName)) {
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
			if (getFilesSize(tempDiffsFolder.resolve(patchesFolderName)) > REBASELINE_SIZE) {
				LoggingSingleton.setRebaselining(true);
			}
    	}

    	private void tarAndZipDiffs() throws IOException {
			Files.createDirectories(tempFilepathResolve(tempDirectory));

			tarDiffs();
			
    	    Path targetTar = tempFilepathResolve(tempDirectory)
    	    		.resolve(getDiffsTarFilename());
    	    Path zipPath = tempFilepathResolve(tempDirectory)
    	    		.resolve(getDiffsTarZipFilename());

    	    try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zipPath, 
	                StandardOpenOption.CREATE,
	                StandardOpenOption.TRUNCATE_EXISTING));
    	         InputStream in = Files.newInputStream(targetTar)) {

    	        ZipEntry entry = new ZipEntry(diffsFolderName);
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
    	
    	private void addPriorRebaslinedDiffs() {
    		try {
				Path tempFilePath = filepathResolve(tempDirectory);
	    		
	    		Files.walkFileTree(tempFilePath, new SimpleFileVisitor<Path>() {
	    		    @Override
	    		    public FileVisitResult visitFile(Path p, BasicFileAttributes attrs) throws IOException {
	    		    	String fileName = p.getFileName().toString();
	    		    	if (isDiffsTarZipFilename(fileName) &&
	    		    			!fileName.equals(getDiffsTarZipFilename())) { // any prior
	    					Path newPriorRebaslinedDiffPath = tempFilepathResolve(tempDirectory)
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
    	
    	private void saveTestRunInfo(ObjectMapper objectMapper, JsonNode testRunInfo) throws StreamWriteException, DatabindException, IOException {
    		File testRunInfoFile = tempFilepathResolve(tempDirectory)
    				.resolve(testRunInfoFilename).toFile();
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(testRunInfoFile, testRunInfo);
    	}
    	
    	private void atomicallySaveTempFiles() {
    		Path targetTar = filepathResolve()
    				.resolve(finalTarFilename);
    		Path tempTargetTar = tempFilepathResolve(tempDirectory)
    				.resolve(finalTarFilename);
    		Path tempDirectoryPath = tempFilepathResolve(tempDirectory);

    		Map<String,Path> tempFiles = new TreeMap<>(new FilenameComparator());
    		tempFiles.put(errorLogFilename,null);
    		tempFiles.put(testRunInfoFilename,null); // Move to first in list increase readability

    		try (OutputStream fOut = Files.newOutputStream(tempTargetTar);
    		     BufferedOutputStream bOut = new BufferedOutputStream(fOut);
    		     TarArchiveOutputStream tOut = new TarArchiveOutputStream(bOut)) {
    		    
    			tOut.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
    		    
    			// Add all diff tar files
        		Files.walkFileTree(tempDirectoryPath, new SimpleFileVisitor<Path>() {
        		    @Override
        		    public FileVisitResult visitFile(Path p, BasicFileAttributes attrs) throws IOException {
        		    	String fileName = p.getFileName().toString();
        		    	if (tempFiles.containsKey(fileName) || isDiffsTarZipFilename(fileName)) {
        		    		tempFiles.put(fileName,p);
        		    	}
        		        return FileVisitResult.CONTINUE;
        		    }
        		});
        		
        		for (String fileName:tempFiles.keySet()) {
        			Path p = tempFiles.getOrDefault(fileName,null);
        			if (p != null) {
    	                TarArchiveEntry entry = new TarArchiveEntry(p.toFile(), fileName);
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
        // Diff Update Helpers
        //================================================================================
    	
    	private long addDiffedFile(String fileName, String packageName, Path revisedPath, Path sourcePath, 
    			int testRunNumber, int seed, boolean redactDiffs) throws DiffException, IOException {
    		// Create this path if it didn't exist: testSupport/diffs/patches/filename
    		String toWriteName = fileName + "_" + testRunNumber;
    		Path toWritePath = filepathResolve(tempDirectory)
    				.resolve(diffsFolderName)
    				.resolve(patchesFolderName)
    				.resolve(packageName + "." + toWriteName);
    		String diffString;
    		if (fileIsOrWasLargerThan(sourcePath, MAX_DIFFED_FILE_SIZE) || fileIsOrWasLargerThan(revisedPath, MAX_DIFFED_FILE_SIZE)) {
    			diffString = "File too large!";
    		} else {
    			// Read in the files
    	        List<String> original = readContents(sourcePath);
    	        List<String> revised = 	Files.readAllLines(revisedPath);
    	        
    	        if (redactDiffs) {
    	        	revised = redactStrings(revised,seed); // original should already be redacted
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
    		Path diffsDir = filepathResolve(tempDirectory)
    				.resolve(diffsFolderName);
    		Path targetTar = tempFilepathResolve(tempDirectory)
    				.resolve(getDiffsTarFilename());

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
    	
        //================================================================================
        // Error Logging
        //================================================================================
    	
    	private String generateMessage(Throwable throwable) {
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
    				+ LoggingSingleton.getTestFilePackageName()
    				+" "
    				+ LoggingSingleton.getTestFileName()
    				+ "\n"
    				+ throwable.getMessage()
    				+ "\n"
    				+ stackStringBuilder.toString()
    				+ "\n";
    	}

    	// Essentially makes a last-ditch effort to log the error
        private void logError(Throwable throwable) {
        	try {
    	        LoggingSingleton.accumulateTime(); // all publics throwing this will have already started time
        		
        		String message = generateMessage(throwable);
        		if (LoggingSingleton.getLoggedInitialError()) {
        			return;
        		}
        		LoggingSingleton.setLoggedInitialError();
        		LoggingSingleton.setSkipLogging(true);
        		
        		Path errorFilepath = tempFilepathResolve(tempDirectory).resolve(errorLogFilename);
        		Path filesDir = tempFilepathResolve(tempDirectory);
        		Path tarPath = filepathResolve().resolve(finalTarFilename);
        		

    			Files.walk(tempDirectory.resolve(sourceFolderName))
    		     .sorted(Comparator.reverseOrder())
    		     .map(Path::toFile)
    		     .forEach(File::delete);
    			
        		Files.createDirectories(errorFilepath.getParent());
        		Files.createDirectories(filesDir);
        		Files.createDirectories(tarPath.getParent());
        		
       	    	untarLogs(filesDir, tarPath);
        		Files.write(
        				errorFilepath,
        			    List.of(message),
        			    StandardOpenOption.CREATE,
        			    StandardOpenOption.APPEND
        			);
        		
        		atomicallySaveTempFiles();
        	} catch (Throwable T) {
        		// Do nothing
        	}
        }
}
