package io.github.kamitejp;

import static java.util.stream.Collectors.toMap;

import java.util.Arrays;
import java.util.Map;

public final class Main {
  private Main() {}

  @SuppressWarnings("UseOfSystemOutOrSystemErr")
  public static void main(String[] args) {
    var argMap = parseArgs(args);

    if (argMap.get("version") != null) {
      System.out.printf("Kamite %s%n", BuildInfo.read().getVersion());
      System.exit(0);
    } else if (argMap.get("help") != null) {
      System.out.println("""
Usage: kamite [options]
Options:
  --help
      Print the usage message and exit
  --version
      Print the program version and exit
  --debug[=all]
      Print debug messages to the console, optionally also from third-party
      components
  --profile=<profile-id>
      Load the config file 'config.<profile-id>.hocon' on top of the main config
      file
  --regionHelper
      Launch in Region Helper mode: prints the region specifications for
      user-selected screen areas, exits once a selection is cancelled.

  Additional options are available, corresponding to the available config keys.
  Please consult the sections 'Command-line parameters' and 'Config' in the
  included README.md file.""");
      System.exit(0);
    }

    (new Kamite()).run(argMap, BuildInfo.read());
  }

  private static Map<String, String> parseArgs(String[] args) {
    return Arrays.stream(args)
      .filter(arg -> arg.startsWith("--"))
      .map(arg -> arg.substring(2))
      .map(arg -> arg.split("=", 2))
      .collect(toMap(segs -> segs[0], segs -> segs.length == 2 ? segs[1] : "true"));
  }
}
