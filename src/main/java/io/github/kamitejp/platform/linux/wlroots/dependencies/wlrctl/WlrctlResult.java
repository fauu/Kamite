package io.github.kamitejp.platform.linux.wlroots.dependencies.wlrctl;

public sealed interface WlrctlResult
  permits WlrctlResult.ExecutionFailed,
          WlrctlResult.Error,
          WlrctlResult.Ok {
  record ExecutionFailed() implements WlrctlResult {}
  record Error(String error) implements WlrctlResult {}
  record Ok() implements WlrctlResult {}
}
