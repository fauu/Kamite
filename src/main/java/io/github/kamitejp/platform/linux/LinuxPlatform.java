package io.github.kamitejp.platform.linux;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.github.kamitejp.dbus.DBusClient;
import io.github.kamitejp.dbus.DBusClientInitializationException;
import io.github.kamitejp.platform.CPUArchitecture;
import io.github.kamitejp.platform.GenericPlatform;
import io.github.kamitejp.platform.OS;
import io.github.kamitejp.platform.Platform;
import io.github.kamitejp.platform.PlatformCreationException;
import io.github.kamitejp.platform.PlatformInitializationException;
import io.github.kamitejp.platform.process.ProcessHelper;

public abstract class LinuxPlatform extends GenericPlatform implements Platform {
  private static final Logger LOG = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  private CPUArchitecture cpuArchitecture;
  protected DBusClient dbusClient;

  protected LinuxPlatform() throws PlatformCreationException {
    super("linux");
    if (getOS() != OS.LINUX) {
      throw new PlatformCreationException("Detected OS is not Linux");
    }
  }

  @Override
  public void init() throws PlatformInitializationException {
    super.init();

    dbusClient = new DBusClient();
    try {
      dbusClient.init();
    } catch (DBusClientInitializationException e) {
      LOG.warn(
        "Failed to initialize DBus client. The DBus API will be unavailable for this session. {}",
        () -> e.toString()
      );
      dbusClient = null;
    }
  }

  @Override
  public Optional<Path> getDefaultPipxVenvPythonPath(String venvName) {
    return getUserHomeDirPath().map(home ->
      home.resolve(".local/pipx/venvs").resolve(venvName).resolve("bin/python")
    );
  }

  @Override
  public void openURL(String url) {
    try {
      Runtime.getRuntime().exec(new String[] { "sh", "-c", "MOZ_DBUS_REMOTE=1 xdg-open " + url });
    } catch (IOException e) {
      LOG.warn("Could not open web browser", e);
    }
  }

  @Override
  public Optional<Path> getConfigDirPath() {
    var envConfigHome = getEnvVarAsNonNullableString("XDG_CONFIG_HOME");
    if (!envConfigHome.isBlank()) {
      return Optional.of(Paths.get(envConfigHome).resolve(APP_DIR_PATH_RELATIVE));
    }
    var maybeHome = getUserHomeDirPath();
    return maybeHome.map(home -> home.resolve(".config").resolve(APP_DIR_PATH_RELATIVE));
  }

  @Override
  public Optional<Path> getDataDirPath() {
    var envDataHome = getEnvVarAsNonNullableString("XDG_DATA_HOME");
    if (!envDataHome.isBlank()) {
      return Optional.of(Paths.get(envDataHome).resolve(APP_DIR_PATH_RELATIVE));
    }
    var maybeHome = getUserHomeDirPath();
    return maybeHome.map(home -> home.resolve(".local/share").resolve(APP_DIR_PATH_RELATIVE));
  }

  @Override
  public void destroy() {
    dbusClient.destroy();
  }

  public Optional<DBusClient> getDBusClient() {
    return Optional.ofNullable(dbusClient);
  }

  @SuppressWarnings("unused")
  private CPUArchitecture getCPUArchitecture() {
    if (cpuArchitecture == null) {
      cpuArchitecture = determineCPUArchitecture().orElse(null);
    }
    return cpuArchitecture;
  }

  // ROBUSTNESS: Possibly returns AMD64 on a 32-bit system running on an AMD64 processor
  private Optional<CPUArchitecture> determineCPUArchitecture() {
    var res =  ProcessHelper.run("uname", "-m").getStdout();
    CPUArchitecture arch = null;
    if ("x86_64".equalsIgnoreCase(res) || "amd64".equalsIgnoreCase(res)) {
      arch = CPUArchitecture.AMD64;
    }
    return Optional.ofNullable(arch);
  }
}
