package io.github.kamitejp.recognition;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;

@JsonTypeInfo(
  use = JsonTypeInfo.Id.NAME,
  include = JsonTypeInfo.As.PROPERTY,
  property = "kind"
)
public sealed interface OCRConfigurationStatus
  permits OCRConfigurationStatus.Initializing,
          OCRConfigurationStatus.Available,
          OCRConfigurationStatus.TimedOutAndReinitializing,
          OCRConfigurationStatus.FailedFatally {
  @JsonTypeName("INITIALIZING")
  record Initializing(String msg) implements OCRConfigurationStatus {}

  @JsonTypeName("AVAILABLE")
  record Available() implements OCRConfigurationStatus {}

  @JsonTypeName("TIMED_OUT_AND_REINITIALIZING")
  record TimedOutAndReinitializing(String msg) implements OCRConfigurationStatus {}

  @JsonTypeName("FAILED_FATALLY")
  record FailedFatally(String msg) implements OCRConfigurationStatus {}
}

