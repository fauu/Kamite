package io.github.kamitejp.recognition;

// import java.util.function.Consumer;
//
// import io.github.kamitejp.platform.MangaOCRController;
// import io.github.kamitejp.platform.MangaOCREvent;
// import io.github.kamitejp.platform.MangaOCRInitializationException;
// import io.github.kamitejp.platform.Platform;
import io.github.kamitejp.util.Result;

public sealed interface OCREngine
  permits OCREngine.Tesseract,
          // OCREngine.MangaOCR,
          OCREngine.MangaOCROnline {
          // OCREngine.OCRSpace,
          // OCREngine.EasyOCROnline,
          // OCREngine.HiveOCROnline,
          // OCREngine.GLens,
          // OCREngine.None {
  final class Tesseract implements OCREngine {
    public final String binPath; // XXX: public
    private TesseractAdapter adapter;

    public Tesseract(OCREngineParams.Tesseract params) {
      this.binPath = params.binPath();
    }

    @Override
    public Result<Void, String> init() {
      if (adapter == null) {
        this.adapter = new TesseractAdapter();
      }
      return Result.Ok(null);
    }

    @Override
    public String toString() {
      return "Tesseract OCR";
    }

    @Override
    public boolean isRemote() {
      return false;
    }

    public TesseractAdapter getAdapter() {
      return adapter;
    }
  }

  // record MangaOCR(
  //   String customPythonPath, MangaOCRController controller
  // ) implements OCREngine {
  //   public static MangaOCR uninitialized(String customPythonPath) {
  //     return new MangaOCR(customPythonPath, null);
  //   }
  //
  //   public MangaOCR initialized(
  //     Platform platform, Consumer<MangaOCREvent> eventCb
  //   ) throws MangaOCRInitializationException {
  //     if (controller != null) {
  //       throw new IllegalStateException("This OCREngine.MangaOCR instance is already initialized");
  //     }
  //     return new MangaOCR(
  //       customPythonPath,
  //       new MangaOCRController(platform, customPythonPath, eventCb)
  //     );
  //   }
  //
  //   @Override
  //   public boolean isRemote() {
  //     return false;
  //   }
  //
  //   @Override
  //   public void destroy() {
  //     controller.destroy();
  //   }
  //
  //   @Override
  //   public String toString() {
  //     return "\"Manga OCR\"";
  //   }
  // }

  final class MangaOCROnline implements OCREngine {
    private MangaOCRHFAdapter adapter;

    @Override
    public Result<Void, String> init() {
      if (adapter == null) {
        this.adapter = new MangaOCRHFAdapter();
      }
      return Result.Ok(null);
    }

    @Override
    public boolean isRemote() {
      return true;
    }

    @Override
    public String toString() {
      return "\"Manga OCR\" Online (HF Space by Detomo)";
    }

    public MangaOCRHFAdapter getAdapter() {
      return adapter;
    }
  }

  // record OCRSpace(
  //   String apiKey, OCRSpaceSubengine subengine, OCRSpaceAdapter adapter
  // ) implements OCREngine {
  //   public static OCRSpace uninitialized(String apiKey, int subengineNumber) {
  //     return new OCRSpace(apiKey, OCRSpaceSubengine.fromNumber(subengineNumber), null);
  //   }
  //
  //   public OCRSpace initialized() {
  //     if (adapter != null) {
  //       throw new IllegalStateException("This OCREngine.OCRSpace instance is already initialized");
  //     }
  //     return new OCRSpace(apiKey, subengine, new OCRSpaceAdapter(apiKey, subengine));
  //   }
  //
  //   @Override
  //   public boolean isRemote() {
  //     return true;
  //   }
  //
  //   @Override
  //   public String toString() {
  //     return "OCR.space (subengine %d)".formatted(subengine.toNumber());
  //   }
  // }
  //
  // record EasyOCROnline(EasyOCRHFAdapter adapter) implements OCREngine {
  //   public static EasyOCROnline uninitialized() {
  //     return new EasyOCROnline(null);
  //   }
  //
  //   public EasyOCROnline initialized() {
  //     if (adapter != null) {
  //       throw new IllegalStateException(
  //         "This OCREngine.EasyOCROnline instance is already initialized"
  //       );
  //     }
  //     return new EasyOCROnline(new EasyOCRHFAdapter());
  //   }
  //
  //   @Override
  //   public boolean isRemote() {
  //     return true;
  //   }
  //
  //   @Override
  //   public String toString() {
  //     return "EasyOCR Online (HF Space by tomofi)";
  //   }
  // }
  //
  // record HiveOCROnline(HiveOCRHFAdapter adapter) implements OCREngine {
  //   public static HiveOCROnline uninitialized() {
  //     return new HiveOCROnline(null);
  //   }
  //
  //   public HiveOCROnline initialized() {
  //     if (adapter != null) {
  //       throw new IllegalStateException(
  //         "This OCREngine.HiveOCROnline instance is already initialized"
  //       );
  //     }
  //     return new HiveOCROnline(new HiveOCRHFAdapter());
  //   }
  //
  //   @Override
  //   public boolean isRemote() {
  //     return true;
  //   }
  //
  //   @Override
  //   public String toString() {
  //     return "Hive OCR Online (HF Space by seaoctopusredchicken)";
  //   }
  // }

  public Result<Void, String> init();

  boolean isRemote();

  default void destroy() {
    // Do nothing
  }

  // XXX
  // static Result<OCREngine, String> uninitializedFromConfig(
  //   Config.OCREngine configEngine, Config.OCR.Configuration config
  // ) {
  //   OCREngine engine;
  //   String errorMessage = null;
  //
  //   engine = switch (configEngine) {
  //     case TESSERACT ->
  //       OCREngine.Tesseract.uninitialized(config.path());
      // case MANGAOCR  ->
      //   OCREngine.MangaOCR.uninitialized(config.ocr().mangaocr().pythonPath());
      // case MANGAOCR_ONLINE  ->
      //   OCREngine.MangaOCROnline.uninitialized();
      // case OCRSPACE  -> {
      //   var apiKey = config.secrets().ocrspace();
      //   if (apiKey == null) {
      //     errorMessage = "Please provide the API key for OCR.space ('secrets.ocrspace')";
      //     yield null;
      //   }
      //   yield OCREngine.OCRSpace.uninitialized(
      //     config.secrets().ocrspace(), config.ocr().ocrspace().engine()
      //   );
      // }
      // case EASYOCR_ONLINE  ->
      //   OCREngine.EasyOCROnline.uninitialized();
      // case HIVEOCR_ONLINE  ->
      //   OCREngine.HiveOCROnline.uninitialized();
      //  case GLENS  ->
      //    OCREngine.GLens.uninitialized();
  //     case NONE ->
  //       new OCREngine.None();
  //     default ->
  //       throw new IllegalStateException("XXX Unimplemented");
  //   };
  //
  //   return engine == null ? Result.Err(errorMessage) : Result.Ok(engine);
  // }
}
