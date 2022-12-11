package io.github.kamitejp.event;

import static java.util.stream.Collectors.toList;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import io.github.kamitejp.api.IncomingCommand;
import io.github.kamitejp.config.Config;
import io.github.kamitejp.platform.process.ProcessHelper;
import io.github.kamitejp.util.Executor;

public final class EventManager {
  private Map<Class<? extends Event>, List<EventHandler>> handlerMap;
  private List<String> handledEventNames;

  private final Consumer<IncomingCommand> commandCb;
  private final Consumer<List<String>> handledEventsChangedCb;

  public EventManager(
    List<Config.Events.Handler> handlerDefinitions,
    Consumer<IncomingCommand> commandCb,
    Consumer<List<String>> handledEventsChangedCb
  ) {
    this.commandCb = commandCb;
    this.handledEventsChangedCb = handledEventsChangedCb;
    setUserEventHandlers(handlerDefinitions);
  }

  private void init() {
    handlerMap = new HashMap<>(16);
    handledEventNames = new ArrayList<>(16);
  }

  public void setUserEventHandlers(List<Config.Events.Handler> handlerDefinitions) {
    if (handlerMap == null) {
      init();
    } else {
      clearUserEventHandlers();
    }
    if (handlerDefinitions != null) {
      for (var definition : handlerDefinitions) {
        var eventClass = Event.NAME_TO_CLASS.get(definition.on());
        if (eventClass == null) {
          continue;
        }
        EventHandler.fromUserConfigDefinition(definition)
          .ifPresent(handler -> doRegisterEventHandler(eventClass, handler));
      }
    }
    updateHandledEventNames();
  }

  public <T extends Event> void registerEventHandler(Class<T> eventClass, EventHandler handler) {
    doRegisterEventHandler(eventClass, handler);
    updateHandledEventNames();
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
      handler.getConsumer().ifPresent(consumer -> consumer.accept(event));
    }
  }

  private <T extends Event> void doRegisterEventHandler(
    Class<T> eventClass, EventHandler handler
  ) {
    if (handlerMap == null) {
      init();
    }
    var handlersForTheEvent = handlerMap.get(eventClass);
    if (handlersForTheEvent == null) {
      var newHandlerList = new ArrayList<EventHandler>(8);
      newHandlerList.add(handler);
      handlerMap.put(eventClass, newHandlerList);
    } else {
      handlersForTheEvent.add(handler);
    }
  }

  private void clearUserEventHandlers() {
    handlerMap.entrySet().removeIf(entry -> {
      var nonUserHandlers = entry.getValue().stream()
        .filter(handler -> handler.getSource() != EventHandlerSource.USER)
        .collect(toList());
      entry.setValue(nonUserHandlers);
      return nonUserHandlers.isEmpty();
    });
  }

  private void updateHandledEventNames() {
    var newNames = handlerMap.keySet().stream().map(Event.CLASS_TO_NAME::get).sorted().toList();
    if (newNames.equals(handledEventNames)) {
      return;
    }
    handledEventNames = newNames;
    handledEventsChangedCb.accept(newNames);
  }
}
