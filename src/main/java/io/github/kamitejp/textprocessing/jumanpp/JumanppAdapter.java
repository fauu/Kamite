package io.github.kamitejp.textprocessing.jumanpp;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.invoke.MethodHandles;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.github.kamitejp.platform.Platform;

public class JumanppAdapter {
  private static final Logger LOG = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  private static final String MODEL_FILENAME = "jumandic.model";
  private static final long MODEL_CRC32 = 3410533956L;
  private static final String BIN_FILENAME = "jumanpp_v2";
  private static final long BIN_CRC32 = 3439180611L;
  private static final String OUTPUT_END_MARKER_LINE_PREFIX = "EOS";

  private final Platform platform;

  private Process process;
  private OutputStream writer;
  private BufferedReader reader;

  public JumanppAdapter(Platform platform) {
    this.platform = platform;
  }

  public void start() throws JumanppLoadingException {
    LOG.info("Starting Juman++ morphological analyzer");

    var maybeVerifiedModel = platform.getVerifiedDependencyFile(MODEL_FILENAME, MODEL_CRC32);
    if (maybeVerifiedModel.isErr()) {
      throw new JumanppLoadingException(
        "Do not have the required Juman++ model file: %s".formatted(maybeVerifiedModel.err())
      );
    }

    var maybeVerifiedBin = platform.getVerifiedDependencyFile(BIN_FILENAME, BIN_CRC32);
    if (maybeVerifiedBin.isErr()) {
      throw new JumanppLoadingException(
        "Do not have the required Juman++ executable file: %s".formatted(maybeVerifiedBin.err())
      );
    }

    var pb = new ProcessBuilder(
      maybeVerifiedBin.get().getAbsolutePath(),
      "--model=%s".formatted(maybeVerifiedModel.get().getAbsolutePath())
    );
    try {
      process = pb.start();
      writer = process.getOutputStream();
      reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
    } catch (IOException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
  }

  public void test(String text) {
    // XXX
    if (writer == null || reader == null) {
      return;
    }

    try {
      writer.write((text + "\n").getBytes());
      writer.flush();

      String line;
      StringBuilder outBuilder = new StringBuilder();
      while (true) {
        line = reader.readLine();
        if (line == null || line.startsWith(OUTPUT_END_MARKER_LINE_PREFIX)) {
          break;
        }
        outBuilder.append(line + "\n");
        // TODO: Parse line
      }
      System.out.println(outBuilder.toString());
    } catch (IOException e) {
      LOG.warn("Error communicating with Juman++. See stderr for the stack trace");
      e.printStackTrace();
    }
  }

  public void destroy() {
    try {
      if (writer != null) {
        writer.close();
      }
      if (reader != null) {
        reader.close();
      }
    } catch (IOException e) {
      LOG.warn("Error closing a Juman++ process stream");
    }
  }
}
