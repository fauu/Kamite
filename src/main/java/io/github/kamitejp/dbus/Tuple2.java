package io.github.kamitejp.dbus;

import org.freedesktop.dbus.Tuple;

@SuppressWarnings("unused")
public final class Tuple2<A, B> extends Tuple {
  public final A a;
  public final B b;

  public Tuple2(A a, B b) {
    this.a = a;
    this.b = b;
  }
}
