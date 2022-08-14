package io.github.kamitejp.recognition;

import java.util.function.Consumer;

import io.github.kamitejp.config.Config;
import io.github.kamitejp.platform.MangaOCRController;
import io.github.kamitejp.platform.MangaOCREvent;
import io.github.kamitejp.platform.MangaOCRInitializationException;
import io.github.kamitejp.platform.Platform;
import io.github.kamitejp.util.Result;

public sealed interface OCREngine
  permits OCREngine.Tesseract,
          OCREngine.MangaOCR,
          OCREngine.OCRSpace,
          OCREngine.None {
  public record Tesseract(String binPath) implements OCREngine {}

  public record MangaOCR(MangaOCRController controller) implements OCREngine {
    public static MangaOCR uninitialized() {
      return new MangaOCR(null);
    }

    public MangaOCR initialized(
      Platform platform, Consumer<MangaOCREvent> eventCb
    ) throws MangaOCRInitializationException {
      if (controller != null) {
        throw new IllegalStateException("This OCREngine.MangaOCR instance is already initialized");
      }
      return new MangaOCR(new MangaOCRController(platform, eventCb));
    }
  }

  public record OCRSpace(String apiKey, OCRSpaceAdapter adapter) implements OCREngine {
    public static OCRSpace uninitialized(String apiKey) {
      return new OCRSpace(apiKey, null);
    }

    public OCRSpace initialized() {
      if (adapter != null) {
        throw new IllegalStateException("This OCREngine.OCRSpace instance is already initialized");
      }
      return new OCRSpace(apiKey, new OCRSpaceAdapter(apiKey));
    }
  }

  public record None() implements OCREngine {}

  default void destroy() {
    switch (this) {
      case OCREngine.MangaOCR engine -> engine.controller.destroy();
      case default -> {}
    }
  }

  default String displayName() {
    return switch (this) {
      case OCREngine.Tesseract ignored -> "Tesseract OCR";
      case OCREngine.MangaOCR ignored  -> "“Manga OCR”";
      case OCREngine.OCRSpace ignored  -> "OCR.space";
      case OCREngine.None ignored      -> "None";
    };
  }

  static Result<OCREngine, String> uninitializedFromConfig(Config config) {
    OCREngine engine;
    String errorMessage = null;

    engine = switch (config.ocr().engine()) {
      case TESSERACT ->
        new OCREngine.Tesseract(config.ocr().tesseract().path());
      case MANGAOCR  ->
        OCREngine.MangaOCR.uninitialized();
      case OCRSPACE  -> {
        var apiKey = config.secrets().ocrspace();
        if (apiKey == null) {
          errorMessage = "Please provide the API key for OCR.space ('secrets.ocrspace')";
          yield null;
        }
        yield OCREngine.OCRSpace.uninitialized(config.secrets().ocrspace());
      }
      case NONE ->
        new OCREngine.None();
    };

    return engine == null ? Result.Err(errorMessage) : Result.Ok(engine);
  }
}
