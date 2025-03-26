package io.github.kamitejp.recognition;

public sealed interface RemoteOCRError extends OCRError
  permits RemoteOCRError.Timeout,
          RemoteOCRError.SendFailed,
          RemoteOCRError.Unauthorized,
          RemoteOCRError.UnexpectedStatusCode,
          RemoteOCRError.Other {
  record Timeout() implements RemoteOCRError {}
  record SendFailed(String exceptionMessage) implements RemoteOCRError {}
  record Unauthorized() implements RemoteOCRError {}
  record UnexpectedStatusCode(int code) implements RemoteOCRError {}
  record Other(String error) implements RemoteOCRError {}
}