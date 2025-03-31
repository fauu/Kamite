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
          OCRConfigurationStatus.ReinitializingAfterAdapterTimeout,
          OCRConfigurationStatus.AdapterFailedFatally {
  @JsonTypeName("INITIALIZING")
  record Initializing(String msg) implements OCRConfigurationStatus {}

  @JsonTypeName("AVAILABLE")
  record Available() implements OCRConfigurationStatus {}

  @JsonTypeName("REINITIALIZING_AFTER_ADAPTER_TIMEOUT")
  record ReinitializingAfterAdapterTimeout(String msg) implements OCRConfigurationStatus {}

  @JsonTypeName("ADAPTER_FAILED_FATALLY")
  record AdapterFailedFatally(String msg) implements OCRConfigurationStatus {}
}

