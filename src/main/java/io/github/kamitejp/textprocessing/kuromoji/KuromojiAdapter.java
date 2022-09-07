package io.github.kamitejp.textprocessing.kuromoji;

import java.io.File;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.List;
import java.util.Objects;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.github.kamitejp.platform.Platform;
import io.github.kamitejp.util.Hashing;
import io.github.kamitejp.util.Result;

public class KuromojiAdapter {
  private static final Logger LOG = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  private final static String JAR_FILENAME = "kuromoji-unidic-kanaaccent-0.9.0.jar";
  private final static long JAR_CRC32 = 205347984;

  private final static String DICT_PACKAGE = "com.atilika.kuromoji.unidic.kanaaccent";
  private final static String TOKENIZER_CLASS_NAME = "%s.Tokenizer".formatted(DICT_PACKAGE);
  private final static String TOKEN_CLASS_NAME = "%s.Token".formatted(DICT_PACKAGE);

  private final static String TOKENIZE_METHOD_NAME = "tokenize";
  private final static String GET_SURFACE_METHOD_NAME = "getSurface";
  private final static String GET_PART_OF_SPEECH_LEVEL1_METHOD_NAME = "getPartOfSpeechLevel1";
  private final static String GET_KANA_METHOD_NAME = "getKana";

  private Platform platform;

  private Object tokenizer;
  private Method tokenizeMethod;

  private Method getSurfaceMethod;
  private Method getPartOfSpeechLevel1Method;
  private Method getKanaMethod;

  public KuromojiAdapter(Platform platform) {
    this.platform = platform;
  }

  @SuppressWarnings("unchecked")
  public List<MinimalKuromojiToken> tokenize(String s) throws KuromojiLoadingException {
    if (tokenizer == null) {
      loadLibrary();
    }

    try {
      var tokens = (List<Object>) tokenizeMethod.invoke(tokenizer, s);
      return tokens.stream().map(t -> {
        try {
          return new MinimalKuromojiToken(
            (String) getSurfaceMethod.invoke(t),
            (String) getKanaMethod.invoke(t),
            (String) getPartOfSpeechLevel1Method.invoke(t)
          );
        } catch (IllegalAccessException | InvocationTargetException e) {
          LOG.error("Error invoking Kuromoji' `Token.get...()`", e);
          return null;
        }
      })
        .filter(Objects::nonNull)
        .toList();
    } catch (IllegalAccessException | InvocationTargetException e) {
      LOG.error("Error invoking Kuromoji's `Tokenizer.tokenize()`", e);
      return List.of();
    }
  }

  public Result<File, KuromojiLibraryVerificationError> getVerifiedLibraryFile() {
    var maybeJARPath = platform.getDataDirPath().map(p -> p.resolve(JAR_FILENAME));
    if (maybeJARPath.isEmpty()) {
      return Result.Err(KuromojiLibraryVerificationError.COULD_NOT_DETERMINE_PATH);
    }

    var jarPath = maybeJARPath.get();
    var jarFile = jarPath.toFile();
    if (!jarFile.canRead()) {
      return Result.Err(KuromojiLibraryVerificationError.NO_READABLE_FILE_AT_PATH);
    }

    try {
      var jarCRC32 = Hashing.crc32(jarFile);
      if (jarCRC32 != JAR_CRC32) {
        return Result.Err(KuromojiLibraryVerificationError.HASH_DOES_NOT_MATCH);
      }
    } catch (IOException e) {
      LOG.error("Exception while hashing Kuromoji JAR file", e);
      return Result.Err(KuromojiLibraryVerificationError.COULD_NOT_COMPUTE_HASH);
    }

    return Result.Ok(jarFile);
  }

  public boolean kuromojiAvailable() {
    return getVerifiedLibraryFile().isOk();
  }

  private void loadLibrary() throws KuromojiLoadingException {
    LOG.info("Loading Kuromoji");

    var maybeVerifiedJAR = getVerifiedLibraryFile();
    if (maybeVerifiedJAR.isErr()) {
      throw new KuromojiLoadingException(
        "Do not have valid Kuromoji JAR file: %s".formatted(maybeVerifiedJAR.err())
      );
    }

    URLClassLoader classLoader = null;
    try {
       classLoader = new URLClassLoader(
        new URL[] { maybeVerifiedJAR.get().toURI().toURL() },
        this.getClass().getClassLoader()
      );
      var tokenizerClass = Class.forName(TOKENIZER_CLASS_NAME, true, classLoader);
      tokenizer = tokenizerClass.getDeclaredConstructor().newInstance();
      tokenizeMethod = tokenizerClass.getDeclaredMethod(TOKENIZE_METHOD_NAME, String.class);

      var tokenClass = Class.forName(TOKEN_CLASS_NAME, true, classLoader);
      getSurfaceMethod = tokenClass.getMethod(GET_SURFACE_METHOD_NAME);
      getPartOfSpeechLevel1Method =
        tokenClass.getDeclaredMethod(GET_PART_OF_SPEECH_LEVEL1_METHOD_NAME);
      getKanaMethod = tokenClass.getDeclaredMethod(GET_KANA_METHOD_NAME);
    } catch (Exception e) {
      throw new KuromojiLoadingException("Could not load objects from Kuromoji JAR", e);
    } finally {
      try {
        if (classLoader != null) {
          classLoader.close();
        }
      } catch (IOException e) {
        LOG.debug("Exception while closing class loader", e);
      }
    }
  }
}
