package io.github.kamitejp.platform.agent;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
record AgentSentenceMessage(String sentence, String type) {}
