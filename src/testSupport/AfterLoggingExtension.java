package testSupport;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.difflib.DiffUtils;
import com.github.difflib.algorithm.DiffException;
import com.github.difflib.patch.AbstractDelta;
import com.github.difflib.patch.Patch;

public class AfterLoggingExtension implements AfterAllCallback {

	static private final String filepath = "src/testSupport/";
	static private final String csvFilename = "supportData.csv";
	static private final String testRunInfoFilename = "testRunInfo.json";
	
	static boolean diffsWritten = false;
	
	private String buildDiffOutputString(List<AbstractDelta<String>> deltas, int testRunNumber) {
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
	
	private void addDiffedFile(String fileName, String packageName, Path revisedPath, Path sourcePath, int testRunNumber) {
		// Create this path if it didn't exist: testSupport/diffs/patches/filename
		String toWritePath = filepath + "diffs/patches/" + packageName + "." + fileName + "_" + testRunNumber;
		File myObj = new File(toWritePath);
		try {
			// Read in the files
	        List<String> original = Files.readAllLines(sourcePath);
	        List<String> revised = Files.readAllLines(revisedPath);
	
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
			
			// Create output file/directories if it doesn't exist
	        File parentDir = myObj.getParentFile();
	        if (parentDir != null && !parentDir.exists()) {
	            parentDir.mkdirs(); // Creates all necessary subdirs
	        }
			myObj.createNewFile();
	        
			FileOutputStream fos = new FileOutputStream(toWritePath, true);
			fos.write(buildDiffOutputString(deltas, testRunNumber).getBytes());
			fos.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
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
			    	if (fileName.endsWith(".java")) {

			            // We get what's past sourceFolder to String length in the file path, and then separate out the file name at the end
			            String sourceFolderPath = Paths.get("src/").toString();
			            String packageAndFileName = file.toString().substring(sourceFolderPath.length()+1);
			            String packageName = packageAndFileName.substring(0,packageAndFileName.length()-fileName.length()-1);
			            String fileNameNoJava = fileName.substring(0,fileName.length()-".java".length());
			            Path baselineFilePath = Paths.get(filepath + "diffs/baselines/" + packageName.replace('/', '.') + "." + fileNameNoJava);
			            
			            if (Files.exists(baselineFilePath)) { // the file already exists, diff it
			            	addDiffedFile(fileNameNoJava, packageName, file, baselineFilePath, testRunNumber);
			            } else {
			                try {
								Files.copy(file, baselineFilePath);
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

	private void exportResults(String outputString, ObjectMapper objectMapper, JsonNode testRunInfo) {
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
			exportResults(LoggingSingleton.getFullMessage(),
						  LoggingSingleton.getObjectMapper(),
						  LoggingSingleton.getTestRunInfo());
		}
	}

}
