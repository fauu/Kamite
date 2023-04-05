package io.github.kamitejp.recognition;

import io.github.kamitejp.util.Result;

public interface StatefulOCRAdapter {
  Result<Void, String> init();
}
