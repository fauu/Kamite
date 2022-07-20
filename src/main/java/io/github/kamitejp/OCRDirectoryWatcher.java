package io.github.kamitejp;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.nio.file.FileSystems;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.kamitejp.platform.GenericPlatform;

public class OCRDirectoryWatcher {
  private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private final Path dirPath;
  private final WatchService watchService;
  private final Consumer<BufferedImage> recognizeBox;
  private final Thread worker;

  public OCRDirectoryWatcher(
    String rawDirPath, Consumer<BufferedImage> recognizeBox
  ) throws OCRDirectoryWatcherCreationException {
    this.recognizeBox = recognizeBox;
    try {
      dirPath = Paths.get(rawDirPath);
    } catch (InvalidPathException e) {
      throw new OCRDirectoryWatcherCreationException(e);
    }
    try {
      watchService = FileSystems.getDefault().newWatchService();
    } catch (IOException e) {
      throw new OCRDirectoryWatcherCreationException(e);
    }
    try {
      var watchKey = dirPath.register(
        watchService, StandardWatchEventKinds.ENTRY_MODIFY
      );
      worker = new Thread(
        new Worker(watchService, watchKey, this::handleFileModified)
      );
      LOG.debug("Starting OCR directory watcher worker thread");
      worker.start();
    } catch (IOException e) {
      throw new OCRDirectoryWatcherCreationException(e);
    }
  }

  public void destroy() {
    try {
      watchService.close();
    } catch (IOException e) {
      LOG.debug("Failed to close watch service. See stderr for stack trace");
      e.printStackTrace();
    }
    worker.interrupt();
  }

  private void handleFileModified(Path relativePath) {
    var absolutePath = dirPath.resolve(relativePath);
    GenericPlatform.openImage(absolutePath)
      .ifPresentOrElse(
        img -> {
          LOG.debug("Recognizing image from watched directory: {}", absolutePath);
          recognizeBox.accept(img);
        },
        () -> LOG.error("OCR directory watcher did not receive image: {}", absolutePath)
      );
  }

  private class Worker implements Runnable {
    private final WatchService watchService;
    private WatchKey watchKey;
    private final Consumer<Path> fileModifiedOrCreatedCb;

    Worker(
      WatchService watchService,
      WatchKey watchKey,
      Consumer<Path> fileModifiedOrCreatedCb
    ) {
      this.watchService = watchService;
      this.watchKey = watchKey;
      this.fileModifiedOrCreatedCb = fileModifiedOrCreatedCb;
    }

    @Override
    public void run() {
      try {
        while ((watchKey = watchService.take()) != null) {
          for (var ev : watchKey.pollEvents()) {
            LOG.debug( // NOPMD
              "Received watch service event: kind='{}' context='{}'",
              ev.kind(),
              ev.context()
            );
            if (ev.context() != null) {
              fileModifiedOrCreatedCb.accept((Path) ev.context());
            }
          }
          watchKey.reset();
        }
      } catch (InterruptedException e) {
        LOG.debug("Worker thread was interrupted. Finishing execution", e);
      }
    }
  }
}
