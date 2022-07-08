package io.github.kamitejp.dbus;

import org.freedesktop.dbus.Tuple;

@SuppressWarnings("unused")
public final class Tuple4<A, B, C, D> extends Tuple {
  public final A a;
  public final B b;
  public final C c;
  public final D d;

  public Tuple4(A a, B b, C c, D d) {
    this.a = a;
    this.b = b;
    this.c = c;
    this.d = d;
  }
}
