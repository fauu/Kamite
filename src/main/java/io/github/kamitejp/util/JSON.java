package io.github.kamitejp.util;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

public final class JSON {
  private static final JsonMapper DEFAULT_MAPPER = createDefaultMapper();

  private JSON() {}

  public static JsonMapper mapper() {
    return DEFAULT_MAPPER;
  }

  private static JsonMapper createDefaultMapper() {
    return JsonMapper.builder()
      .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
      .addModule(new JavaTimeModule())
      .addModule(new Jdk8Module())
      .build();
  }
}
