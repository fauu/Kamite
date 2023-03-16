import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigFactory;

public class ListKnownConfigKeys {
  public static void main(String[] args) {
    if (args.length < 2) {
      System.err.println(
        "Missing required arguments: 1) config spec file path, 2) known config keys output path"
      );
      return;
    }
    var configPath = Path.of(args[0]);

    var configFile = configPath.toFile();
    if (!configFile.canRead()) {
      System.err.println("File at specified path not readable");
      return;
    }

    var outPath = args[1];
    var keyCount = 0;
    try {
      var tsConfig = ConfigFactory.parseFile(configFile);
      var keys = ownConfigEntryKeys(tsConfig);
      keyCount = keys.size();
      var outStr = String.join("\n", keys);
      Files.write(Paths.get(outPath), outStr.getBytes(StandardCharsets.UTF_8));
    } catch (ConfigException e) {
      System.err.println("Exception while parsing config");
      e.printStackTrace();
      return;
    } catch (IOException e) {
      System.err.println("Exception while writing keys");
      e.printStackTrace();
      return;
    }

    System.out.printf("Wrote %d keys to: %s\n", keyCount, outPath);
  }

  private static List<String> ownConfigEntryKeys(Config config) {
    return config.entrySet().stream()
      .map(Map.Entry::getKey)
      .filter(ListKnownConfigKeys::isOwnKey)
      .toList();
  }

  // NOTE: Copied from io.github.kamitejp.config.ConfigManager
  public static boolean isOwnKey(String key) {
    return Character.isLowerCase(key.charAt(0));
  }
}
