package testSupport;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

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

	private void writeCompressedContents(String toWritePath, String fileName, String toWrite) {
	    try (
	        // Create or overwrite the file
	        FileOutputStream fos = new FileOutputStream(toWritePath, false);
	        BufferedOutputStream bos = new BufferedOutputStream(fos);
	        ZipOutputStream zos = new ZipOutputStream(bos)
	    ) {
	        // Add a single entry (file) to the ZIP
	        ZipEntry entry = new ZipEntry(fileName);
	        zos.putNextEntry(entry);

	        // Write data for this entry
	        zos.write(toWrite.getBytes(StandardCharsets.UTF_8));

	        // Close the entry
	        zos.closeEntry();

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
		String toWritePath = filepath + "diffs/patches/" + packageName + "." + toWriteName + compressionSuffix;
		try {
			
			// Read in the files
	        List<String> original = readCompressedContents(sourcePath + compressionSuffix);
	        List<String> revised = 	Files.readAllLines(revisedPath);
	
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
			writeCompressedContents(toWritePath,toWriteName+".java",diffString);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	private boolean baselineExists(String baselineFilePath) {
		baselineFilePath += compressionSuffix;
		
		return Files.exists(Paths.get(baselineFilePath));
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
			                	writeCompressedContents(baselineFilePath + compressionSuffix, fileName, sourceContents);
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
			writeDiffs(LoggingSingleton.getCurrentTestRunNumber());
			diffsWritten = true;
		}
		
		LoggingSingleton.addTimeStamp();
		if (LoggingSingleton.isOperationSupported()) {
			exportResults(LoggingSingleton.getFullMessage());
		}
		
		saveTestRunInfo(LoggingSingleton.getObjectMapper(), LoggingSingleton.getTestRunInfo());
	}

}
