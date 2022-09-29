package io.github.kamitejp.config;

import java.io.File;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.util.List;
import java.util.function.Consumer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

final class ConfigFilesWatcher implements Runnable {
  private static final Logger LOG = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  private final List<File> configFiles;
  private final Consumer<Void> fileModifiedCb;
  private final Thread thread;

  ConfigFilesWatcher(List<File> configFiles, Consumer<Void> fileModifiedCb) {
    if (configFiles.isEmpty()) {
      throw new IllegalArgumentException("List of config files must not be empty");
    }
    //noinspection AssignmentOrReturnOfFieldWithMutableType
    this.configFiles = configFiles;
    this.fileModifiedCb = fileModifiedCb;
    thread = new Thread(this);
    thread.start();
  }

  @Override
  public void run() {
    var configFilenames = configFiles.stream().map(File::toPath).map(Path::getFileName).toList();
    try (var watchService = FileSystems.getDefault().newWatchService()) {
      var watchDir = configFiles.get(0).getParentFile().toPath();
      var watchKey = watchDir.register(watchService, StandardWatchEventKinds.ENTRY_MODIFY);
      while ((watchKey = watchService.take()) != null) {
        for (var ev : watchKey.pollEvents()) {
          LOG.debug("Received watch service event: kind='{}' context='{}'", ev::kind, ev::context);
          if (ev.context() != null) {
            var filePath = (Path) ev.context();
            if (configFilenames.contains(filePath)) {
              fileModifiedCb.accept(null);
            }
          }
        }
        watchKey.reset();
      }
    } catch (IOException e) {
      LOG.error("Exception while creating file watcher:", e);
    } catch (InterruptedException ignored) {
      LOG.debug("Watch service was interrupted");
    }
  }

  void destroy() {
    if (thread != null) {
      thread.interrupt();
    }
  }
}
