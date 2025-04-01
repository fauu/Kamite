package io.github.kamitejp.event;

import java.util.Map;

record EventDomElement(String tagName, Map<String, String> attributes) {}
