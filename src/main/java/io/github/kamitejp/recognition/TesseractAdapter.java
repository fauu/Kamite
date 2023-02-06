package io.github.kamitejp.recognition;

import java.awt.image.BufferedImage;
import java.util.Map;

import io.github.kamitejp.image.ImageOps;
import io.github.kamitejp.platform.process.ProcessHelper;
import io.github.kamitejp.platform.process.ProcessRunParams;
import io.github.kamitejp.util.Result;

public class TesseractAdapter {
  private static final String DPI = "70";
  private static final String OEM = "1";
  private static final Map<String, String> ENV = Map.of("OMP_THREAD_LIMIT", "1");
  private static final int PROCESS_TIMEOUT_MS = 5000;

  public Result<String, Void> ocr(BufferedImage img, String binPath, String lang, int psm) {
    var imgOS = ImageOps.encodeIntoByteArrayOutputStream(img);
    var res = ProcessHelper.run(
      ProcessRunParams.ofCmd(
        binPath,
        "stdin", "stdout",
        "-l", lang,
        "--dpi", DPI,
        "--oem", OEM,
        "--psm", Integer.toString(psm),
        "-c", "tessedit_create_hocr=1",
        "-c", "hocr_font-info=0"
      )
        .withEnv(ENV)
        .withInputBytes(imgOS.toByteArray())
        .withTimeout(PROCESS_TIMEOUT_MS)
    );
    if (res.didCompleteWithoutError()) {
      return Result.Ok("");
      // return new TesseractResult.HOCR(res.getStdout());
    } else if (res.didCompleteWithError()) {
      return Result.Ok("");
      // return new TesseractResult.Error(res.getStderr());
    } else if (res.didTimeOut()) {
      return Result.Ok("");
      // return new TesseractResult.TimedOut();
    } else {
      return Result.Ok("");
      // return new TesseractResult.ExecutionFailed();
    }
  }
}
