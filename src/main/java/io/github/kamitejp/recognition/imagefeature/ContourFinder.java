/* 
 * Adapted with original modifications from PContour - https://github.com/LingDong-/PContour.
 * For Kamite project license information, please see the COPYING.md file.
 *
 * The following is the license notice from the underlying work:
 *
 *  MIT License
 *
 *  Copyright (c) 2021 Lingdong Huang
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

package io.github.kamitejp.recognition.imagefeature;

import java.util.ArrayList;
import java.util.List;

import io.github.kamitejp.geometry.Point;

@SuppressWarnings({"PMD.LocalVariableNamingConventions", "PMD.FormalParameterNamingConventions"})
public class ContourFinder {
  private static final int N_PIXEL_NEIGHBOR = 8;

   // 0=background, 1=foreground, will be modified by the function
  public static List<Contour> find(int[] F, int w, int h) {
    var nbd = 1;
    var lnbd = 1;

    var contours = new ArrayList<Contour>();

    for (int i = 1; i < h - 1; i++){
      F[i * w] = 0;
      F[i * w + w - 1] = 0;
    }

    for (int i = 0; i < w; i++){
      F[i] = 0;
      F[w * h - 1 - i] = 0;
    }

    for (int i = 1; i < h - 1; i++) {
      lnbd = 1;
      for (int j = 1; j < w -1 ; j++) {
        var i2 = 0;
        var j2 = 0;

        if (F[i * w + j] == 0) {
          continue;
        }

        if (F[i * w + j] == 1 && F[i * w + j - 1] == 0) {
          nbd++;
          i2 = i;
          j2 = j - 1;
        } else if (F[i * w + j] >= 1 && F[i * w + j + 1] == 0) {
          nbd++;
          i2 = i;
          j2 = j + 1;
          if (F[i * w + j] > 1) {
            lnbd = F[i * w + j];
          }
        } else {
          if (F[i * w + j] != 1) {
            lnbd = Math.abs(F[i * w + j]);
          }
          continue;
        }

        var points = new ArrayList<Point>();
        points.add(new Point(j, i));
        var B = new Contour(points, nbd, j2 == j + 1 ? Contour.Type.HOLE : Contour.Type.OUTER);
        contours.add(B);

        var B0 = new Contour();
        for (int c = 0; c < contours.size(); c++){
          if (contours.get(c).id == lnbd){
            B0 = contours.get(c);
            break;
          }
        }
        if (B0.type == Contour.Type.HOLE){
          if (B.type == Contour.Type.HOLE){
            B.parent = B0.parent;
          } else {
            B.parent = lnbd;
          }
        } else {
          if (B.type == Contour.Type.HOLE){
            B.parent = lnbd;
          } else {
            B.parent = B0.parent;
          }
        }

        var i1 = -1; 
        var j1 = -1;
        int[] i1j1 = cwNon0(F, w, i, j, i2, j2, 0);
        if (i1j1 == null){
          F[i * w + j] = -nbd;
          if (F[i * w + j] != 1) {
            lnbd = Math.abs(F[i * w + j]);
          }
          continue;
        }
        i1 = i1j1[0];
        j1 = i1j1[1];

        i2 = i1;
        j2 = j1;
        var i3 = i;
        var j3 = j;

        while (true) {
          int[] i4j4 = ccwNon0(F, w, i3, j3, i2, j2, 1);
          int i4 = i4j4[0];
          int j4 = i4j4[1];

          contours.get(contours.size() - 1).points.add(new Point(j4, i4));

          if (F[i3 * w + j3 + 1] == 0) {
            F[i3 * w + j3] = -nbd;
          } else if (F[i3 * w + j3] == 1){
            F[i3 * w + j3] = nbd;
          }

          if (i4 == i && j4 == j && i3 == i1 && j3 == j1) {
            if (F[i * w + j] != 1) {
              lnbd = Math.abs(F[i * w + j]);
            }
            break;
          } else {
            i2 = i3;
            j2 = j3;
            i3 = i4;
            j3 = j4;
          }
        }
      }
    }
    return contours;
  }

  public static List<Point> approxPolySimple(List<Point> polyline) {
    var epsilon = 0.1f;
    if (polyline.size() <= 2){
      return polyline;
    }
    var ret = new ArrayList<Point>();
    ret.add(Point.from(polyline.get(0)));

    for (int i = 1; i < polyline.size() - 1; i++){
      var d = pointDistanceToSegment(
        polyline.get(i),
        polyline.get(i - 1),
        polyline.get(i + 1)
      );
      if (d > epsilon){
        ret.add(Point.from(polyline.get(i)));
      }
    }
    ret.add(Point.from(polyline.get(polyline.size() - 1)));
    return ret;
  }

  public static List<Point> approxPolyDP(List<Point> polyline, float epsilon) {
    if (polyline.size() <= 2){
      return polyline;
    }

    var dmax = 0.0f;
    int argmax = -1;
    for (int i = 1; i < polyline.size() - 1; i++){
      float d = pointDistanceToSegment(
        polyline.get(i), 
        polyline.get(0), 
        polyline.get(polyline.size() - 1)
      );
      if (d > dmax){
        dmax = d;
        argmax = i;
      }
    }

    var ret = new ArrayList<Point>();
    if (dmax > epsilon){
      var L = approxPolyDP(new ArrayList<Point>(polyline.subList(0, argmax + 1)), epsilon);
      var R = approxPolyDP(new ArrayList<Point>(polyline.subList(argmax, polyline.size())), epsilon);
      ret.addAll(L.subList(0, L.size() - 1));
      ret.addAll(R);
    } else {
      ret.add(Point.from(polyline.get(0)));
      ret.add(Point.from(polyline.get(polyline.size() - 1)));
    }
    return ret;
  }


  private static float pointDistanceToSegment(Point p, Point p0, Point p1) {
    var x = p.x();
    var y = p.y();

    var x1 = p0.x();
    var y1 = p0.y();

    var x2 = p1.x();
    var y2 = p1.y();

    var A = x - x1;
    var B = y - y1;
    var C = x2 - x1;
    var D = y2 - y1;

    var dot = A * C + B * D;
    var lenSq = C * C + D * D;
    var param = -1;
    if (lenSq != 0) {
      param = dot / lenSq;
    }

    float xx;
    float yy;
    if (param < 0) {
      xx = x1;
      yy = y1;
    } else if (param > 1) {
      xx = x2;
      yy = y2;
    } else {
      xx = x1 + param * C;
      yy = y1 + param * D;
    }

    var dx = x - xx;
    var dy = y - yy;

    return (float) Math.sqrt(dx * dx + dy * dy);
  }

  private static int[] neighborIDToIndex(int i, int j, int id) {
    var k = 0;
    var l = 0;
    switch (id) {
      case 0: k = i;     l = j + 1; break;
      case 1: k = i - 1; l = j + 1; break;
      case 2: k = i - 1; l = j;     break;
      case 3: k = i - 1; l = j - 1; break;
      case 4: k = i;     l = j - 1; break;
      case 5: k = i + 1; l = j - 1; break;
      case 6: k = i + 1; l = j;     break;
      case 7: k = i + 1; l = j + 1; break;
      default: throw new IllegalArgumentException();
    }
    return new int[]{k, l};
  }

  @SuppressWarnings("PMD.ControlStatementBraces")
  private static int neighborIndexToID(int i0, int j0, int i, int j){
    var di = i - i0;
    var dj = j - j0;
    if (di == 0 && dj == 1) return 0;
    if (di ==-1 && dj == 1) return 1;
    if (di ==-1 && dj == 0) return 2;
    if (di ==-1 && dj ==-1) return 3;
    if (di == 0 && dj ==-1) return 4;
    if (di == 1 && dj ==-1) return 5;
    if (di == 1 && dj == 0) return 6;
    if (di == 1 && dj == 1) return 7;
    return -1;
  }

  @SuppressWarnings("PMD.ReturnEmptyCollectionRatherThanNull")
  private static int[] ccwNon0(int[] F, int w, int i0, int j0, int i, int j, int offset){
    int id = neighborIndexToID(i0, j0, i, j);
    for (int k = 0; k < N_PIXEL_NEIGHBOR; k++){
      int kk = (k + id + offset + N_PIXEL_NEIGHBOR * 2) % N_PIXEL_NEIGHBOR;
      int[] ij = neighborIDToIndex(i0, j0, kk);
      if (F[ij[0] * w + ij[1]] != 0) {
        return ij;
      }
    }
    return null;
  }

  @SuppressWarnings("PMD.ReturnEmptyCollectionRatherThanNull")
  private static int[] cwNon0(int[] F, int w, int i0, int j0, int i, int j, int offset){
    int id = neighborIndexToID(i0, j0, i, j);
    for (int k = 0; k < N_PIXEL_NEIGHBOR; k++){
      int kk = (-k + id - offset + N_PIXEL_NEIGHBOR * 2) % N_PIXEL_NEIGHBOR;
      int[] ij = neighborIDToIndex(i0, j0, kk);
      if (F[ij[0] * w + ij[1]] != 0) {
        return ij;
      }
    }
    return null;
  }
}
