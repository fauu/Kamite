package io.github.kamitejp.recognition;

import java.awt.image.BufferedImage;

import io.github.kamitejp.util.Result;

interface RemoteOCRAdapter extends OCRAdapter {
  Result<String, RemoteOCRRequestError> ocr(BufferedImage img);
}
