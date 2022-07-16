package io.github.kamitejp.platform.linux.wlroots.dependencies.slurp;

import io.github.kamitejp.geometry.Rectangle;

public sealed interface SlurpResult
  permits SlurpResult.ExecutionFailed,
          SlurpResult.Error,
          SlurpResult.Cancelled,
          SlurpResult.FormatDifferentFromExpected,
          SlurpResult.Point,
          SlurpResult.Area {
  record ExecutionFailed() implements SlurpResult {}
  record Error(String error) implements SlurpResult {}
  record Cancelled() implements SlurpResult {}
  record FormatDifferentFromExpected(String expected) implements SlurpResult {}
  record Point(io.github.kamitejp.geometry.Point point) implements SlurpResult {}
  record Area(Rectangle area) implements SlurpResult {}
}
