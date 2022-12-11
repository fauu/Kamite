package io.github.kamitejp.event;

import java.util.Map;

record EventDOMElement(String tagName, Map<String, String> attributes) {}
