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
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
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

public class LoggingExtension implements TestWatcher, BeforeAllCallback, BeforeEachCallback,
		ExtensionContext.Store.CloseableResource {

	// Namespace for storing this extension in JUnit's ExtensionContext
	private static final ExtensionContext.Namespace NAMESPACE =
			ExtensionContext.Namespace.create(LoggingExtension.class);

	//================================================================================
	// Fields
	//================================================================================

	private LoggingSingleton logger;
	static private boolean loggerInitialized = false;
	static private Path tempDirectory;
	static private Map<Path, List<String>> inMemoryBaselines;  // Source files captured at test start time
	static private Map<Path, String> inMemoryBaselineBytes;  // new String(Files.readAllBytes(file))
	static private boolean loggedShutdownReason = false;  // Ensures shutdown reason is logged only once

	final static String testRunInfoFilename = "testRunInfo.json";
	final static String startTestRunInfoFilename  = "startTestRunInfo.json";
	final static String errorLogFilename = "error-logs.txt";
	final static String finalTarFilename = "run.tar";
	final static String backupTarPrefix = "run.tar.bak.";  // followed by run numbers separated by -
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

			// Initialize tempDirectory early so logError() can work if exceptions occur
			if (tempDirectory == null) {
				initTempDirectory();
			}
			
			long repoSize = getRepoFilesSize();
			boolean repoTooBig = repoSize > MAX_REPO_SIZE;
			boolean tarIsTooBig = tarTooBig();
			if (repoTooBig || tarIsTooBig) {
				if (repoTooBig) {
					logShutdownReason("Repo size (" + repoSize + " bytes) exceeds MAX_REPO_SIZE (" + MAX_REPO_SIZE + " bytes)");
				} else {
					logShutdownReason("Tar size exceeds MAX_TAR_SIZE (" + MAX_TAR_SIZE + " bytes)");
				}
				return;
			}

			if (!loggerInitialized) {
				long initStart = System.nanoTime();

				initDirectories();

				// Capture source files into memory before tests run
				// This ensures code snapshots match the actual tested code
				captureSourceFilesInMemory();

				File testRunInfoFile = filepathResolve(tempDirectory)
						.resolve(testRunInfoFilename).toFile();
				logger = LoggingSingleton.getInstance(testRunInfoFile);

				// Register with JUnit's lifecycle instead of JVM shutdown hook
				// This ensures close() runs at the right time, before JVM shutdown begins
				// The store will call close() when the root context is closed (after all tests)
				ctx.getRoot().getStore(NAMESPACE).put("logger", this);

				loggerInitialized = true;
				LoggingSingleton.setBeforeAllInitDurationMs(
						TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - initStart));
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
			LoggingSingleton.resetAccumulatedTime();

			setUpAndCheckTiming(SYNC_MAX_TIME);

			boolean skip = LoggingSingleton.getSkipLogging();
			boolean strikes = LoggingSingleton.tooManyStrikes();
			if (skip || strikes) {
				if (skip) {
					logShutdownReason("skipLogging flag is set");
				} else {
					logShutdownReason("Too many timing strikes accumulated");
				}
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

			boolean skip = LoggingSingleton.getSkipLogging();
			boolean strikes = LoggingSingleton.tooManyStrikes();
			if (skip || strikes) {
				if (skip) {
					logShutdownReason("skipLogging flag is set");
				} else {
					logShutdownReason("Too many timing strikes accumulated");
				}
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

			boolean skip = LoggingSingleton.getSkipLogging();
			boolean strikes = LoggingSingleton.tooManyStrikes();
			if (skip || strikes) {
				if (skip) {
					logShutdownReason("skipLogging flag is set");
				} else {
					logShutdownReason("Too many timing strikes accumulated");
				}
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

			boolean skip = LoggingSingleton.getSkipLogging();
			boolean strikes = LoggingSingleton.tooManyStrikes();
			if (skip || strikes) {
				if (skip) {
					logShutdownReason("skipLogging flag is set");
				} else {
					logShutdownReason("Too many timing strikes accumulated");
				}
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

			boolean skip = LoggingSingleton.getSkipLogging();
			boolean strikes = LoggingSingleton.tooManyStrikes();
			if (skip || strikes) {
				if (skip) {
					logShutdownReason("skipLogging flag is set");
				} else {
					logShutdownReason("Too many timing strikes accumulated");
				}
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
		long closeStart = System.nanoTime();
		// Track errors but continue processing - don't let one failure kill everything
		List<String> errors = new ArrayList<>();

		try {
			setUpAndCheckTiming(ASYNC_MAX_TIME);
			if (LoggingSingleton.getSkipLogging()) {
				logShutdownReason("skipLogging flag is set (in close)");
				return;
			} else if (LoggingSingleton.tooManyStrikes()) {
				logShutdownReason("Too many timing strikes accumulated (in close)");
				LoggingSingleton.setSkipLogging(true);
			}

			int currentTestRunNumber = logger.getCurrentTestRunNumber();
			int seed = logger.getSeed();
			boolean redactDiffs = logger.getRedactDiffs();

			// Each operation is independent - failures don't cascade
			errors.addAll(timedSafeExecute("unzipAndUntarDiffs", () -> unzipAndUntarDiffs()));
			errors.addAll(timedSafeExecute("writeDiffs", () -> writeDiffs(currentTestRunNumber, seed, redactDiffs)));
			errors.addAll(timedSafeExecute("tarAndZipDiffs", () -> tarAndZipDiffs()));
			errors.addAll(timedSafeExecute("addPriorRebaslinedDiffs", () -> addPriorRebaslinedDiffs()));

			// Copy error logs - use copy instead of move for safety
			errors.addAll(timedSafeExecute("copyErrorLogs", () -> {
				Path oldErrorLogsFilePath = filepathResolve(tempDirectory).resolve(errorLogFilename);
				Path newErrorLogsFilePath = tempFilepathResolve(tempDirectory).resolve(errorLogFilename);
				if (Files.exists(oldErrorLogsFilePath)) {
					Files.copy(oldErrorLogsFilePath, newErrorLogsFilePath,
							StandardCopyOption.REPLACE_EXISTING);
				} else {
					Files.createDirectories(newErrorLogsFilePath.getParent());
					Files.createFile(newErrorLogsFilePath);
				}
			}));

			accumulateAndCheckTiming(ASYNC_MAX_TIME);

			// Write any accumulated errors to the error log before final save
			if (!errors.isEmpty()) {
				Path errorFilepath = tempFilepathResolve(tempDirectory).resolve(errorLogFilename);
				Files.write(errorFilepath, errors, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
			}

			// Log successful run number if no errors and no shutdown occurred
			// This allows identifying gaps where runs failed
			if (errors.isEmpty() && !loggedShutdownReason) {
				Path errorFilepath = tempFilepathResolve(tempDirectory).resolve(errorLogFilename);
				Files.write(errorFilepath, List.of(String.valueOf(currentTestRunNumber)),
						StandardOpenOption.CREATE, StandardOpenOption.APPEND);
			}

			// Record close duration before saving testRunInfo so it appears in the tar
			long closeDurationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - closeStart);
			LoggingSingleton.setCloseDurationMs(closeDurationMs);

			errors.addAll(safeExecute("saveTestRunInfo", () ->
					saveTestRunInfo(logger.getObjectMapper(), logger.getTestRunInfo())));

			errors.addAll(safeExecute("atomicallySaveTempFiles", () -> atomicallySaveTempFiles()));

			// Delete intermediates - failure here is non-critical
			safeExecute("cleanup", () -> {
				try (var walkStream = Files.walk(tempDirectory)) {
					walkStream
						.sorted(Comparator.reverseOrder())
						.map(Path::toFile)
						.forEach(File::delete);
				}
			});

		} catch (Throwable T) {
			// Last resort - try to save whatever we can
			try {
				Path errorFilepath = tempFilepathResolve(tempDirectory).resolve(errorLogFilename);
				Files.createDirectories(errorFilepath.getParent());
				errors.add("FATAL: " + T.getClass().getName() + ": " + T.getMessage());
				Files.write(errorFilepath, errors, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
				saveTestRunInfo(logger.getObjectMapper(), logger.getTestRunInfo());
				atomicallySaveTempFiles();
			} catch (Throwable ignored) {
				// Fail silently - no stderr output
			}
		}
	}

	@FunctionalInterface
	private interface ThrowingRunnable {
		void run() throws Exception;
	}

	private List<String> safeExecute(String operationName, ThrowingRunnable operation) {
		List<String> errors = new ArrayList<>();
		try {
			operation.run();
		} catch (Throwable t) {
			String error = "ERROR in " + operationName + ": " + t.getClass().getName() + ": " + t.getMessage();
			errors.add(error);
		}
		return errors;
	}

	private List<String> timedSafeExecute(String operationName, ThrowingRunnable operation) {
		long opStart = System.nanoTime();
		List<String> errors = safeExecute(operationName, operation);
		long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - opStart);
		try {
			LoggingSingleton.addCloseTiming(operationName, durationMs);
		} catch (Throwable ignored) {}
		return errors;
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
			Path tempTarPath = filepathResolve().resolve(finalTarFilename + ".tmp");

			// Clean up any interrupted temp file
			Files.deleteIfExists(tempTarPath);

			// Variables for recovery tracking (used after untarLogs)
			List<Integer> lostRuns = null;
			int maxLostRun = -1;

			// Check for backup file (indicates interrupted save)
			Path backupPath = findBackupFile();
			if (backupPath != null) {
				lostRuns = parseBackupRunNumbers(backupPath);
				maxLostRun = lostRuns.stream().mapToInt(Integer::intValue).max().orElse(-1);

				if (Files.notExists(tarPath)) {
					// Main tar missing - COPY from backup (keep backup for run number tracking)
					Files.copy(backupPath, tarPath, StandardCopyOption.REPLACE_EXISTING);

					// Log recovery with all interrupted run numbers
					Path errorLogPath = filepathResolve(tempDirectory).resolve(errorLogFilename);
					Files.createDirectories(errorLogPath.getParent());
					String msg = "RECOVERED: " + formatInterruptedRuns(lostRuns)
						+ " interrupted at " + LocalTime.now();
					Files.write(errorLogPath, List.of(msg),
						StandardOpenOption.CREATE, StandardOpenOption.APPEND);
				}
				// If tar exists, keep backup anyway - it tracks prior failed run numbers
				// The backup will be extended in atomicallySaveTempFiles() and deleted on success
			}

			untarLogs(filesDir, tarPath);

			// After untarring, update prevRunNumber to skip interrupted runs
			if (lostRuns != null && maxLostRun > 0) {
				Path testRunInfoPath = filepathResolve(tempDirectory).resolve(testRunInfoFilename);
				if (Files.exists(testRunInfoPath)) {
					updatePrevRunNumber(testRunInfoPath, maxLostRun);
				}
			}
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

	/**
	 * Captures source files into memory at test start time.
	 * This ensures diffs are computed against the code that was actually tested,
	 * not code that may have been modified during test execution.
	 * Integrates with the initial size check to ensure excessively large files
	 * are never loaded into memory.
	 */
	private void captureSourceFilesInMemory() {
		inMemoryBaselines = new HashMap<>();
		inMemoryBaselineBytes = new HashMap<>();
		Path sourceFolder = Paths.get(sourceFolderName);
		try {
			Files.walkFileTree(sourceFolder, new SimpleFileVisitor<Path>() {
				@Override
				public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
					if (dir.getFileName().toString().equals(testSupportPackageName)) {
						return FileVisitResult.SKIP_SUBTREE;
					}
					return FileVisitResult.CONTINUE;
				}
				@Override
				public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
					if (file.toString().endsWith(".java")) {
						// Use simple size check - LoggingSingleton isn't initialized yet
						if (!fileLargerThan(file, MAX_DIFFED_FILE_SIZE)) {
							inMemoryBaselines.put(file, Files.readAllLines(file, StandardCharsets.UTF_8));
							inMemoryBaselineBytes.put(file, new String(Files.readAllBytes(file)));
						}
					}
					return FileVisitResult.CONTINUE;
				}
			});
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
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

	private Path findBackupFile() throws IOException {
		Path testSupportDir = filepathResolve();
		if (Files.notExists(testSupportDir)) {
			return null;
		}
		try (var stream = Files.list(testSupportDir)) {
			return stream
				.filter(p -> p.getFileName().toString().startsWith(backupTarPrefix))
				.findFirst()
				.orElse(null);
		}
	}

	private List<Integer> parseBackupRunNumbers(Path backupPath) {
		List<Integer> runs = new ArrayList<>();
		String filename = backupPath.getFileName().toString();
		String numPart = filename.substring(backupTarPrefix.length());
		for (String s : numPart.split("-")) {
			try {
				runs.add(Integer.parseInt(s));
			} catch (NumberFormatException e) {
				// Skip unparseable parts
			}
		}
		return runs;
	}

	private String formatInterruptedRuns(List<Integer> runs) {
		if (runs.isEmpty()) return "unknown runs";
		if (runs.size() == 1) return "run #" + runs.get(0);
		int first = runs.get(0);
		int last = runs.get(runs.size() - 1);
		return "runs #" + first + "-" + last;
	}

	private void updatePrevRunNumber(Path testRunInfoPath, int newPrevRunNumber) throws IOException {
		// Read, modify, write testRunInfo.json to skip interrupted run numbers
		ObjectMapper mapper = new ObjectMapper();
		JsonNode root = mapper.readTree(testRunInfoPath.toFile());
		((com.fasterxml.jackson.databind.node.ObjectNode) root)
			.put("prevRunNumber", newPrevRunNumber);
		mapper.writerWithDefaultPrettyPrinter().writeValue(testRunInfoPath.toFile(), root);
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
		if (Files.notExists(path)) {
			return false;
		}
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
		toRet.append(deltas.size());
		toRet.append(";\n");

		for (AbstractDelta<String> delta : deltas) {
			toRet.append(delta.getType());
			toRet.append("\n");

			int srcPos = delta.getSource().getPosition();
			int tgtPos = delta.getTarget().getPosition();
			toRet.append(srcPos);
			toRet.append(",");
			toRet.append(tgtPos);
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
		Path tempDiffsFolder = filepathResolve(tempDirectory).resolve(diffsFolderName);
		Path srcRoot = Paths.get(sourceFolderName);

		// Iterate over captured files instead of walking filesystem
		// This ensures we only process files that were successfully captured in memory,
		// avoiding null pointer issues when path objects don't match between walks
		for (Map.Entry<Path, List<String>> entry : inMemoryBaselines.entrySet()) {
			Path file = entry.getKey();
			List<String> capturedLines = entry.getValue();

			String fileName = file.getFileName().toString();
			String fileNameNoJava = fileName.substring(0, fileName.length() - ".java".length());

			Path relative = srcRoot.relativize(file);
			Path packagePath = (relative.getParent() == null) ? relative : relative.getParent();
			String packageName = packagePath.toString().replace(File.separatorChar, '.');

			Path baselineFilePath = tempDiffsFolder.resolve("baselines")
					.resolve(packageName + "." + fileNameNoJava);

			if (Files.exists(baselineFilePath)) {
				// Diff against existing baseline
				try {
					addDiffedFile(fileNameNoJava, packageName, capturedLines,
							baselineFilePath, testRunNumber, seed, redactDiffs);
				} catch (DiffException e) {
					throw new UncheckedIOException("DiffException: " + e.getMessage(), null);
				}
			} else {
				// Create new baseline using captured bytes
				String sourceContents = inMemoryBaselineBytes.get(file);
				if (sourceContents != null) {
					if (redactDiffs) {
						sourceContents = redactString(sourceContents, seed);
					}
					Files.createDirectories(baselineFilePath.getParent());
					writeContents(baselineFilePath, fileName, sourceContents);

					// Write creation patch
					String toWriteName = fileNameNoJava + "_" + testRunNumber;
					Path toWritePath = tempDiffsFolder.resolve(patchesFolderName)
							.resolve(packageName + "." + toWriteName);
					Files.createDirectories(toWritePath.getParent());
					writeContents(toWritePath, toWriteName + ".java", "File created!");
				}
			}
		}

		// Rebaselining logic
		if (LoggingSingleton.isRebaselining()) {
			LoggingSingleton.updatePreviousBaselineRunNumber();
		}
		LoggingSingleton.setRebaselining(false);
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
						try {
							Files.move(p, newPriorRebaslinedDiffPath,
									StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
						} catch (AtomicMoveNotSupportedException e) {
							Files.copy(p, newPriorRebaslinedDiffPath, StandardCopyOption.REPLACE_EXISTING);
							Files.delete(p);
						}

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

		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}

		// Step 1: Create or extend backup with current run number
		try {
			int currentRunNumber = LoggingSingleton.getCurrentTestRunNumber();
			Path existingBackup = findBackupFile();
			Path backupPath;

			if (existingBackup != null) {
				// Use range format: first failed run to current run
				// e.g., run.tar.bak.42 -> run.tar.bak.42-43
				// e.g., run.tar.bak.42-43 -> run.tar.bak.42-44 (not 42-43-44)
				List<Integer> priorRuns = parseBackupRunNumbers(existingBackup);
				int firstRun = priorRuns.get(0);
				String newName = backupTarPrefix + firstRun + "-" + currentRunNumber;
				backupPath = existingBackup.resolveSibling(newName);
				try {
					Files.move(existingBackup, backupPath,
							StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
				} catch (AtomicMoveNotSupportedException e) {
					// Windows cross-filesystem fallback
					Files.copy(existingBackup, backupPath, StandardCopyOption.REPLACE_EXISTING);
					Files.delete(existingBackup);
				}
			} else if (Files.exists(targetTar)) {
				// First backup - copy existing tar
				backupPath = filepathResolve().resolve(backupTarPrefix + currentRunNumber);
				Files.copy(targetTar, backupPath, StandardCopyOption.REPLACE_EXISTING);
			} else {
				backupPath = null;  // No existing tar to backup
			}

			// Step 2: Write new tar (with atomic fallback for Windows)
			try {
				Files.move(tempTargetTar, targetTar,
						StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
			} catch (AtomicMoveNotSupportedException e) {
				// Windows cross-filesystem fallback
				Files.copy(tempTargetTar, targetTar, StandardCopyOption.REPLACE_EXISTING);
				Files.delete(tempTargetTar);
			}

			// Step 3: Delete backup (save complete - all prior failures now succeeded)
			if (backupPath != null) {
				Files.deleteIfExists(backupPath);
			}
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	//================================================================================
	// Diff Update Helpers
	//================================================================================

	private long addDiffedFile(String fileName, String packageName, List<String> capturedLines,
							   Path sourcePath, int testRunNumber, int seed, boolean redactDiffs) throws DiffException, IOException {
		// Create this path if it didn't exist: testSupport/diffs/patches/filename
		String toWriteName = fileName + "_" + testRunNumber;
		Path toWritePath = filepathResolve(tempDirectory)
				.resolve(diffsFolderName)
				.resolve(patchesFolderName)
				.resolve(packageName + "." + toWriteName);
		String diffString;
		// Size check already performed during captureSourceFilesInMemory() for capturedLines
		// Only need to check sourcePath (the baseline file in temp directory)
		if (fileIsOrWasLargerThan(sourcePath, MAX_DIFFED_FILE_SIZE)) {
			diffString = "File too large!";
		} else {
			// Read in the baseline from disk
			List<String> original = readContents(sourcePath);
			// Use captured lines from memory instead of reading from disk
			List<String> revised = capturedLines;

			if (redactDiffs) {
				revised = redactStrings(revised, seed); // original should already be redacted
			}

			// Compute the diff: original -> revised
			Patch<String> patch = DiffUtils.diff(original, revised);

			// Write diffs
			List<AbstractDelta<String>> deltas = patch.getDeltas();
			if (deltas == null || deltas.size() == 0) {
				return 0;
			}

			Files.createDirectories(toWritePath.getParent());

			diffString = buildDiffOutputString(deltas);
		}
		return writeContents(toWritePath, toWriteName + ".java", diffString);
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

			try (var walkStream = Files.walk(diffsDir)) {
				walkStream
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
			}
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	//================================================================================
	// Error Logging
	//================================================================================

	private String generateMessage(Throwable throwable) {
		// Build stack trace (truncated for readability)
		StringBuilder stackStringBuilder = new StringBuilder();
		int messageLength = 0;
		int messageLengthLimit = 256;
		for (StackTraceElement ste : throwable.getStackTrace()) {
			String steMessage = ste.toString();
			if ((steMessage != null && steMessage.length() > 0)
					&& messageLength < messageLengthLimit) {
				stackStringBuilder.append("    at ").append(steMessage).append("\n");
				messageLength += steMessage.length() + 1;
			}
		}

		// Determine operation context for error categorization
		String operation = "initialization";
		for (StackTraceElement ste : throwable.getStackTrace()) {
			String method = ste.getMethodName();
			if (method != null && ste.getClassName().contains("LoggingExtension")) {
				// Use the first LoggingExtension method as the operation name
				operation = method;
				break;
			}
		}

		// Safely get context values that may not be initialized yet
		String runNum = "?";
		String pkgName = "?";
		String fileName = "?";
		try {
			runNum = String.valueOf(LoggingSingleton.getCurrentTestRunNumber());
		} catch (Throwable ignored) {}
		try {
			String pkg = LoggingSingleton.getTestFilePackageName();
			if (pkg != null) pkgName = pkg;
		} catch (Throwable ignored) {}
		try {
			String file = LoggingSingleton.getTestFileName();
			if (file != null) fileName = file;
		} catch (Throwable ignored) {}

		// Format: ERROR in <operation>: <ExceptionClass>: <message>
		// This format is parseable by analyze_errors.sh
		String exceptionType = throwable.getClass().getName();
		String message = throwable.getMessage();
		if (message == null) {
			message = "(no message)";
		}

		return "ERROR in " + operation + ": " + exceptionType + ": " + message + "\n"
				+ "  Run: " + runNum + " at " + LocalTime.now() + " [" + pkgName + " " + fileName + "]\n"
				+ stackStringBuilder.toString();
	}

	// Essentially makes a last-ditch effort to log the error
	// Now with defensive error handling - fails silently (no stderr)
	private void logError(Throwable throwable) {
		try {
			try {
				LoggingSingleton.accumulateTime();
			} catch (Throwable ignored) {
				// May not have started timing yet
			}

			String message = generateMessage(throwable);
			if (LoggingSingleton.getLoggedInitialError()) {
				return;
			}
			LoggingSingleton.setLoggedInitialError();

			// Ensure tempDirectory exists before trying to use it
			if (tempDirectory == null) {
				initTempDirectory();
			}

			Path errorFilepath = tempFilepathResolve(tempDirectory).resolve(errorLogFilename);
			Path filesDir = tempFilepathResolve(tempDirectory);
			Path tarPath = filepathResolve().resolve(finalTarFilename);

			// Safely try to clean up source folder
			try {
				if (Files.exists(tempDirectory.resolve(sourceFolderName))) {
					Files.walk(tempDirectory.resolve(sourceFolderName))
							.sorted(Comparator.reverseOrder())
							.map(Path::toFile)
							.forEach(File::delete);
				}
			} catch (Throwable ignored) {
				// Silently continue
			}

			Files.createDirectories(errorFilepath.getParent());
			Files.createDirectories(filesDir);
			Files.createDirectories(tarPath.getParent());

			// Safely try to untar existing logs
			try {
				untarLogs(filesDir, tarPath);
			} catch (Throwable t) {
				// Silently continue - we still want to save what we can
			}

			Files.write(
					errorFilepath,
					List.of(message),
					StandardOpenOption.CREATE,
					StandardOpenOption.APPEND
			);

			atomicallySaveTempFiles();
		} catch (Throwable T) {
			// Fail silently - no stderr output
		}
	}

	// Logs why the logger is shutting down/skipping, then saves to tar
	// Only logs once to avoid duplicate messages
	private void logShutdownReason(String reason) {
		if (loggedShutdownReason) {
			return;
		}
		loggedShutdownReason = true;

		try {
			// Ensure tempDirectory exists
			if (tempDirectory == null) {
				initTempDirectory();
			}

			Path errorFilepath = tempFilepathResolve(tempDirectory).resolve(errorLogFilename);
			Path filesDir = tempFilepathResolve(tempDirectory);
			Path tarPath = filepathResolve().resolve(finalTarFilename);

			Files.createDirectories(errorFilepath.getParent());
			Files.createDirectories(filesDir);

			// Try to untar existing logs to preserve history
			try {
				untarLogs(filesDir, tarPath);
			} catch (Throwable ignored) {
				// Continue anyway
			}

			String message = "SHUTDOWN - " + LocalTime.now() + ": " + reason + "\n";
			Files.write(
					errorFilepath,
					List.of(message),
					StandardOpenOption.CREATE,
					StandardOpenOption.APPEND
			);

			atomicallySaveTempFiles();
		} catch (Throwable ignored) {
			// Fail silently
		}
	}
}

