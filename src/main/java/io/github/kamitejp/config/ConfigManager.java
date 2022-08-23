package io.github.kamitejp.config;

import java.io.File;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.UnaryOperator;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigOrigin;
import com.typesafe.config.impl.ConfigImpl;

import io.github.kamitejp.util.Result;

public final class ConfigManager {
  private static final Logger LOG = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  private static final String MAIN_CONFIG_FILE_PATH_RELATIVE = "config.hocon";
  private static final String PROFILE_CONFIG_FILE_PATH_RELATIVE_TPL = "config.%s.hocon";
  private static final String DEFAULT_CONFIG_FILE_RESOURCE_PATH = "/config.default.hocon";
  private static final String LOOKUP_TARGET_URL_PLACEHOLDER = "{}";

  private ConfigManager() {}

  @SuppressWarnings("WeakerAccess") // Mistaken
  public record ReadSuccess(Config config, String[] loadedProfileNames) {}

  public static Result<ReadSuccess, String> read(
    Path configDirPath,
    String profileName,
    Map<String, String> args
  ) {
    var mainConfigPath = configDirPath.resolve(MAIN_CONFIG_FILE_PATH_RELATIVE);
    var profileConfigPath =
      profileName == null
      ? null
      : configDirPath.resolve(PROFILE_CONFIG_FILE_PATH_RELATIVE_TPL.formatted(profileName));

    var ensureRes = ensureMainConfig(configDirPath, mainConfigPath);
    if (ensureRes.isErr()) {
      return Result.Err(ensureRes.err());
    }

    String[] loadedProfileNames = null;
    File profileConfigFile = null;
    if (profileConfigPath != null) {
      profileConfigFile = profileConfigPath.toFile();
      if (!profileConfigFile.canRead()) {
        LOG.warn("Config file for the requested profile is not accessible: {}", profileConfigPath);
      } else {
        loadedProfileNames = new String[] { profileName };
      }
    }

    try {
      var tsConfig = configFromArgs(args);
      if (tsConfig == null) {
        return Result.Err("Failed to parse command line arguments into a Config object");
      }
      if (profileConfigFile != null) {
        tsConfig = tsConfig.withFallback(ConfigFactory.parseFile(profileConfigFile));
      }
      tsConfig = tsConfig.withFallback(ConfigFactory.parseFile(mainConfigPath.toFile()));

      var config = new Config(tsConfig.resolve());
      validateExtra(config);

      LOG.debug("Read config: {}", config);

      return Result.Ok(new ReadSuccess(config, loadedProfileNames));
    } catch (ConfigException e) {
      return Result.Err(e.toString());
    }
  }

  private static Result<Void, String> ensureMainConfig(Path dirPath, Path filePath) {
    if (Files.isReadable(filePath)) {
      return Result.Ok(null);
    }

    LOG.info( // NOPMD
      "Main config file is not accessible: {}. Creating a default config file",
      filePath
    );
    var res = createDefaultConfig(dirPath, filePath);
    if (res.isOk()) {
      LOG.info("Created a default config file at '{}'", filePath);
    }
    return res;
  }

  private static Result<Void, String> createDefaultConfig(Path dirPath, Path filePath) {
    try {
      if (!Files.isDirectory(dirPath)) {
        LOG.info("Creating the config directory: {}", dirPath);
        Files.createDirectory(dirPath);
      }
      Files.copy(
        Objects.requireNonNull(
          ConfigManager.class.getResourceAsStream(DEFAULT_CONFIG_FILE_RESOURCE_PATH)
        ),
        filePath
      );
    } catch (IOException | RuntimeException e) {
      return Result.Err(
        "Failed to create a default config file at '%s': %s".formatted(filePath, e)
      );
    }
    return Result.Ok(null);
  }


  private static com.typesafe.config.Config configFromArgs(Map<String, String> args) {
    try {
      var c = Class.forName("com.typesafe.config.impl.PropertiesParser");
      var fromStringMapMethod = c.getDeclaredMethod("fromStringMap", ConfigOrigin.class, Map.class);
      fromStringMapMethod.setAccessible(true);
      var abstractConfig = fromStringMapMethod.invoke(
        null,
        ConfigImpl.newSimpleOrigin("program arguments"),
        args
      );

      c = Class.forName("com.typesafe.config.impl.AbstractConfigObject");
      var toConfigMethod = c.getDeclaredMethod("toConfig");
      toConfigMethod.setAccessible(true);

      return (com.typesafe.config.Config) toConfigMethod.invoke(abstractConfig);
    } catch (
      ClassNotFoundException
      | NoSuchMethodException
      | InvocationTargetException
      | IllegalAccessException e
    ) {
      LOG.error("Could not read program arguments into a Config object");
    }
    return null;
  }

  @SuppressWarnings("ThrowsRuntimeException")
  private static void validateExtra(Config config) throws ConfigException {
    validateExtraList(config.commands().custom(), "commands.custom[%d]", (c, key) -> {
      validateSymbolLength(c.symbol(), key.apply("symbol"));
      validateStringNonEmpty(c.name(), key.apply("name"));
      validateStringNonEmpty(c.command(), key.apply("command"));
    });

    validateExtraList(config.lookup().targets(), "lookup.targets[%d]", (t, key) ->{
      validateSymbolLength(t.symbol(), key.apply("symbol"));
      validateStringNonEmpty(t.name(), key.apply("name"));

      var urlKey = key.apply("url");
      validateStringNonEmpty(t.url(), urlKey);
      validateStringContains(t.url(), LOOKUP_TARGET_URL_PLACEHOLDER, urlKey);
    });

    validateStringNonEmptyOrNull(config.ocr().watchDir(), "ocr.watchDir");
    validateStringNonEmptyOrNull(config.ocr().mangaocr().pythonPath(), "ocr.mangaocr.pythonPath");

    validateExtraList(config.ocr().regions(), "ocr.regions[%d]", (r, key) -> {
      validateSymbolLength(r.symbol(), key.apply("symbol"));
      validateStringNonEmptyOrNull(r.description(), key.apply("description"));
    });

    validateStringNonEmptyOrNull(config.secrets().ocrspace(), "secrets.ocrspace");
  }

  @SuppressWarnings("ThrowsRuntimeException")
  private static <T> void validateExtraList(
    List<T> list, String keyPrefixTpl, BiConsumer<T, UnaryOperator<String>> validateElementFn
  ) throws ConfigException {
    if (list == null) {
      return;
    }
    for (var i = 0; i < list.size(); i++) {
      var el = list.get(i);
      var keyPrefix = keyPrefixTpl.formatted(i);
      //noinspection ObjectAllocationInLoop
      validateElementFn.accept(el, (key) -> "%s.%s".formatted(keyPrefix, key));
    }
  }

  @SuppressWarnings("ThrowsRuntimeException")
  private static void validateSymbolLength(
    CharSequence symbol, String key
  ) throws ConfigException.BadValue {
    var len = symbol.length();
    if (len < 1 || len > 3) {
      throw new ConfigException.BadValue(key, "should be between 1 and 3 characters");
    }
  }

  @SuppressWarnings("ThrowsRuntimeException")
  private static void validateStringNonEmpty(
    CharSequence s, String key
  ) throws ConfigException.BadValue {
    if (s.isEmpty()) {
      throw new ConfigException.BadValue(key, "should not be empty");
    }
  }

  @SuppressWarnings("ThrowsRuntimeException")
  private static void validateStringNonEmptyOrNull(
    CharSequence s, String key
  ) throws ConfigException.BadValue {
    if (s != null) {
      validateStringNonEmpty(s, key);
    }
  }

  @SuppressWarnings({"SameParameterValue", "ThrowsRuntimeException"})
  private static void validateStringContains(
    String s, String substring, String key
  ) throws ConfigException.BadValue {
    if (!s.contains(substring)) {
      throw new ConfigException.BadValue(key, "should contain '%s'".formatted(substring));
    }
  }
}
