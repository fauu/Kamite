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

public class EventHandler {
  private static final Logger LOG = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  private List<String> execCommand;
  private Command command;
  private Consumer<Event> consumer;

  public EventHandler(Consumer<Event> consumer) {
    this(null, null, consumer);
  }

  private EventHandler(List<String> execCommand, Command command, Consumer<Event> consumer) {
    this.execCommand = execCommand;
    this.command = command;
    this.consumer = consumer;
  }

  public Optional<List<String>> getExecCommand() {
    return Optional.ofNullable(this.execCommand);
  }

  public Optional<Command> getCommand() {
    return Optional.ofNullable(this.command);
  }

  public Optional<Consumer<Event>> getConsumer() {
    return Optional.ofNullable(this.consumer);
  }

  public static Optional<EventHandler> fromConfigDefinition(Config.Events.Handler definition) {
    if (definition.exec() != null || definition.command() != null) {
      return Optional.of(new EventHandler(
        definition.exec(),
        Command.fromConfigDefinition(definition.command()),
        null
      ));
    }
    return Optional.empty();
  }

  // QUAL: (DRY) Unify with the method in `prepareForDispatch.ts`?
  public static List<String> fillExecCommandPlaceholders(List<String> command, Event event) {
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

  record Command(String kind, String paramsJSON) {
    static Command fromConfigDefinition(Config.Events.Handler.Command command) {
      if (command == null) {
        return null;
      }
      return new Command(command.kind(), command.params());
    }
  };
}

