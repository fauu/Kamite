package io.github.kamitejp.event;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import io.github.kamitejp.api.IncomingCommand;
import io.github.kamitejp.config.Config;
import io.github.kamitejp.platform.process.ProcessHelper;
import io.github.kamitejp.util.Executor;

public class EventManager {
  private Map<Class<? extends Event>, List<EventHandler>> handlerMap;

  private Consumer<IncomingCommand> commandCb;

  public EventManager(
    List<Config.Events.Handler> handlerDefinitions,
    Consumer<IncomingCommand> commandCb
  ) {
    setEventHandlers(handlerDefinitions);
    this.commandCb = commandCb;
  }

  public void setEventHandlers(List<Config.Events.Handler> handlerDefinitions) {
    if (handlerDefinitions == null) {
      return;
    }
    if (this.handlerMap == null) {
      this.handlerMap = new HashMap<>();
    }
    this.handlerMap.clear();
    for (var definition : handlerDefinitions) {
      var clazz = Event.EVENT_NAME_TO_CLASS.get(definition.on());
      if (clazz == null) {
        continue;
      }
      EventHandler.fromConfigDefinition(definition)
        .ifPresent(h -> registerHandler(clazz, h));
    }
  }

  public <T extends Event> void registerHandler(
    Class<T> eventClass, EventHandler handler
  ) {
    if (this.handlerMap == null) {
      this.handlerMap = new HashMap<>();
    }
    var handlerList = handlerMap.get(eventClass);
    if (handlerList == null) {
      var newHandlerList = new ArrayList<EventHandler>();
      newHandlerList.add(handler);
      handlerMap.put(eventClass, newHandlerList);
    } else {
      handlerList.add(handler);
    }
  }

  public void handle(Event event) {
    var handlers = handlerMap.get(event.getClass());
    if (handlers == null) {
      return;
    }
    for (var handler : handlers) {
      handler.getExecCommand().ifPresent(execCommand ->
        Executor.get().execute(() ->
          ProcessHelper.run(EventHandler.fillExecCommandPlaceholders(execCommand, event))
        )
      );
      handler.getCommand().ifPresent(command -> {
        // PERF: Could cache parsed params JSON
        commandCb.accept(new IncomingCommand.Segmented(
          new IncomingCommand.Kind.Joined(command.kind()),
          new IncomingCommand.Params.RawJSON(command.paramsJSON())
        ));
      });
      handler.getConsumer().ifPresent(c -> c.accept(event));
    }
  }
}
