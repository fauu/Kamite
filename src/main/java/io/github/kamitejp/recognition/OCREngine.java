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
          OCREngine.EasyOCROnline,
          OCREngine.HiveOCROnline,
          OCREngine.GLens,
          OCREngine.None {
  record Tesseract(String binPath) implements OCREngine {
    @Override
    public String toString() {
      return "Tesseract OCR";
    }

    @Override
    public boolean isRemote() {
      return false;
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
    public boolean isRemote() {
      return false;
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
    public boolean isRemote() {
      return true;
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
    public boolean isRemote() {
      return true;
    }

    @Override
    public String toString() {
      return "OCR.space (subengine %d)".formatted(subengine.toNumber());
    }
  }

  record EasyOCROnline(EasyOCRHFAdapter adapter) implements OCREngine {
    public static EasyOCROnline uninitialized() {
      return new EasyOCROnline(null);
    }

    public EasyOCROnline initialized() {
      if (adapter != null) {
        throw new IllegalStateException(
          "This OCREngine.EasyOCROnline instance is already initialized"
        );
      }
      return new EasyOCROnline(new EasyOCRHFAdapter());
    }

    @Override
    public boolean isRemote() {
      return true;
    }

    @Override
    public String toString() {
      return "EasyOCR Online (HF Space by tomofi)";
    }
  }

  record HiveOCROnline(HiveOCRHFAdapter adapter) implements OCREngine {
    public static HiveOCROnline uninitialized() {
      return new HiveOCROnline(null);
    }

    public HiveOCROnline initialized() {
      if (adapter != null) {
        throw new IllegalStateException(
          "This OCREngine.HiveOCROnline instance is already initialized"
        );
      }
      return new HiveOCROnline(new HiveOCRHFAdapter());
    }

    @Override
    public boolean isRemote() {
      return true;
    }

    @Override
    public String toString() {
      return "Hive OCR Online (HF Space by seaoctopusredchicken)";
    }
  }

  record GLens(GLensHFAdapter adapter) implements OCREngine {
    public static GLens uninitialized() {
      return new GLens(null);
    }

    public GLens initialized() {
      if (adapter != null) {
        throw new IllegalStateException(
          "This OCREngine.GLens instance is already initialized"
        );
      }
      return new GLens(new GLensHFAdapter());
    }

    @Override
    public boolean isRemote() {
      return true;
    }

    @Override
    public String toString() {
      return "G Lens (HF Space by akiraakirasharika)";
    }
  }

  record None() implements OCREngine {
    @Override
    public String toString() {
      return "None";
    }

    @Override
    public boolean isRemote() {
      return false;
    }
  }

  boolean isRemote();

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
      case EASYOCR_ONLINE  ->
        OCREngine.EasyOCROnline.uninitialized();
      case HIVEOCR_ONLINE  ->
        OCREngine.HiveOCROnline.uninitialized();
      case GLENS  ->
        OCREngine.GLens.uninitialized();
      case NONE ->
        new OCREngine.None();
    };

    return engine == null ? Result.Err(errorMessage) : Result.Ok(engine);
  }
}
