/*
 * Adapted from Apache Flink - https://github.com/apache/flink. Changes:
 *   - Rename `Either` to `Result`, `Left` to `Ok`, `Right` to `Err`.
 * For Kamite project license information, please see the COPYING.md file.
 *
 * The following is the license notice from the underlying work:
 *
 *  Copyerr 2014-2021 The Apache Software Foundation
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not
 *  use this file except in compliance with the License. You may obtain a copy of
 *  the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package io.github.kamitejp.util;

import java.util.Optional;

public abstract class Result<L, R> {
  @SuppressWarnings("PMD.MethodNamingConventions")
  public static <L, R> Result<L, R> Ok(L value) {
    return new Ok<>(value);
  }

  @SuppressWarnings("PMD.MethodNamingConventions")
  public static <L, R> Result<L, R> Err(R value) {
    return new Err<>(value);
  }

  public abstract L get() throws IllegalStateException;

  public abstract R err() throws IllegalStateException;

  public final boolean isOk() {
    return getClass() == Ok.class;
  }

  public final boolean isErr() {
    return getClass() == Err.class;
  }

  @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
  public static <T, R> Result<T, R> of(Optional<T> optional, R errValue) {
    return optional.<Result<T, R>>map(Result::Ok).orElseGet(() -> Err(errValue));
  }

  public static class Ok<L, R> extends Result<L, R> {
    private L value;

    public Ok(L value) {
      this.value = value;
    }

    @Override
    public L get() {
      return value;
    }

    @Override
    public R err() {
      throw new IllegalStateException("Cannot retrieve Err value on a Ok");
    }

    public void setValue(L value) {
      this.value = value;
    }

    @Override
    public boolean equals(Object object) {
      return object instanceof final Ok<?, ?> other && value.equals(other.value);
    }

    @Override
    public int hashCode() {
      if (value == null) {
        return 0;
      }
      return value.hashCode();
    }

    @Override
    public String toString() {
      return "Ok(" + value == null ? "null" : value.toString() + ")";
    }

    public static <L, R> Ok<L, R> of(L ok) {
      return new Ok<>(ok);
    }
  }

  public static class Err<L, R> extends Result<L, R> {
    private R value;

    public Err(R value) {
      this.value = java.util.Objects.requireNonNull(value);
    }

    @Override
    public L get() {
      throw new IllegalStateException("Cannot retrieve Ok value on a Err");
    }

    @Override
    public R err() {
      return value;
    }

    public void setValue(R value) {
      this.value = value;
    }

    @Override
    public boolean equals(Object object) {
      return object instanceof final Err<?, ?> other && value.equals(other.value);
    }

    @Override
    public int hashCode() {
      return value.hashCode();
    }

    @Override
    public String toString() {
      return "Err(" + value.toString() + ")";
    }

    public static <L, R> Err<L, R> of(R err) {
      return new Err<>(err);
    }
  }
}
