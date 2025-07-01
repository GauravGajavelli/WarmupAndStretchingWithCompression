package testSupport;

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
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
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
import org.junit.jupiter.api.extension.ExtensionContext;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.difflib.DiffUtils;
import com.github.difflib.algorithm.DiffException;
import com.github.difflib.patch.AbstractDelta;
import com.github.difflib.patch.Patch;

public class AfterLoggingExtension implements AfterAllCallback {

	static private final String filepath = "src/testSupport/";
	static private final String csvFilename = "supportData.csv";
	static private final String testRunInfoFilename = "testRunInfo.json";
	static private final String compressionSuffix = ".zip";
	
	static boolean diffsWritten = false;
	
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
	
	private List<String> readCompressedContents(String path) throws IOException {
	    ByteArrayOutputStream baos = new ByteArrayOutputStream();
	    
	    try (
	        FileInputStream fis = new FileInputStream(Paths.get(path).toFile());
	        BufferedInputStream bis = new BufferedInputStream(fis);
	        ZipInputStream zis = new ZipInputStream(bis)
	    ) {
	        // Get the first (and only) ZIP entry
	        ZipEntry entry = zis.getNextEntry();
	        if (entry == null) {
	            // No entries found in the ZIP, return an empty list
	            return Collections.emptyList();
	        }

	        // Read the ZIP entry content
	        byte[] buffer = new byte[8192];
	        int bytesRead;
	        while ((bytesRead = zis.read(buffer)) != -1) {
	            baos.write(buffer, 0, bytesRead);
	        }

	        // Close out this entry
	        zis.closeEntry();
	    }

	    // Convert the decompressed bytes to a UTF-8 string
	    String content = new String(baos.toByteArray(), StandardCharsets.UTF_8);

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
	
	private void addDiffedFile(String fileName, String packageName, Path revisedPath, String sourcePath, int testRunNumber) {
		// Create this path if it didn't exist: testSupport/diffs/patches/filename
		String toWriteName = fileName + "_" + testRunNumber;
		String toWritePath = filepath + "diffs/patches/" + packageName + "." + toWriteName;
		try {
			
			// Read in the files
	        List<String> original = readCompressedContents(sourcePath);
	        List<String> revised = 	Files.readAllLines(revisedPath);
//	        System.out.println("source: "+sourcePath);
//	        System.out.println("revised: "+revisedPath);
	
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
	
	private boolean baselineExists(String baselineFilePath) {
//		baselineFilePath += compressionSuffix;
		
		return Files.exists(Paths.get(baselineFilePath));
	}

	private void tarDiffs(int testRunNumber) {
		Path diffsDir = Paths.get(filepath).resolve("diffs");
		Path targetTar = Paths.get(filepath, "diffs.tar");

		try (OutputStream fOut = Files.newOutputStream(targetTar);
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
	
	private void deleteDiffs(int testRunNumber) {
	    Path pathToBeDeleted = Paths.get(filepath).resolve("diffs");
	    if (Files.exists(pathToBeDeleted)) {
	        try {
				Files.walk(pathToBeDeleted)
				     .sorted(Comparator.reverseOrder())   // delete children before parents
				     .forEach(p -> {
				         try { Files.delete(p); }
				         catch (IOException e) { throw new UncheckedIOException(e); }
				     });
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
	    }
	}
	
	private void untarDiffs(int testRunNumber) {
		Path diffsDir = Paths.get(filepath).resolve("diffs");
	    Path tarPath  = Paths.get(filepath, "diffs.tar");
	    boolean empty = true;

	    try (InputStream fIn  = Files.newInputStream(tarPath);
	         BufferedInputStream bIn = new BufferedInputStream(fIn);
	         TarArchiveInputStream tIn = new TarArchiveInputStream(bIn)) {

	        TarArchiveEntry entry;
	        while ((entry = tIn.getNextTarEntry()) != null) {
	        	
	        	empty = false;

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
	                try (OutputStream o = Files.newOutputStream(outPath)) {
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
	    
        if (empty) {
            File diffsDirectory = new File(filepath+"diffs");
            diffsDirectory.mkdir();
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
			            	addDiffedFile(fileNameNoJava, packageName, file, baselineFilePath, testRunNumber);
			            } else { // copy it in
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


	private void exportResults(String outputString) {
		try {
			// Create file only if it doesn't exist
			File myObj = new File(filepath + csvFilename);
			myObj.createNewFile();

			try {
				FileOutputStream fos = new FileOutputStream(filepath + csvFilename, true);
				outputString += "\n";
				fos.write(outputString.getBytes());
				fos.close();
			} catch (Exception e) {
				System.err.println("Could open/write/close supportData.csv file - Ignore this error");
			}
		} catch (IOException e) {
			System.err.println("Could not create supportData.csv file - Ignore this error");
		}
	}
	
	private void saveTestRunInfo(ObjectMapper objectMapper, JsonNode testRunInfo) {
     try {
		File testRunInfoFile = new File(filepath + testRunInfoFilename);
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(testRunInfoFile, testRunInfo);
		} catch (IOException e) {
			System.err.println("Could not create supportData.csv file - Ignore this error");
		}
	}
	
	@Override
	public void afterAll(ExtensionContext arg0) throws Exception {
		if (!diffsWritten) {
			int currentTestRunNumber = LoggingSingleton.getCurrentTestRunNumber();
			untarDiffs(currentTestRunNumber);
			writeDiffs(currentTestRunNumber);
			tarDiffs(currentTestRunNumber);
			deleteDiffs(currentTestRunNumber);
			diffsWritten = true;
		}
		
		LoggingSingleton.addTimeStamp();
		if (LoggingSingleton.isOperationSupported()) {
			exportResults(LoggingSingleton.getFullMessage());
		}
		
		saveTestRunInfo(LoggingSingleton.getObjectMapper(), LoggingSingleton.getTestRunInfo());
	}

}
