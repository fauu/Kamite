package io.github.kamitejp.util;

import java.lang.reflect.ParameterizedType;

public final class Reflection {
  private Reflection() {}

  public static Class<?> extractNthTypeParameter(Class<?> configClass, int n) {
    var genericSuperclass = configClass.getGenericSuperclass();

    if (!(genericSuperclass instanceof ParameterizedType)) {
      throw new IllegalArgumentException("Class is not parameterized");
    }

    var paramType = (ParameterizedType) genericSuperclass;
    var typeArgs = paramType.getActualTypeArguments();

    if (typeArgs.length <= n) {
      throw new IllegalArgumentException("Class has fewer than %d type parameters".formatted(n));
    }

    var nthArg = typeArgs[n];

    if (nthArg instanceof Class) {
      return (Class<?>) nthArg;
    } else if (nthArg instanceof ParameterizedType) {
      return (Class<?>) ((ParameterizedType) nthArg).getRawType();
    }

    throw new IllegalArgumentException("N-th type parameter is not a Class");
  }
}
