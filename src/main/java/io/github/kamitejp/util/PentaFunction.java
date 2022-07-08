package io.github.kamitejp.util;

@FunctionalInterface
public interface PentaFunction<F, S, T, O, I, R> {
  R apply(F first, S second, T third, O fourth, I fifth);
}

