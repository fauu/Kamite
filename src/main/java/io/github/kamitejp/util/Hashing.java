package io.github.kamitejp.util;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class Hashing {
  private static MessageDigest md5Digest;

  private Hashing() {}

  // https://mkyong.com/java/how-to-generate-a-file-checksum-value-in-java/
  public static String md5(File file) throws IOException {
    if (md5Digest == null) {
      try {
        md5Digest = MessageDigest.getInstance("MD5");
      } catch (NoSuchAlgorithmException e) {
        throw new IllegalStateException("MD5 algorithm unavailable");
      }
    } else {
      md5Digest.reset();
    }

    var fis = new FileInputStream(file);
    var bis = new BufferedInputStream(fis);
    var dis = new DigestInputStream(bis, md5Digest);

    try {
      while (dis.read() != -1) {
        // Empty loop to clear the data
      }
    } finally {
      fis.close();
    }

    // Bytes to hex
    var res = new StringBuilder();
    for (var b : md5Digest.digest()) {
      res.append(String.format("%02x", b));
    }
    return res.toString();
  }
}
