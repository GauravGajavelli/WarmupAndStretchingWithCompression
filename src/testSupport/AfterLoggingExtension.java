package testSupport;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class AfterLoggingExtension implements AfterAllCallback {

	static private final String filepath = "src/testSupport/";
	static private final String csvFilename = "supportData.csv";
	static private final String testRunInfoFilename = "testRunInfo.json";

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
		LoggingSingleton.addTimeStamp();
		if (LoggingSingleton.isOperationSupported()) {
			exportResults(LoggingSingleton.getFullMessage(), LoggingSingleton.getObjectMapper(), LoggingSingleton.getTestRunInfo());
		}
	}

}
