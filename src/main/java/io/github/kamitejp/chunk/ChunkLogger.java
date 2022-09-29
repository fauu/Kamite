package io.github.kamitejp.chunk;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.lang.invoke.MethodHandles;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.regex.Pattern;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ChunkLogger {
  private static final Logger LOG = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  private static final Pattern CHUNK_NEWLINE_RE = Pattern.compile("\r?\n");

  private final DateTimeFormatter logFilenameTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss", Locale.ENGLISH);

  private final OutputStreamWriter logFileWriter;

  public ChunkLogger(String logDirPathStr) throws ChunkLoggerInitializationException {
    var logDirPath = Paths.get(logDirPathStr);
    try {
      Files.createDirectories(logDirPath);
    } catch (IOException e) {
      throw new ChunkLoggerInitializationException("Could not create chunk log directory", e);
    }
    var logFile = logDirPath.resolve(logFilename()).toFile();
    try {
      logFileWriter = new OutputStreamWriter(new FileOutputStream(logFile), StandardCharsets.UTF_8);
    } catch (FileNotFoundException e) {
      throw new ChunkLoggerInitializationException("Could not create chunk log file", e);
    }
  }

  public void log(String chunk) {
    try {
      logFileWriter.write(CHUNK_NEWLINE_RE.matcher(chunk).replaceAll("\\\\n"));
      logFileWriter.write("\n");
      logFileWriter.flush();
    } catch (IOException e) {
      LOG.error("Exception while logging chunk `{}`", chunk);
    }
  }

  public void finalizeLog() {
    try {
      logFileWriter.close();
    } catch (IOException e) {
      LOG.error("Exception while closing chunk log file", e);
    }
  }

  private String logFilename() {
    var timeStr = logFilenameTimeFormatter.format(LocalDateTime.now());
    return "kamite_chunk_log_%s.txt".formatted(timeStr);
  }
}
