package io.github.kamitejp.dbus;

import java.lang.invoke.MethodHandles;
import java.util.Optional;
import java.util.function.Consumer;

import org.freedesktop.dbus.connections.impl.DBusConnection;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.interfaces.CallbackHandler;
import org.freedesktop.dbus.interfaces.DBusInterface;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.kamitejp.api.IncomingCommand;

public class DBusClient implements Receiver {
  private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private static final String BUS_NAME = "io.github.kamitejp";
  private static final String OBJECT_PATH = "/Receiver";

  private DBusConnection conn;
  private Consumer<DBusEvent> eventCb;

  public void init() throws DBusClientInitializationException {
    try {
      conn = DBusConnection.getConnection(DBusConnection.DBusBusType.SESSION);
      conn.requestBusName(BUS_NAME);
      conn.exportObject(this);
    } catch (DBusException e) {
      throw new DBusClientInitializationException(e);
    }
  }

  public void onEvent(Consumer<DBusEvent> eventCb) {
    this.eventCb = eventCb;
  }

  @Override
  public String getObjectPath() {
    return OBJECT_PATH;
  }

  @Override
  public void command(String kind) {
    command(kind, null);
  }

  @Override
  public void command(String kind, String paramsJSON) {
    if (eventCb == null) {
      LOG.warn("Received command through DBus but there is no handler registered");
      return;
    }
    eventCb.accept(new DBusEvent.CommandReceived(
      new IncomingCommand.Segmented(
        new IncomingCommand.Kind.Joined(kind),
        new IncomingCommand.Params.RawJSON(paramsJSON)
      )
    ));
  }

  public <A> void callWithCallback(
    DBusInterface object,
    String method,
    CallbackHandler<A> cbHandler,
    Object... params
  ) {
    if (conn == null) {
      return;
    }
    conn.callWithCallback(object, method, cbHandler, params);
  }

  public <I extends DBusInterface> Optional<I> getRemoteObject(
    String busName,
    String objectPath,
    Class<I> type
  ) {
    if (conn == null) {
      return Optional.empty();
    }
    try {
      return Optional.of(conn.getRemoteObject(busName, objectPath, type));
    } catch (Exception e) {
      LOG.error("Could not get remote object", e);
      return Optional.empty();
    }
  }

  public void destroy() {
    this.conn.disconnect();
  }
}
