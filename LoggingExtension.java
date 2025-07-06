package testSupport;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext.Namespace;
import org.junit.jupiter.api.extension.TestWatcher;
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
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.utils.IOUtils;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

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
    	if (!storeInitialized) {
	        State global = (State) ctx.getRoot().getStore(NS)
	           .getOrComputeIfAbsent(State.class, k -> new State());      // ← creates once
	        global.initLogger();
	
			// Initialize the static fields in the singleton
			String testClassName = ctx.getDisplayName();
	    	LoggingSingleton.incrementRunNumber();
	    	LoggingSingleton.addRunTime();
	    	
	    	storeInitialized = true;
    	}
		
		Class<?> testClass = ctx.getTestClass().orElseThrow();
        String testFileName = testClass.getSimpleName();
        String packageName = testClass.getPackageName();
        
        System.out.println("CurrentTestFilePath: "+testFileName+", "+packageName);
        
        LoggingSingleton.setCurrentTestFilePath(testFileName, packageName);
	}

    //================================================================================
    // TestWatcher Variables and Methods
    //================================================================================
	
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
	
    public void testDisabled(ExtensionContext c) { 
    	
    }
	
    //================================================================================
    // State Class
    //================================================================================
	
	private static State state(ExtensionContext c) {
        return c.getRoot().getStore(NS).get(State.class, State.class);
    }

    /* closed automatically even when the container is aborted */
    static final class State implements ExtensionContext.Store.CloseableResource {
        private LoggingSingleton logger;
        
        //================================================================================
        // Init Method
        //================================================================================

        void initLogger() {
        	System.out.println("A: Should only run once");
    		Path filesDir = Paths.get(filepath);
    	    Path tarPath  = Paths.get(filepath, "run.tar");

    	    untarFile(filesDir, tarPath);
    	    
            logger = LoggingSingleton.getInstance();
        }
        
        //================================================================================
        // Properties
        //================================================================================

    	static private final String tempFilepath = "src/testSupport/temp/";
//    	static private final String prevRunDataFilepath = "src/testSupport/run/";
    	static private final String filepath = "src/testSupport/";
    	static private final String testRunInfoFilename = "testRunInfo.json";
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
    	
    	private void writeContents(String toWritePath, String fileName, String toWrite) {
    	    try {
    	    	Path file = Path.of(toWritePath);
    	        // `StandardOpenOption.CREATE` replaces the file if it already exists.
    	        Files.writeString(
    	                file,
    	                toWrite,
    	                StandardCharsets.UTF_8,
    	                StandardOpenOption.CREATE,
    	                StandardOpenOption.TRUNCATE_EXISTING   // overwrite
    	        );
    	    } catch (IOException e) {
    	        e.printStackTrace();
    	    }
    	}
    	
    	private void createDirectoriesIfNotCreated(String toCreatePath) {
    		// Create output file/directories if it doesn't exist
    		File myObj = new File(toCreatePath);
            File parentDir = myObj.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs(); // Creates all necessary subdirs
            }
    	}

    	private void createDirectoriesIfNotCreated(String toCreatePath,boolean createDummyFile) {
    		createDirectoriesIfNotCreated(toCreatePath);
            if (createDummyFile) {
            	createDummyFileInDirectory(toCreatePath);
            }
    	}
    	
    	private void createDummyFileInDirectory(String path) {
            Path dir  = Paths.get(path);     // the directory
            Path file = dir.resolve("dummy.txt");           // dir + filename

            try {
                /* 1. Make sure the directory (and any parents) exist. */
                Files.createDirectories(dir);                // succeeds silently if already there

                /* 2. Actually create the file. 
                       - CREATE_NEW  → fail if file exists
                       - CREATE      → create if missing, else truncate (use APPEND to keep contents) */
                if (!Files.exists(file)) {
                	Files.createFile(file);                      // = CREATE_NEW
                }
            } catch (IOException e) {
            	e.printStackTrace();
            }
    	}
    	
    	private void deletePath(String path) {
    	    Path pathToBeDeleted = Paths.get(path);
    	    if (Files.exists(pathToBeDeleted)) {
    	        try {
    				Files.walk(pathToBeDeleted)
    				     .sorted(Comparator.reverseOrder())   // delete children before parents
    				     .forEach(p -> {
    				         try { Files.delete(p); }
    				         catch (IOException e) { throw new UncheckedIOException(e); }
    				     });
    			} catch (IOException e) {
    				e.printStackTrace();
    			}
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
    	                try (OutputStream o = Files.newOutputStream(outPath, StandardOpenOption.CREATE)) {
    	                    IOUtils.copy(tIn, o);         // stream file bytes
    	                }
    	                // Preserve timestamp; add other metadata here if you like
    	                FileTime mtime = FileTime.fromMillis(entry.getModTime().getTime());
    	                Files.setLastModifiedTime(outPath, mtime);
    	            }
    	        }
    	    } catch (IOException e) {
    	        e.printStackTrace();
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
    	
    	private void addDiffedFile(String fileName, String packageName, Path revisedPath, String sourcePath, int testRunNumber) {
    		// Create this path if it didn't exist: testSupport/diffs/patches/filename
    		String toWriteName = fileName + "_" + testRunNumber;
    		String toWritePath = filepath + "diffs/patches/" + packageName + "." + toWriteName;
    		try {
    			
    			// Read in the files
    	        List<String> original = readContents(sourcePath);
    	        List<String> revised = 	Files.readAllLines(revisedPath);
//    	        System.out.println("source: "+sourcePath);
//    	        System.out.println("revised: "+revisedPath);
    	
    	        // Compute the diff: original -> revised
    	        Patch<String> patch = new Patch<>();
    			try {
    				patch = DiffUtils.diff(original, revised);
    			} catch (DiffException e) {
    				e.printStackTrace();
    			}
    			
    			// Write diffs
    			List<AbstractDelta<String>> deltas = patch.getDeltas();
    			if (deltas == null || deltas.size() == 0) {
    				return;
    			}
    			
    			createDirectoriesIfNotCreated(toWritePath);

    			String diffString = buildDiffOutputString(deltas);
    			writeContents(toWritePath,toWriteName+".java",diffString);
    		} catch (IOException e) {
    			// TODO Auto-generated catch block
    			e.printStackTrace();
    		}
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
    		                try (OutputStream o = Files.newOutputStream(outPath, StandardOpenOption.CREATE)) {
    		                    IOUtils.copy(tis, o);         // stream file bytes
    		                }
    		                // Preserve timestamp; add other metadata here if you like
    		                FileTime mtime = FileTime.fromMillis(entry.getModTime().getTime());
    		                Files.setLastModifiedTime(outPath, mtime);
    		            }
    		        }
    		    }
    	    } catch (IOException e) {
    	        e.printStackTrace();
    	    }
    	}
    	
    	private void writeDiffs(int testRunNumber) {
            Path sourceFolder = Paths.get("src/");
            
            try {
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
    			            	addDiffedFile(fileNameNoJava, packageName, file, baselineFilePath, testRunNumber);
    			            } else { // copy it in
//    			            	System.out.println("No baseline exists");
    			                try {
    			                	String sourceContents = new String(Files.readAllBytes(file));
    			        			createDirectoriesIfNotCreated(baselineFilePath);
    			                	writeContents(baselineFilePath, fileName, sourceContents);
    							} catch (IOException e) {
    								// TODO Auto-generated catch block
    								e.printStackTrace();
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
    		} catch (IOException e) {
    			// TODO Auto-generated catch block
    			e.printStackTrace();
    		}
    	}

    	private void tarAndZipDiffs() {
			try {
				Files.createDirectories(Paths.get(tempFilepath));
			} catch (IOException e) {
				e.printStackTrace();
			}

			tarDiffs();
    		Path targetTar = Paths.get(tempFilepath, diffsTarFilename);
    	    Path zipPath  = Paths.get(tempFilepath).resolve(diffsTarZipFilename); // final product
    	    // create or overwrite the .zip
    	    try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zipPath, StandardOpenOption.CREATE));
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
    			e.printStackTrace();
    		}
    	}
    	
    	private void tarDiffs() {
    		Path diffsDir = Paths.get(filepath).resolve("diffs");
    		Path targetTar = Paths.get(tempFilepath, diffsTarFilename);

    		try (OutputStream fOut = Files.newOutputStream(targetTar, StandardOpenOption.CREATE);
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
    			// TODO Auto-generated catch block
    			e.printStackTrace();
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
    	             try {
    	                 TarArchiveEntry entry = new TarArchiveEntry(p.toFile(), file);
    	                 tOut.putArchiveEntry(entry);
    	                 Files.copy(p, tOut);
    	                 tOut.closeArchiveEntry();
    	             } catch (IOException ex) {
    	                 throw new UncheckedIOException(ex);
    	             }
    		    }
    		    
    		    Files.move(tempTargetTar,targetTar,
    		            StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    		} catch (IOException e) {
    			// TODO Auto-generated catch block
    			e.printStackTrace();
    		}
    	}

    	private static void saveTestRunInfo(ObjectMapper objectMapper, JsonNode testRunInfo) {
         try {
    		File testRunInfoFile = new File(tempFilepath + testRunInfoFilename);
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(testRunInfoFile, testRunInfo);
    		} catch (IOException e) {
    			e.printStackTrace();
    		}
    	}

        @Override public void close() {
        	System.out.println("B: Should only run once");
			int currentTestRunNumber = logger.getCurrentTestRunNumber();
			unzipAndUntarDiffs();
			writeDiffs(currentTestRunNumber);
			tarAndZipDiffs();
			
			// Delete loaded files
			deletePath(filepath+"diffs");
			try {
				Files.delete(Paths.get(filepath+testRunInfoFilename));
				Files.delete(Paths.get(filepath+diffsTarZipFilename));
			} catch (IOException e) {
				e.printStackTrace();
			}
    		
			saveTestRunInfo(logger.getObjectMapper(), logger.getTestRunInfo());
			atomicallySaveTempFiles();

			// Delete intermediates
			deletePath(tempFilepath);
        }
    }
}
