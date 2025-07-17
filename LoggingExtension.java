package testSupport;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext.Namespace;
import org.junit.jupiter.api.extension.TestWatcher;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestPlan;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.OpenOption;
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
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.utils.IOUtils;
import org.junit.jupiter.api.extension.BeforeAllCallback;

import com.fasterxml.jackson.core.exc.StreamWriteException;
import com.fasterxml.jackson.databind.DatabindException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.difflib.DiffUtils;
import com.github.difflib.algorithm.DiffException;
import com.github.difflib.patch.AbstractDelta;
import com.github.difflib.patch.Patch;

public class LoggingExtension implements TestWatcher, BeforeAllCallback {	
	
    //================================================================================
    // BeforeAll Variables and Methods
    //================================================================================
	
	private static boolean storeInitialized = false;
    // lives for the whole JVM
    private static final Namespace NS = Namespace.GLOBAL;

    public void beforeAll(ExtensionContext ctx) {
    	try {
    		
    	if (!storeInitialized) {
    		initLogger(); // gets invalid
	        Runtime.getRuntime().addShutdownHook(new Thread(this::close));
	
			// Initialize the static fields in the singleton
			String testClassName = ctx.getDisplayName();
	    	LoggingSingleton.incrementRunNumber();
	    	LoggingSingleton.addRunTime();

	    	storeInitialized = true;
    	}
		Class<?> testClass = ctx.getTestClass().orElseThrow();
        String testFileName = testClass.getSimpleName();
        String packageName = testClass.getPackageName();
        
        LoggingSingleton.setCurrentTestFilePath(testFileName, packageName);

    	} catch (Throwable T) {
    		LoggingSingleton.logError(T);
    	}
	}

    //================================================================================
    // TestWatcher Variables and Methods
    //================================================================================
	
	@Override
	public void testAborted(ExtensionContext ctx, Throwable cause) {
		try {
	    
			setTestRunNumberAndStatusHelper(ctx, TestStatus.ABORTED, cause);

		} catch (Throwable T) {
    		LoggingSingleton.logError(T);
		}

	}
	
	public void testDisabled(ExtensionContext ctx) { 
		try {
		
	    setTestRunNumberAndStatusHelper(ctx, TestStatus.DISABLED);
	    
	} catch (Throwable T) {
		LoggingSingleton.logError(T);
	}

    }
    
	@Override
	public void testFailed(ExtensionContext ctx, Throwable cause) {
		try {
	    
			setTestRunNumberAndStatusHelper(ctx, TestStatus.FAILED, cause);
	    
    	} catch (Throwable T) {
    		LoggingSingleton.logError(T);
    	}

	}

	@Override
	public void testSuccessful(ExtensionContext ctx) {
		try {
		
	    setTestRunNumberAndStatusHelper(ctx, TestStatus.SUCCESSFUL);
	    
    	} catch (Throwable T) {
    		LoggingSingleton.logError(T);
    	}
	}
	
    public void close() {
		try {
			
			int currentTestRunNumber = logger.getCurrentTestRunNumber();
			int seed = logger.getSeed();
			boolean encryptDiffs = logger.getEncryptDiffs();
			
			unzipAndUntarDiffs();
			writeDiffs(currentTestRunNumber, seed, encryptDiffs);
			tarAndZipDiffs();
			
			// Delete loaded files
			deletePath(filepath+"diffs");
			
//		try {
//			Files.delete(Paths.get(filepath+testRunInfoFilename));
//			Files.delete(Paths.get(filepath+diffsTarZipFilename));
//	    } catch (IOException e) { 
//	    	throw new UncheckedIOException(e);
//	    }
			
			saveTestRunInfo(logger.getObjectMapper(), logger.getTestRunInfo());
			atomicallySaveTempFiles();
			
			// Delete intermediates
			deletePath(tempFilepath);
		} catch (Throwable T) {
			LoggingSingleton.logError(T);
		}
    }
	
	
	private void setTestRunNumberAndStatusHelper(ExtensionContext ctx, TestStatus testStatus) {
	    // Get the test method name
	    String testName = ctx.getDisplayName();
	    
	    // Get the test class
	    Class<?> testClass = ctx.getTestClass()
	        .orElseThrow(() -> new IllegalStateException("No test class"));
	    String testFileName = testClass.getSimpleName();
	
	    // Optionally get the file name (assuming standard naming and location)
		LoggingSingleton.setTestRunNumberAndStatus(testFileName, testName, testStatus);		
	}
	
	private void setTestRunNumberAndStatusHelper(ExtensionContext ctx, TestStatus testStatus, Throwable testCause) {
	    // Get the test method name
	    String testName = ctx.getDisplayName();
	    
	    // Get the test class
	    Class<?> testClass = ctx.getTestClass()
	        .orElseThrow(() -> new IllegalStateException("No test class"));
	    String testFileName = testClass.getSimpleName();
	
	    // Optionally get the file name (assuming standard naming and location)
		LoggingSingleton.setTestRunNumberAndStatus(testFileName, testName, testStatus, testCause.toString());		
	}

    //================================================================================
    // Former State Class
    //================================================================================

    
        private LoggingSingleton logger;
        
        //================================================================================
        // Init Method
        //================================================================================

        void initLogger() throws IOException {
    		Path filesDir = Paths.get(filepath);
    	    Path tarPath  = Paths.get(filepath, "run.tar");
    	    try {
    	    untarFile(filesDir, tarPath);
		    	Path testRunInfoPath = Paths.get(filepath+testRunInfoFilename);
		    	if (Files.notExists(testRunInfoPath)) {
					Files.copy(
							Paths.get(filepath+startTestRunInfoFilename),
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
        // Properties
        //================================================================================

    	static private final String tempFilepath = "src/testSupport/temp/";
    	static private final String filepath = "src/testSupport/";
    	static private final String testRunInfoFilename = "testRunInfo.json";
    	static private final String startTestRunInfoFilename  = "startTestRunInfo.json";
    	static private final String diffsTarFilename = "diffs.tar";
    	static private final String diffsTarZipFilename = "diffs.tar.zip";
    	
    	//================================================================================
        // File Utilities
        //================================================================================
    	
    	private boolean baselineExists(String baselineFilePath) {
    		return Files.exists(Paths.get(baselineFilePath));
    	}
    	
    	private List<String> readContents(String path) throws IOException {
    	    Path file = Path.of(path);
    	    String content  = Files.readString(file, StandardCharsets.UTF_8);  // Java 11+

    	    // Split into lines
    	    return Arrays.asList(content.split("\\r?\\n"));
    	}
    	
    	private void writeContents(String toWritePath, String fileName, String toWrite) throws IOException {
    	    	Path file = Path.of(toWritePath);
    	        // `StandardOpenOption.CREATE` replaces the file if it already exists.
    	        Files.writeString(
    	                file,
    	                toWrite,
    	                StandardCharsets.UTF_8,
    	                StandardOpenOption.CREATE,
    	                StandardOpenOption.TRUNCATE_EXISTING   // overwrite
    	        );
    	}
    	
    	private void createDirectoriesIfNotCreated(String toCreatePath) {
    		// Create output file/directories if it doesn't exist
    		File myObj = new File(toCreatePath);
            File parentDir = myObj.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs(); // Creates all necessary subdirs
            }
    	}

    	private void createDirectoriesIfNotCreated(String toCreatePath,boolean createDummyFile) throws IOException {
    		createDirectoriesIfNotCreated(toCreatePath);
            if (createDummyFile) {
            	createDummyFileInDirectory(toCreatePath);
            }
    	}
    	
    	private void createDummyFileInDirectory(String path) throws IOException {
            Path dir  = Paths.get(path);     // the directory
            Path file = dir.resolve("dummy.txt");           // dir + filename

                /* 1. Make sure the directory (and any parents) exist. */
                Files.createDirectories(dir);                // succeeds silently if already there

                /* 2. Actually create the file. 
                       - CREATE_NEW  → fail if file exists
                       - CREATE      → create if missing, else truncate (use APPEND to keep contents) */
                if (!Files.exists(file)) {
                	Files.createFile(file);                      // = CREATE_NEW
                }
    	}
    	
    	private void deletePath(String path) throws IOException {
    	    Path pathToBeDeleted = Paths.get(path);
    	    if (Files.exists(pathToBeDeleted)) {
    				Files.walk(pathToBeDeleted)
    				     .sorted(Comparator.reverseOrder())   // delete children before parents
    				     .forEach(p -> {
    				         try { Files.delete(p); }
    				         catch (IOException e) { throw new UncheckedIOException(e); }
    				     });
    	    }
    	}
    	 
    	// note that this is gets rid of the outermost folder surrounding the tar
    	private void untarFile(Path targetPath, Path tarPath) {
    		if (Files.notExists(tarPath)) {
    			return;
    		}
    	    try (InputStream fIn  = Files.newInputStream(tarPath);
    	         BufferedInputStream bIn = new BufferedInputStream(fIn);
    	         TarArchiveInputStream tIn = new TarArchiveInputStream(bIn)) {

    	        TarArchiveEntry entry;
    	        while ((entry = tIn.getNextTarEntry()) != null) {

    	            Path outPath = targetPath.resolve(entry.getName()).normalize();

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
    	
    	
    	
    	//================================================================================
        // Helpers
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
    	
    	/**
    	 * Maps 0–61 → ['0'–'9', 'A'–'Z', 'a'–'z'].
    	 * Assumes idx has already been reduced with idx = Math.floorMod(x, 62).
    	 */
    	private char idxToAlphanum(int idx) {
    	    // first block: digits 0–9  (10 chars)
    	    if (idx < 10) {
    	        return (char) ('0' + idx);
    	    }
    	    idx -= 10;

    	    // second block: uppercase A–Z (26 chars)
    	    if (idx < 26) {
    	        return (char) ('A' + idx);
    	    }
    	    idx -= 26;

    	    // third block: lowercase a–z (26 chars)
    	    return (char) ('a' + idx);      // idx now 0-25
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
    	
    	private void addDiffedFile(String fileName, String packageName, Path revisedPath, String sourcePath, 
    			int testRunNumber, int seed, boolean encryptDiffs) throws DiffException, IOException {
    		// Create this path if it didn't exist: testSupport/diffs/patches/filename
    		String toWriteName = fileName + "_" + testRunNumber;
    		String toWritePath = filepath + "diffs/patches/" + packageName + "." + toWriteName;
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
    				return;
    			}
    			
    			createDirectoriesIfNotCreated(toWritePath);

    			String diffString = buildDiffOutputString(deltas);
    			writeContents(toWritePath,toWriteName+".java",diffString);
    	}
    		
    	private void unzipAndUntarDiffs() {
    		Path zipPath     = Paths.get(filepath, diffsTarZipFilename);
    		Path diffsDir = Paths.get(filepath).resolve("diffs");
    		if (Files.notExists(zipPath)) {
    			return;
    		}
    		try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipPath))) {
    		    ZipEntry zentry = zis.getNextEntry();               // there is only one
    		    if (zentry != null) {
//    		        Files.copy(zis, extractedTar, StandardCopyOption.REPLACE_EXISTING);
    		        TarArchiveInputStream tis = new TarArchiveInputStream(new BufferedInputStream(zis));
    		        TarArchiveEntry entry;
    		        while ((entry = tis.getNextTarEntry()) != null) {

    		            Path outPath = diffsDir.resolve(entry.getName()).normalize();

    		            /* Security guard: prevent "../../etc/passwd"–style entries
    		             * from escaping the intended extraction root.
    		             */
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
    		                    IOUtils.copy(tis, o);         // stream file bytes
    		                }
    		                // Preserve timestamp; add other metadata here if you like
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
            Path sourceFolder = Paths.get("src/");

    			Files.walkFileTree(sourceFolder, new SimpleFileVisitor<Path>() {
    			    @Override
    			    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
    			        String fileName = file.getFileName().toString();
    		            String fileNameNoJava = fileName.substring(0,fileName.length()-".java".length());
    		            
    			    	if (fileName.endsWith(".java")) {
    			            // We get what's past sourceFolder to String length in the file path, and then separate out the file name at the end

    			    		String sourceFolderPath = Paths.get("src/").toString();
    			            String packageAndFileName = file.toString().substring(sourceFolderPath.length()+1);
    			            String packageName = packageAndFileName.substring(0,packageAndFileName.length()-fileName.length()-1);
    			            String baselineFilePath = filepath + "diffs/baselines/" + packageName.replace('/', '.') + "." + fileNameNoJava;

    			            if (baselineExists(baselineFilePath)) { // the file already exists, diff it
//    			            	System.out.println("Baseline exists");
    			            	try {
									addDiffedFile(fileNameNoJava, packageName, file, baselineFilePath, testRunNumber, seed, encryptDiffs);
	    			    	    } catch (DiffException e) { 
	    			    	    	throw new UncheckedIOException("DiffException: "+e.getMessage(), null);
	    			    	    } catch (IOException e) { 
	    			    	    	throw new UncheckedIOException(e);
	    			    	    }
    			            } else { // copy it in
//    			            	System.out.println("No baseline exists");
    			                try {
    			                	String sourceContents = new String(Files.readAllBytes(file));
    			                	if (encryptDiffs) {
    			                		sourceContents = encryptString(sourceContents, seed); // one-way encryption
    			                	}
    			        			createDirectoriesIfNotCreated(baselineFilePath);
    			                	writeContents(baselineFilePath, fileName, sourceContents);
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
    	}

    	private void tarAndZipDiffs() throws IOException {
			Files.createDirectories(Paths.get(tempFilepath));

			tarDiffs();
    		Path targetTar = Paths.get(tempFilepath, diffsTarFilename);
    	    Path zipPath  = Paths.get(tempFilepath).resolve(diffsTarZipFilename); // final product
    	    // create or overwrite the .zip
    	    try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zipPath, 
	                StandardOpenOption.CREATE,
	                StandardOpenOption.TRUNCATE_EXISTING));
    	         InputStream in = Files.newInputStream(targetTar)) {

    	        // 1. create an entry – the name inside the zip (no parent dirs!)
    	        ZipEntry entry = new ZipEntry("diffs");
    	        zos.putNextEntry(entry);

    	        // 2. copy the file’s bytes into the entry
    	        byte[] buffer = new byte[8_192];
    	        int bytesRead;
    	        while ((bytesRead = in.read(buffer)) != -1) {
    	            zos.write(buffer, 0, bytesRead);
    	        }

    	        // 3. close the single entry (zos.close() will also do it implicitly)
    	        zos.closeEntry();
    	    } catch (IOException e) { 
    	    	throw new UncheckedIOException(e);
    	    }
	    }
    	
    	private void tarDiffs() {
    		Path diffsDir = Paths.get(filepath).resolve("diffs");
    		Path targetTar = Paths.get(tempFilepath, diffsTarFilename);

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
    	
    	private static void atomicallySaveTempFiles() {
    		Path targetTar = Paths.get(filepath, "run.tar");
    		Path tempTargetTar = Paths.get(tempFilepath, "run.tar");
    		List<String> tempFiles = new ArrayList<>();
    		tempFiles.add(testRunInfoFilename);
    		tempFiles.add(diffsTarZipFilename);
    		
    		try (OutputStream fOut = Files.newOutputStream(tempTargetTar); // no StandardOpenOption.CREATE; should fail
    		     BufferedOutputStream bOut = new BufferedOutputStream(fOut);
    		     TarArchiveOutputStream tOut = new TarArchiveOutputStream(bOut)) {

    		    tOut.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
    		    for (String file:tempFiles) {
    		    	Path p = Paths.get(tempFilepath + file);

    	                 TarArchiveEntry entry = new TarArchiveEntry(p.toFile(), file);
    	                 tOut.putArchiveEntry(entry);
    	                 Files.copy(p, tOut);
    	                 tOut.closeArchiveEntry();
    		    }
    		    
    		    Files.move(tempTargetTar,targetTar,
    		            StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    	    } catch (IOException e) { 
    	    	throw new UncheckedIOException(e);
    	    }
		}

    	private static void saveTestRunInfo(ObjectMapper objectMapper, JsonNode testRunInfo) throws StreamWriteException, DatabindException, IOException {
    		File testRunInfoFile = new File(tempFilepath + testRunInfoFilename);
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(testRunInfoFile, testRunInfo);
    	}
    
}
