package io.github.kamitejp.platform.linux.xorg.dependencies.slop;

import io.github.kamitejp.geometry.Rectangle;

public sealed interface SlopResult
  permits SlopResult.ExecutionFailed,
          SlopResult.Error,
          SlopResult.Cancelled,
          SlopResult.FormatDifferentFromExpected,
          SlopResult.ZeroArea,
          SlopResult.Area {
  record ExecutionFailed() implements SlopResult {}
  record Error(String error) implements SlopResult {}
  record Cancelled() implements SlopResult {}
  record FormatDifferentFromExpected(String expected) implements SlopResult {}
  record ZeroArea() implements SlopResult {}
  record Area(Rectangle area) implements SlopResult {}
}
