package io.github.kamitejp.recognition;

public sealed interface RemoteOCRRequestError
  permits RemoteOCRRequestError.Timeout,
          RemoteOCRRequestError.SendFailed,
          RemoteOCRRequestError.Unauthorized,
          RemoteOCRRequestError.UnexpectedStatusCode,
          RemoteOCRRequestError.Other {
  record Timeout() implements RemoteOCRRequestError {}
  record SendFailed(String exceptionMessage) implements RemoteOCRRequestError {}
  record Unauthorized() implements RemoteOCRRequestError {}
  record UnexpectedStatusCode(int code) implements RemoteOCRRequestError {}
  record Other(String error) implements RemoteOCRRequestError {}
}
