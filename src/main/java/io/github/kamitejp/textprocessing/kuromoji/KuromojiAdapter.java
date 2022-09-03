package io.github.kamitejp.textprocessing.kuromoji;

import static java.util.stream.Collectors.toList;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.List;

import io.github.kamitejp.platform.Platform;
import io.github.kamitejp.util.Hashing;

public class KuromojiAdapter {
  private final static String JAR_FILENAME = "kuromoji-unidic-kanaaccent-0.9.0.jar";
  private final static String JAR_MD5 = "a212085b1a57822da1ffecfad6c3c059";

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

  private Class<?> tokenClass;
  private Method getSurfaceMethod;
  private Method getPartOfSpeechLevel1Method;
  private Method getKanaMethod;

  public KuromojiAdapter(Platform platform) {
    this.platform = platform;
  }

  @SuppressWarnings("unchecked")
  public List<MinimalKuromojiToken> tokenize(String s) throws KuromojiLoadingException {
    if (tokenizer == null) {
      loadKuromoji();
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
          // TODO Auto-generated catch block
          e.printStackTrace();
        }
        return null; // XXX
      }).collect(toList());
    } catch (IllegalAccessException | InvocationTargetException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    return null; // XXX
  }

  private void loadKuromoji() throws KuromojiLoadingException {
    var maybeJARPath = platform.getDataDirPath().map(p -> p.resolve(JAR_FILENAME));
    if (maybeJARPath.isEmpty()) {
      throw new KuromojiLoadingException("Could not determine Kuromoji JAR file path");
    }

    var jarPath = maybeJARPath.get();
    var jarFile = jarPath.toFile();
    if (!jarFile.canRead()) {
      throw new KuromojiLoadingException(
        "No readable Kuromoji JAR file at `%s`".formatted(jarPath)
      );
    }

    String jarMD5 = null;
    try {
      jarMD5 = Hashing.md5(jarFile);
    } catch (IOException e) {
      throw new KuromojiLoadingException("Could not compute Kuromoji JAR file hash", e);
    }

    if (!JAR_MD5.equalsIgnoreCase(jarMD5)) {
      throw new KuromojiLoadingException(
        "Kuromoji JAR file is incorrect (md5: %s)".formatted(jarMD5)
      );
    }

    try {
      var classLoader = new URLClassLoader(
        new URL[] {jarFile.toURI().toURL()},
        this.getClass().getClassLoader()
      );
      var tokenizerClass = Class.forName(TOKENIZER_CLASS_NAME, true, classLoader);
      tokenizer = tokenizerClass.getDeclaredConstructor().newInstance();
      tokenizeMethod = tokenizerClass.getDeclaredMethod(TOKENIZE_METHOD_NAME, String.class);

      tokenClass = Class.forName(TOKEN_CLASS_NAME, true, classLoader);
      getSurfaceMethod = tokenClass.getMethod(GET_SURFACE_METHOD_NAME);
      getPartOfSpeechLevel1Method = tokenClass.getDeclaredMethod(GET_PART_OF_SPEECH_LEVEL1_METHOD_NAME);
      getKanaMethod = tokenClass.getDeclaredMethod(GET_KANA_METHOD_NAME);
    } catch (Exception e) {
      throw new KuromojiLoadingException("Could not load objects from Kuromoji JAR", e);
    }
  }
}
