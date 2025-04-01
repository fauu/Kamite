package io.github.kamitejp.recognition;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;

@JsonTypeInfo(
  use = JsonTypeInfo.Id.NAME,
  include = JsonTypeInfo.As.PROPERTY,
  property = "kind"
)
public sealed interface OcrConfigurationStatus
  permits OcrConfigurationStatus.Initializing,
          OcrConfigurationStatus.Available,
          OcrConfigurationStatus.ReinitializingAfterAdapterTimeout,
          OcrConfigurationStatus.AdapterFailedFatally {
  @JsonTypeName("INITIALIZING")
  record Initializing(String msg) implements OcrConfigurationStatus {}

  @JsonTypeName("AVAILABLE")
  record Available() implements OcrConfigurationStatus {}

  @JsonTypeName("REINITIALIZING_AFTER_ADAPTER_TIMEOUT")
  record ReinitializingAfterAdapterTimeout(String msg) implements OcrConfigurationStatus {}

  @JsonTypeName("ADAPTER_FAILED_FATALLY")
  record AdapterFailedFatally(String msg) implements OcrConfigurationStatus {}
}

