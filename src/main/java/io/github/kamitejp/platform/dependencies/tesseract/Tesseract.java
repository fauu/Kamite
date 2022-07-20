package io.github.kamitejp.platform.dependencies.tesseract;

import java.awt.image.BufferedImage;
import java.util.Map;

import io.github.kamitejp.platform.BaseSimpleDependency;
import io.github.kamitejp.platform.process.ProcessHelper;
import io.github.kamitejp.platform.process.ProcessRunParams;
import io.github.kamitejp.recognition.ImageOps;

public final class Tesseract extends BaseSimpleDependency {
  private static final String DPI = "70";
  private static final String OEM = "1";
  private static final Map<String, String> ENV = Map.of("OMP_THREAD_LIMIT", "1");

  public Tesseract() {
    super("tesseract", "Tesseract");
  }

  @Override
  public boolean checkIsAvailable() {
    var res = ProcessHelper.run(ProcessRunParams.ofCmd(BIN, "-v").withTimeout(1000));
    return res.didComplete() && res.getStdout().startsWith("tesseract ");
  }

  public TesseractResult ocr(BufferedImage img, TesseractModel model) {
    var imgOS = ImageOps.encodeIntoByteArrayOutputStream(img);
    var res = ProcessHelper.run(
      ProcessRunParams.ofCmd(
        BIN,
        "stdin", "stdout",
        "-l", model.lang,
        "--dpi", DPI,
        "--oem", OEM,
        "--psm", model.psm,
        "-c", "tessedit_create_hocr=1",
        "-c", "hocr_font-info=0"
      )
        .withEnv(ENV)
        .withInputBytes(imgOS.toByteArray())
        .withTimeout(3000)
    );
    if (!res.didComplete()) {
      return new TesseractResult.ExecutionFailed();
    } else if (res.didCompleteAndError()) {
      return new TesseractResult.Error(res.getStderr());
    } else {
      return new TesseractResult.HOCR(res.getStdout());
    }
  }
}
