package org.vcsreader.lang;

import org.jetbrains.annotations.NotNull;
import org.mozilla.universalchardet.UniversalDetector;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class CommandLine {
	public static final int exitCodeBeforeFinished = Integer.MIN_VALUE;

	private final Config config;
	private final String[] commandAndArgs;

	private String stdout = "";
	private String stderr = "";
	private int exitCode = exitCodeBeforeFinished;

	private final AtomicReference<Process> processRef = new AtomicReference<>();
	private final Map<String, String> environment = new HashMap<>();


	public CommandLine(Collection<String> commandAndArgs) {
		this(Config.defaults, commandAndArgs.toArray(new String[0]));
	}

	public CommandLine(String... commandAndArgs) {
		this(Config.defaults, commandAndArgs);
	}

	public CommandLine(Config config, String... commandAndArgs) {
		this.config = config;
		this.commandAndArgs = checkForNulls(commandAndArgs);
	}

	public CommandLine workingDir(String path) {
		if (path == null) return new CommandLine(config.workingDir(null), commandAndArgs);
		else return new CommandLine(config.workingDir(new File(path)), commandAndArgs);
	}

	public CommandLine environment(Map<String, String> map) {
		environment.clear();
		environment.putAll(map);
		return this;
	}

	public CommandLine outputCharset(@NotNull Charset charset) {
		return new CommandLine(config.outputCharset(charset), commandAndArgs);
	}

	public CommandLine charsetAutoDetect(boolean value) {
		return new CommandLine(config.charsetAutoDetect(value), commandAndArgs);
	}

	public CommandLine execute() throws Failure {
		Process process;
		try {

			ProcessBuilder builder = new ProcessBuilder(commandAndArgs).directory(config.workingDir);
			builder.environment().putAll(environment);
			process = builder.start();
			processRef.set(process);

			try (final InputStream stdoutInputStream = process.getInputStream();
				 final InputStream stderrInputStream = process.getErrorStream()) {

				CompletableFuture<String> stdoutFuture = CompletableFuture.supplyAsync(
						() -> readStreamTask(stdoutInputStream, config.stdoutBufferSize), config.asyncExecutor);
				CompletableFuture<String> stderrFuture = CompletableFuture.supplyAsync(
						() -> readStreamTask(stderrInputStream, config.stderrBufferSize), config.asyncExecutor);

				stdout = stdoutFuture.get();
				stderr = stderrFuture.get();

				process.waitFor();
				process.destroy();
				exitCode = process.exitValue();
			}
		} catch (Exception e) {
			throw new Failure(e);
		} finally {
			kill(); // Make sure process is stopped in case of exceptions in java code.
			processRef.set(null);
		}

		return this;
	}

	/**
	 * @return true is underlying process is dead (or there is no process), false if process is still running.
	 */
	public boolean kill() {
		Process process = processRef.get();
		if (process != null) {
			process.destroy();
			try {
				for (int i = 0; i < 20 && process.isAlive(); i++) {
					Thread.sleep(10);
				}
			} catch (InterruptedException ignored) {
			}
			return !process.isAlive();
		}
		return true;
	}

	@NotNull
	public String stdout() {
		return stdout;
	}

	@NotNull
	public String stderr() {
		return stderr;
	}

	public int exitCode() {
		return exitCode;
	}

	public String describe() {
		StringBuilder result = new StringBuilder();
		for (int i = 0; i < commandAndArgs.length; i++) {
			result.append(commandAndArgs[i]);
			if (i < commandAndArgs.length - 1) result.append(" ");
		}
		if (config.workingDir != null) {
			result.append(" (working directory '").append(config.workingDir).append("')");
		}
		return result.toString();
	}

	@Override
	public String toString() {
		return describe();
	}

	private String readStreamTask(final InputStream stdoutInputStream, final int inputBufferSize) {
		try {
			byte[] bytes = readAsBytes(stdoutInputStream, inputBufferSize);
			return convertToString(bytes);
		} catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	private String convertToString(byte[] bytes) throws IOException {
		Charset charset = config.charsetAutoDetect ?
				detectCharset(bytes, config.maxBufferForCharsetDetection) :
				config.outputCharset;
		if (charset == null) charset = config.outputCharset;
		return new String(bytes, charset);
	}

	private static byte[] readAsBytes(InputStream inputStream, int inputBufferSize) throws IOException {
		ByteArrayOutputStream byteArrayStream = new ByteArrayOutputStream();
		byte[] buffer = new byte[inputBufferSize];
		int n;
		while ((n = inputStream.read(buffer, 0, buffer.length)) != -1) {
			byteArrayStream.write(buffer, 0, n);
		}
		byteArrayStream.flush();
		return byteArrayStream.toByteArray();
	}

	private static Charset detectCharset(byte[] bytes, int maxBufferForCharsetDetection) {
		UniversalDetector detector = new UniversalDetector(null);
		try {
			detector.handleData(bytes, 0, Math.min(bytes.length, maxBufferForCharsetDetection));
			detector.dataEnd();
		} finally {
			detector.reset();
		}
		String charsetName = detector.getDetectedCharset();
		return charsetName == null ? null : Charset.forName(charsetName);
	}

	private static String[] checkForNulls(String[] command) {
		for (String arg : command) {
			if (arg == null) {
				throw new IllegalStateException("Command cannot have null as inputs, but was: " + Arrays.toString(command));
			}
		}
		return command;
	}

	public static class Failure extends RuntimeException {
		public Failure(Throwable cause) {
			super(cause);
		}
	}

	public static class Config {
		private static final int defaultBufferSize = 8192;
		private static final File currentDirectory = null;
		private static final AtomicInteger threadCounter = new AtomicInteger(1);
		public static Config defaults = new Config(
				currentDirectory,
				defaultBufferSize,
				defaultBufferSize,
				Charset.defaultCharset(), false, defaultBufferSize,
				Executors.newFixedThreadPool(ForkJoinPool.getCommonPoolParallelism(),
						r -> new Thread(r, "stdout reader: " + threadCounter.getAndIncrement()))
		);

		private final File workingDir;
		private final int stdoutBufferSize;
		private final int stderrBufferSize;
		private final Charset outputCharset;
		private final boolean charsetAutoDetect;
		private final int maxBufferForCharsetDetection;
		private final ExecutorService asyncExecutor;

		public Config(File workingDir, int stdoutBufferSize, int stderrBufferSize, Charset outputCharset,
					  boolean charsetAutoDetect, int maxBufferForCharsetDetection, ExecutorService asyncExecutor) {
			this.workingDir = workingDir;
			this.stdoutBufferSize = stdoutBufferSize;
			this.stderrBufferSize = stderrBufferSize;
			this.outputCharset = outputCharset;
			this.charsetAutoDetect = charsetAutoDetect;
			this.maxBufferForCharsetDetection = maxBufferForCharsetDetection;
			this.asyncExecutor = asyncExecutor;
		}

		public Config workingDir(File newWorkingDirectory) {
			return new Config(newWorkingDirectory, stdoutBufferSize, stderrBufferSize, outputCharset, charsetAutoDetect, maxBufferForCharsetDetection, asyncExecutor);
		}

		public Config charsetAutoDetect(boolean value) {
			return new Config(workingDir, stdoutBufferSize, stderrBufferSize, outputCharset, value, maxBufferForCharsetDetection, asyncExecutor);
		}

		public Config outputCharset(Charset charset) {
			return new Config(workingDir, stdoutBufferSize, stderrBufferSize, charset, charsetAutoDetect, maxBufferForCharsetDetection, asyncExecutor);
		}

		public Config asyncExecutor(ExecutorService newAsyncExecutor) {
			return new Config(workingDir, stdoutBufferSize, stderrBufferSize, outputCharset, charsetAutoDetect, maxBufferForCharsetDetection, newAsyncExecutor);
		}
	}
}
