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
          OCREngine.MangaOCROnline,
          OCREngine.OCRSpace,
          OCREngine.None {
  record Tesseract(String binPath) implements OCREngine {
    @Override
    public String toString() {
      return "Tesseract OCR";
    }
  }

  record MangaOCR(
    String customPythonPath, MangaOCRController controller
  ) implements OCREngine {
    public static MangaOCR uninitialized(String customPythonPath) {
      return new MangaOCR(customPythonPath, null);
    }

    public MangaOCR initialized(
      Platform platform, Consumer<MangaOCREvent> eventCb
    ) throws MangaOCRInitializationException {
      if (controller != null) {
        throw new IllegalStateException("This OCREngine.MangaOCR instance is already initialized");
      }
      return new MangaOCR(
        customPythonPath,
        new MangaOCRController(platform, customPythonPath, eventCb)
      );
    }

    @Override
    public void destroy() {
      controller.destroy();
    }

    @Override
    public String toString() {
      return "\"Manga OCR\"";
    }
  }

  record MangaOCROnline(MangaOCRHFAdapter adapter) implements OCREngine {
    public static MangaOCROnline uninitialized() {
      return new MangaOCROnline(null);
    }

    public MangaOCROnline initialized() {
      if (adapter != null) {
        throw new IllegalStateException(
          "This OCREngine.MangaOCROnline instance is already initialized"
        );
      }
      return new MangaOCROnline(new MangaOCRHFAdapter());
    }

    @Override
    public String toString() {
      return "\"Manga OCR\" Online (HF Space by Detomo)";
    }
  }

  record OCRSpace(
    String apiKey, OCRSpaceSubengine subengine, OCRSpaceAdapter adapter
  ) implements OCREngine {
    public static OCRSpace uninitialized(String apiKey, int subengineNumber) {
      return new OCRSpace(apiKey, OCRSpaceSubengine.fromNumber(subengineNumber), null);
    }

    public OCRSpace initialized() {
      if (adapter != null) {
        throw new IllegalStateException("This OCREngine.OCRSpace instance is already initialized");
      }
      return new OCRSpace(apiKey, subengine, new OCRSpaceAdapter(apiKey, subengine));
    }

    @Override
    public String toString() {
      return "OCR.space (subengine %d)".formatted(subengine.toNumber());
    }
  }

  record None() implements OCREngine {
    @Override
    public String toString() {
      return "None";
    }
  }

  default void destroy() {
    // Do nothing
  }

  static Result<OCREngine, String> uninitializedFromConfig(Config config) {
    OCREngine engine;
    String errorMessage = null;

    engine = switch (config.ocr().engine()) {
      case TESSERACT ->
        new OCREngine.Tesseract(config.ocr().tesseract().path());
      case MANGAOCR  ->
        OCREngine.MangaOCR.uninitialized(config.ocr().mangaocr().pythonPath());
      case MANGAOCR_ONLINE  ->
        OCREngine.MangaOCROnline.uninitialized();
      case OCRSPACE  -> {
        var apiKey = config.secrets().ocrspace();
        if (apiKey == null) {
          errorMessage = "Please provide the API key for OCR.space ('secrets.ocrspace')";
          yield null;
        }
        yield OCREngine.OCRSpace.uninitialized(
          config.secrets().ocrspace(), config.ocr().ocrspace().engine()
        );
      }
      case NONE ->
        new OCREngine.None();
    };

    return engine == null ? Result.Err(errorMessage) : Result.Ok(engine);
  }
}
