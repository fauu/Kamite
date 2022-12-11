package io.github.kamitejp.event;

import java.lang.invoke.MethodHandles;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.fasterxml.jackson.core.JsonProcessingException;

import io.github.kamitejp.config.Config;
import io.github.kamitejp.util.JSON;

public final class EventHandler {
  private static final Logger LOG = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  private final EventHandlerSource source;
  private final List<String> execCommand;
  private final EventHandlerCommand command;
  private final Consumer<Event> consumer;

  private EventHandler(
    List<String> execCommand,
    EventHandlerCommand command,
    Consumer<Event> consumer,
    EventHandlerSource source
  ) {
    this.execCommand = execCommand;
    this.command = command;
    this.consumer = consumer;
    this.source = source;
  }

  public EventHandlerSource getSource() {
    return source;
  }

  @SuppressWarnings("OptionalContainsCollection")
  public Optional<List<String>> getExecCommand() {
    return Optional.ofNullable(execCommand);
  }

  public Optional<EventHandlerCommand> getCommand() {
    return Optional.ofNullable(command);
  }

  public Optional<Consumer<Event>> getConsumer() {
    return Optional.ofNullable(consumer);
  }

  public static EventHandler internalOfConsumer(Consumer<Event> consumer) {
    return new EventHandler(null, null, consumer, EventHandlerSource.INTERNAL);
  }

  static Optional<EventHandler> fromUserConfigDefinition(Config.Events.Handler definition) {
    if (definition.exec() != null || definition.command() != null) {
      return Optional.of(new EventHandler(
        definition.exec(),
        EventHandlerCommand.fromConfigDefinition(definition.command()),
        null,
        EventHandlerSource.USER
      ));
    }
    return Optional.empty();
  }

  // QUAL: (DRY) Unify with the method in `prepareForDispatch.ts`?
  static List<String> fillExecCommandPlaceholders(List<String> command, Event event) {
    return command.stream().map(seg ->
      switch (seg) {
        case "{eventData}" -> {
          try {
            yield JSON.mapper().writeValueAsString(event);
          } catch (JsonProcessingException e) {
            LOG.debug("Error while serializing event data: {}", e::toString);
          }
          yield null;
        }
        default -> seg;
      }
    )
      .filter(Objects::nonNull)
      .toList();
  }
}

