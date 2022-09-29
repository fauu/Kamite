package io.github.kamitejp.textprocessing.kuromoji;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
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

  private static final String JAR_VER = "e18ff911fd";
  private static final String JAR_FILENAME
    = "kuromoji-unidic-kanaaccent-%s.jar".formatted(JAR_VER);
  private static final long JAR_CRC32 = 3601520699L;

  private static final String DICT_PACKAGE = "com.atilika.kuromoji.unidic.kanaaccent";
  private static final String TOKENIZER_CLASS_NAME = "%s.Tokenizer".formatted(DICT_PACKAGE);
  private static final String TOKENIZER_BUILDER_CLASS_NAME =
    "%s$Builder".formatted(TOKENIZER_CLASS_NAME);
  private static final String TOKEN_CLASS_NAME = "%s.Token".formatted(DICT_PACKAGE);
  private static final String USER_DICTIONARY_METHOD_NAME = "userDictionary";
  private static final String USER_DICTIONARY_RESOURCE_PATH = "/kuromoji_user_dict.txt";
  private static final String BUILD_METHOD_NAME = "build";
  private static final String TOKENIZE_METHOD_NAME = "tokenize";
  private static final String GET_SURFACE_METHOD_NAME = "getSurface";
  private static final String GET_PART_OF_SPEECH_LEVEL1_METHOD_NAME = "getPartOfSpeechLevel1";
  private static final String GET_KANA_METHOD_NAME = "getKana";

  private final Platform platform;
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

  private Result<File, KuromojiLibraryVerificationError> getVerifiedLibraryFile() {
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

  public boolean isKuromojiAvailable() {
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
    InputStream userDictionaryIn = null;
    try {
      //noinspection ClassLoaderInstantiation
      classLoader = new URLClassLoader(
        new URL[] { maybeVerifiedJAR.get().toURI().toURL() },
        getClass().getClassLoader()
      );
      var tokenizerBuilderClass = Class.forName(TOKENIZER_BUILDER_CLASS_NAME, true, classLoader);
      var tokenizerBuilder = tokenizerBuilderClass.getDeclaredConstructors()[0].newInstance();

      var userDictionaryMethod =
        tokenizerBuilderClass.getMethod(USER_DICTIONARY_METHOD_NAME, InputStream.class);
      userDictionaryIn =
        KuromojiAdapter.class.getResourceAsStream(USER_DICTIONARY_RESOURCE_PATH);
      var tokenizerBuilderWithDict =
        userDictionaryMethod.invoke(tokenizerBuilder, userDictionaryIn);

      var buildMethod = tokenizerBuilderClass.getMethod(BUILD_METHOD_NAME);
      tokenizer = buildMethod.invoke(tokenizerBuilderWithDict);

      var tokenizerClass = Class.forName(TOKENIZER_CLASS_NAME, true, classLoader);
      tokenizeMethod = tokenizerClass.getDeclaredMethod(TOKENIZE_METHOD_NAME, String.class);

      var tokenClass = Class.forName(TOKEN_CLASS_NAME, true, classLoader);
      getSurfaceMethod = tokenClass.getMethod(GET_SURFACE_METHOD_NAME);
      getPartOfSpeechLevel1Method =
        tokenClass.getDeclaredMethod(GET_PART_OF_SPEECH_LEVEL1_METHOD_NAME);
      getKanaMethod = tokenClass.getDeclaredMethod(GET_KANA_METHOD_NAME);
    } catch (@SuppressWarnings("OverlyBroadCatchBlock") Exception e) {
      throw new KuromojiLoadingException("Could not load things from Kuromoji JAR", e);
    } finally {
      try {
        if (classLoader != null) {
          classLoader.close();
        }
        if (userDictionaryIn != null) {
          userDictionaryIn.close();
        }
      } catch (IOException e) {
        LOG.debug("Exception while closing class loader", e);
      }
    }
  }
}
