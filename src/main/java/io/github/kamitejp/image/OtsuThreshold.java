/*
 * Adapted with original modifications from otsu - https://github.com/cawfree/otsu.
 * For Kamite project license information, please see the COPYING.md file.
 *
 * The following is the license notice from the underlying work:
 *
 *  MIT License
 *  Copyright (c) 2020 Alexander Thomas
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in all
 *  copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 *  SOFTWARE.
 */

package io.github.kamitejp.image;

public final class OtsuThreshold {
  private OtsuThreshold() {}

  public static int of(int[] data) {
    int[] b = bins(data);
    int[] h = histogram(data, b);

    var vars = new float[b.length];
    var minVar = Float.POSITIVE_INFINITY;
    int minVarIdx = -1;
    for (int i = 0; i < vars.length; i++) {
      int s0 = 0;
      int e0 = i;
      int s1 = i;
      int e1 = h.length;

      var w0 = 1.0f / width(h, s0, e0);
      var w1 = 1.0f / width(h, s1, e1);

      var wb = weight(h, s0, e0, data.length);
      var vb = variance(h, b, s0, e0, mean(h, b, s0, e0, w0), w0);

      var wf = weight(h, s1, e1, data.length);
      var vf = variance(h, b, s1, e1, mean(h, b, s1, e1, w1), w1);

      var x = cross(wb, vb, wf, vf);

      var result = Float.isNaN(x) ? Float.POSITIVE_INFINITY : x;
      vars[i] = result;
      if (result <= minVar) {
        minVar = result;
        minVarIdx = i;
      }
    }

    return b[minVarIdx];
  }

  private static int[] histogram(int[] data, int[] bins) {
    var result = new int[bins.length];
    for (var datum : data) {
      for (var j = 0; j < bins.length; j++) {
        if (bins[j] == datum) {
          result[j]++;
          break;
        }
      }
    }
    return result;
  }

  private static int width(int[] histogram, int start, int end) {
    var v = 0;
    for (var i = start; i < end; i++) {
      v += histogram[i];
    }
    return v;
  }

  private static int[] bins(int[] data) {
    // ASSUMPTION: data range is 0–255
    var hits = new boolean[256];
    var hitCount = 0;
    for (var datum : data) {
      if (hits[datum] == false) {
        hits[datum] = true;
        hitCount++;
      }
    }
    var result = new int[hitCount];
    var i = 0;
    for (var j = 0; j < hits.length; j++) {
      if (hits[j] == true) {
        result[i++] = j;
      }
    }
    return result;
  }

  private static float weight(int[] histogram, int s, int e, int total) {
    var v = 0;
    for (var i = s; i < e; i++) {
      v += histogram[i];
    }
    return (float) v / total;
  }

  private static float mean(int[] histogram, int[] bins, int s, int e, float width) {
    var v = 0;
    for (var i = s; i < e; i++) {
      v += histogram[i] * bins[i];
    }
    return v * width;
  }

  private static float variance(int[] histogram, int[] bins, int s, int e, float mean, float width) {
    var v = 0;
    for (var i = s; i < e; i++) {
      var d = bins[i] - mean;
      v += d * d * histogram[i];
    }
    return v * width;
  }

  private static float cross(float wb, float vb, float wf, float vf) {
    return wb * vb + wf * vf;
  }
}
