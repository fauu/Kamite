/*
 * Adapted from https://github.com/bramp/Connected-component-labelling. Changes:
 *   - Port from JavaScript.
 * For Kamite project license information, please see the COPYING.md file.
 *
 * The following is the license notice from the underlying work:
 *
 *  Copyright 2021 Andrew Brampton All Rights Reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package io.github.kamitejp.recognition;

@SuppressWarnings({"PMD.LocalVariableNamingConventions", "PMD.FormalParameterNamingConventions"})
public class ConnectedComponentExtractor {
  private static final int BACKGROUND = 255;
  private static final int UNSET      = 0;
  private static final int MARKED     = -1;

  private int[] in;
  private int w;
  private int h;
  private int max;
  private int[] pos;
  private int[] label;
  private int c = 1;

  public ConnectedComponent[] extract(int[] in, int w, int h) {
    if (w < 1) {
      throw new IllegalArgumentException(
        "Input width must be positive for conencted component extraction"
      );
    }
    if (h < 2) {
      throw new IllegalArgumentException(
        "Input height must be at least 2 for conencted component extraction"
      );
    }
    this.in = in;
    this.w = w;
    this.h = h;
    this.max = w * h;
    this.pos = new int[]{1, w + 1, w, w - 1, -1, -w - 1, -w, -w + 1};
    this.label = new int[in.length];

    memset(this.in, 0, w, BACKGROUND);
    memset(this.in, w * (h - 1), w, BACKGROUND);

    for (var y = 1; y < h - 1; y++) {
      var offset = y * w;
      this.in[offset] = BACKGROUND;
      this.in[offset + w - 1] = BACKGROUND;
    }

    memset(this.label, 0, max, UNSET);

    extract();

    return bounds();
  }

  private record TracerOutput(int T, int q) {}

  private TracerOutput tracer(int S, int p) {
    for (int d = 0; d < 8; d++) {
      var q = (p + d) % 8;
      var T = S + pos[q];
      if (T < 0 || T >= max) {
        continue;
      }
      if (in[T] != BACKGROUND) {
        return new TracerOutput(T, q);
      }
      if (label[T] > UNSET) {
        throw new IllegalStateException("Excepted `label` to hold a non-positive value");
      }
      label[T] = MARKED;
    }
    return new TracerOutput(S, -1);
  }

  private void contourTracing(int S, int C, boolean external) {
    var p = external ? 7 : 3;

    var tmp = tracer(S, p);
    var T2 = tmp.T;
    var q = tmp.q;

    label[S] = C;

    if (T2 == S) {
      return;
    }

    var Tnext = T2;
    var T = T2;

    var counter = 0;

    while (T != S || Tnext != T2) {
      if (counter++ >= max) { // NOPMD
        throw new IllegalStateException("Expected `counter` to be smaller than `max`");
      }
      label[Tnext] = C;

      T = Tnext;
      p = (q + 5) % 8;

      tmp = tracer(T, p);
      Tnext = tmp.T;
      q = tmp.q;
    }
  }

  private void extract() {
    var y = 1;
    do {
      var x = 0;
      do {
        var offset = y * w + x;
        if (in[offset] == BACKGROUND) {
          continue;
        }

        var traced = false;

        if (in[offset - w] == BACKGROUND && label[offset] == UNSET) {
          contourTracing(offset, c++, true);
          traced = true;
        }

        if (in[offset + w] == BACKGROUND && label[offset + w] == UNSET) {
          var n = label[offset - 1];
          if (label[offset] != UNSET) {
            n = label[offset];
          }
          if (n <= UNSET) {
            throw new IllegalStateException("Excepted `n` to hold a positive value");
          }
          contourTracing(offset, n, false);
          traced = true;
        }

        if (label[offset] == UNSET) {
          var n = label[offset - 1];
          if (traced) {
            throw new IllegalStateException("Excepted `traced` to be false");
          }
          if (n <= UNSET) {
            throw new IllegalStateException("Excepted `n` to hold a positive value");
          }
          label[offset] = n;
        }
      } while (x++ < (w - 1));
    } while (y++ < (h - 1));
  }

  private ConnectedComponent[] bounds() {
    var maxLabel = 0;
    for (var i = 0; i < label.length; i++) {
      if (label[i] > maxLabel) {
        maxLabel = label[i];
      }
    }

    var result = new ConnectedComponent[maxLabel + 1];
    result[0] = new ConnectedComponent(0, 0, 0, 0, 0);

    var offset = 0;
    for (var y = 0; y < h; y++) {
      for (var x = 0; x < w; x++) {
        var l = label[offset++];
        if (l <= 0) {
          continue;
        }
        if (result[l] != null) {
          var b = result[l];
          if (b.x2 < x) {
            b.x2 = x;
          }
          if (b.x1 > x) {
            b.x1 = x;
          }
          b.y2 = y;
        } else {
          result[l] = new ConnectedComponent(l, x, y, x, y);
        }
      }
    }

    return result;
  }

  private void memset(int[] arr, int offset, int n, int value) {
    for (var i = 0; i < n; i++) {
      arr[offset++] = value;
    }
  }
}
