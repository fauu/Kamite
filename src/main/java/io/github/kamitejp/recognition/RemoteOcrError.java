package io.github.kamitejp.recognition;

public sealed interface RemoteOcrError extends OcrError
  permits RemoteOcrError.Timeout,
          RemoteOcrError.SendFailed,
          RemoteOcrError.Unauthorized,
          RemoteOcrError.UnexpectedStatusCode,
          RemoteOcrError.Other {
  record Timeout() implements RemoteOcrError {}
  record SendFailed(String exceptionMessage) implements RemoteOcrError {}
  record Unauthorized() implements RemoteOcrError {}
  record UnexpectedStatusCode(int code) implements RemoteOcrError {}
  record Other(String error) implements RemoteOcrError {}
}
