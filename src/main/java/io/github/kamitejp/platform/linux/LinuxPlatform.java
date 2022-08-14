package io.github.kamitejp.platform.linux;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
  private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

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
      LOG.warn( // NOPMD
        "Failed to initialize DBus client. The DBus API will be unavailable for this session. {}",
        e.toString()
      );
      dbusClient = null;
    }
  }

  @Override
  public Path getMangaOCRWrapperPath() {
    return getGenericLibDirPath().resolve(MANGAOCR_WRAPPER_FILENAME);
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
      return Optional.of(Paths.get(envConfigHome).resolve(CONFIG_DIR_PATH_RELATIVE));
    }
    var envHome = getEnvVarAsNonNullableString("HOME");
    if (!envHome.isBlank()) {
      return Optional.of(Paths.get(envHome).resolve(".config").resolve(CONFIG_DIR_PATH_RELATIVE));
    }
    return Optional.empty();
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
