package io.github.kamitejp.chunk;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.lang.invoke.MethodHandles;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ChunkLogger {
  private static final Logger LOG = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  private final DateTimeFormatter logFilenameTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

  private OutputStreamWriter logFileWriter;

  public ChunkLogger(String logDirPathStr) throws ChunkLoggerInitializationException {
    var logDirPath = Paths.get(logDirPathStr);
    try {
      Files.createDirectories(logDirPath);
    } catch (IOException e) {
      throw new ChunkLoggerInitializationException("Could not create chunk log directory", e);
    }
    var logFile = logDirPath.resolve(logFilename()).toFile();
    try {
      logFileWriter = new OutputStreamWriter(
        new FileOutputStream(logFile),
        StandardCharsets.UTF_8
      );
    } catch (IOException e) {
      throw new ChunkLoggerInitializationException("Could not create chunk log file", e);
    }
  }

  public void log(String chunk) {
    try {
      logFileWriter.write(chunk.replaceAll("\r?\n", "\\\\n"));
      logFileWriter.write("\n");
      logFileWriter.flush();
    } catch (IOException e) {
      LOG.error("Exception while logging chunk `{}`", chunk);
    }
  }

  public void finalize() {
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
