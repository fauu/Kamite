package io.github.kamitejp.util;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.zip.CRC32;
import java.util.zip.CheckedInputStream;

public final class Hashing {
  private Hashing() {}

  public static long crc32(File file) throws IOException {
    try (
      var in = new CheckedInputStream(
        new BufferedInputStream(new FileInputStream(file)),
        new CRC32()
      )
    ) {
      @SuppressWarnings("CheckForOutOfMemoryOnLargeArrayAllocation") var buf = new byte[4096];
      //noinspection StatementWithEmptyBody
      while (in.read(buf) != -1) {} // NOPMD - intentionally empty
      return in.getChecksum().getValue();
    }
  }
}
