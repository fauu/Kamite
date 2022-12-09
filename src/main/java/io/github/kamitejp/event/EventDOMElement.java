package io.github.kamitejp.event;

import java.util.Map;

public record EventDOMElement(String tagName, Map<String, String> attributes) {}
