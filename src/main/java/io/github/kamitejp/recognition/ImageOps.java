package io.github.kamitejp.recognition;

import java.awt.Color;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedList;
import java.util.Queue;

import javax.imageio.ImageIO;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.kamitejp.geometry.Point;
import io.github.kamitejp.geometry.Rectangle;
import io.github.kamitejp.util.PentaFunction;
import io.github.kamitejp.util.TriFunction;

public final class ImageOps {
  private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private static final String DEFAULT_IMAGE_FORMAT = "png";
  private static final Color DEFAULT_BG_COLOR = Color.WHITE;

  private ImageOps() {}

  public static BufferedImage copied(BufferedImage img) {
    return copied(img, img.getType());
  }

  public static BufferedImage copied(BufferedImage img, int type) {
    var ret = new BufferedImage(img.getWidth(), img.getHeight(), type);
    var gfx = ret.createGraphics();
    gfx.drawImage(img, 0, 0, null);
    gfx.dispose();
    return ret;
  }

  public static BufferedImage withoutAlphaChannel(BufferedImage img) {
    var ret = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_RGB);
    var gfx = ret.createGraphics();
    gfx.setColor(DEFAULT_BG_COLOR);
    gfx.fillRect(0, 0, ret.getWidth(), ret.getHeight());
    gfx.drawImage(img, 0, 0, ret.getWidth(), ret.getHeight(), null);
    gfx.dispose();
    return ret;
  }

  public static BufferedImage cropped(BufferedImage img, Rectangle rect) {
    var res = new BufferedImage(
      rect.getWidth(),
      rect.getHeight(),
      BufferedImage.TYPE_INT_RGB
    );
    var croppedGfx = res.getGraphics();
    croppedGfx.drawImage(
      img,
      0, 0, rect.getWidth(), rect.getHeight(),
      rect.getLeft(), rect.getTop(), rect.getRight(), rect.getBottom(), 
      null
    );
    croppedGfx.dispose();
    return res;
  }

  public static BufferedImage withBorder(BufferedImage img, Color color, int w) {
    var ret = new BufferedImage(img.getWidth() + 2 * w, img.getHeight() + 2 * w, img.getType());
    var gfx = ret.createGraphics();
    gfx.setColor(color);
    gfx.fillRect(0, 0, ret.getWidth(), ret.getHeight());
    gfx.drawImage(img, w, w, img.getWidth(), img.getHeight(), null);
    gfx.dispose();
    return ret;
  }

  public static BufferedImage eroded(BufferedImage img, int radiusX, int radiusY) {
    int w = img.getWidth();
    int h = img.getHeight();
    var result = new BufferedImage(w, h, img.getType());
    for (int y = 0; y < h; y++) {
      for (int x = 0; x < w; x++) {
        result.setRGB(x, y, getMinPixel(img, x, y, radiusX, radiusY));
      }
    }
    return result;
  }

  private static int getMinPixel(BufferedImage img, int x, int y, int rx, int ry) {
    int minR = 255;
    int minG = 255;
    int minB = 255;

    var w = img.getWidth();
    var h = img.getHeight();
    var iStart = -ry / 2;
    var iEnd = -iStart;
    var jStart = -rx / 2;
    var jEnd = -jStart;
    if (iStart == 0) {
      iEnd = 1;
    }
    if (jStart == 0) {
      jEnd = 1;
    }
    for (int i = iStart; i < iEnd; i++) {
      for (int j = jStart; j < jEnd; j++) {
        var xx = x + j;
        var yy = y + i;
        if (xx >= 0 && xx < w && yy >= 0 && yy < h && i * i + j * j < rx * ry) {
          var px = img.getRGB(x + j, y + i);
          minR = Math.min(minR, r(px));
          minG = Math.min(minG, g(px));
          minB = Math.min(minB, b(px));
        }
      }
    }

    return rgbInt(minR, minG, minB);
  }

  public static void threshold(BufferedImage img, int min, int max) {
    transformPixels(img, (r, g, b) -> {
      var grayLevel = rgbGrayLevel(r, g, b);
      int px = 0x000000;
      if (grayLevel > max) {
        px = 0xFFFFFF;
      } else if (grayLevel > min) {
        px = rgbInt(grayLevel, grayLevel, grayLevel);
      }
      return px;
    });
  }

  public static BufferedImage scaled(BufferedImage img, float scale) {
    var w = (int) (img.getWidth() * scale);
    var h = (int) (img.getHeight() * scale);
    return scaled(img, w , h);
  }

  public static BufferedImage scaled(BufferedImage img, int w, int h) {
    var scaled = new BufferedImage(w, h, img.getType());

    var gfx = scaled.createGraphics();
    gfx.setRenderingHint(
      RenderingHints.KEY_INTERPOLATION, 
      RenderingHints.VALUE_INTERPOLATION_BILINEAR
    );
    gfx.setRenderingHint(
      RenderingHints.KEY_RENDERING,
      RenderingHints.VALUE_RENDER_QUALITY
    );
    gfx.drawImage(img, 0, 0, w, h, null);
    gfx.dispose();

    return scaled;
  }

  public static BufferedImage blurred(BufferedImage img, int blurFactor) {
    var downscaled = scaled(img, 1.0f / blurFactor);
    return scaled(downscaled, img.getWidth(), img.getHeight());
  }

  public static void sharpen(BufferedImage img, float amount, int threshold, int blurFactor) {
    var blurred = blurred(img, blurFactor);
    transformPixels(img, (r, g, b, x, y) -> {
      var blurredPx = blurred.getRGB(x, y);
      var br = r(blurredPx);
      var bg = g(blurredPx);
      var bb = b(blurredPx);

      if (Math.abs(r - br) >= threshold) {
        r = (int) (amount * (r - br) + r);
        r = Math.max(Math.min(r, 255), 0);
      }
      if (Math.abs(g - bg) >= threshold) {
        g = (int) (amount * (g - bg) + g);
        g = Math.max(Math.min(g, 255), 0);
      }
      if (Math.abs(b - bb) >= threshold) {
        b = (int) (amount * (b - bb) + b);
        b = Math.max(Math.min(b, 255), 0);
      }

      return rgbInt(r, g, b);
    });
  }

  public static void negate(BufferedImage img) {
    transformPixels(img, (r, g, b) -> {
      return rgbInt(255 - r, 255 - g, 255 - b);
    });
  }

  public static boolean isDarkDominated(BufferedImage img) {
    return isDarkDominated(img, null);
  }

  public static boolean isDarkDominated(BufferedImage img, Rectangle area) {
    var iStart = 0;
    var iEnd = img.getHeight();
    var jStart = 0;
    var jEnd = img.getWidth();
    if (area != null) {
      iStart = Math.max(iStart, area.getTop());
      iEnd = Math.min(iEnd, area.getBottom());
      jStart = Math.max(jStart, area.getLeft());
      jEnd = Math.min(jEnd, area.getRight());
    }

    // NOTE: An ad-hoc heuristic, mostly untested
    var darkBalance = 0;
    for (int y = iStart; y < iEnd; y++) {
      for (int x = jStart; x < jEnd; x++) {
        var px = img.getRGB(x, y);
        if (rgbLuminance(r(px), g(px), b(px)) <= 128) {
          darkBalance++;
        } else {
          darkBalance--;
        }
      }
    }

    return darkBalance > 0;
  }

  public static boolean hasBusyEdges(BufferedImage img) {
    final var edgeWidth = 3;
    var numNotBWPixels = 0;
    var numPixelsCounted = 0;
    var w = img.getWidth();
    var h = img.getHeight();
    var imgArr = toArray(img);
    for (int y = 0; y < h; y++, numPixelsCounted++) {
      var skip = y >= edgeWidth && y < h - edgeWidth;
      for (int x = 0; x < w; x++, numPixelsCounted++) {
        if (x >= edgeWidth && skip) {
          x = w - edgeWidth;
          skip = false;
        }
        int px = arrayPixelAt(imgArr, w, x, y);
        var grayLevel = rgbGrayLevel(r(px), g(px), b(px));
        if (grayLevel > 30 && grayLevel < 230) {
          numNotBWPixels++;
        }
      }
    }
    // NOTE: An ad-hoc heuristic, mostly untested
    return ((float) numNotBWPixels / numPixelsCounted) > 0.3;
  }

  public static boolean isMostlyColorless(BufferedImage img) {
    final var threshold = 20;
    var numColorfulPixels = 0;
    var imgArr = toArray(img);
    for (var px : imgArr) {
      var r = r(px);
      var g = g(px);
      var b = b(px);
      if (
        Math.abs(r - g) > threshold
        || Math.abs(r - b) > threshold
        || Math.abs(g - b) > threshold
      ) {
        numColorfulPixels++;
      }
    }

    // NOTE: An ad-hoc heuristic, mostly untested
    return ((float) numColorfulPixels / imgArr.length) < 0.1;
  }

  public static void toGrayscale(BufferedImage img) {
    transformPixels(img, (r, g, b) -> {
      var grayLevel = rgbGrayLevel(r, g, b);
      return rgbInt(grayLevel, grayLevel, grayLevel);
    });
  }

  public static BufferedImage fromBytes(byte[] bytes) {
    try {
      return ImageIO.read(new ByteArrayInputStream(bytes));
    } catch (IOException e) {
      e.printStackTrace();
      LOG.error("Expection while creating an image out of bytes", e);
    }
    return null;
  }

  public static BufferedImage withWhiteFloodFilledBackground(
    BufferedImage img,
    int numEdgeFloodPoints,
    int threshold
  ) {
    var imgArr = toGrayArray(img);

    var w = img.getWidth();
    var h = img.getHeight();

    for (int i = 0; i <= numEdgeFloodPoints; i++) {
      var skip = i > 0 && i < numEdgeFloodPoints;
      for (int j = 0; j <= numEdgeFloodPoints; j++) {
        if (j > 1 && skip) {
          j = numEdgeFloodPoints;
          skip = false;
        }
        var x = (int) (((float) j / numEdgeFloodPoints) * w) - 1;
        if (x < 0) {
          x = 0;
        }
        var y = (int) (((float) i / numEdgeFloodPoints) * h) - 1;
        if (y < 0) {
          y = 0;
        }
        if (arrayPixelAt(imgArr, w, x, y) < 245) {
          floodFill(imgArr, w, h, x, y, Color.WHITE, threshold);
        }
      }
    }

    return grayArrayToBufferedImage(imgArr, w, h);
  }

  public static void floodFill(
    int[] img,
    int w,
    int h,
    int x,
    int y,
    Color color,
    int threshold
  ) {
    var srcColor = arrayPixelAt(img, w, x, y);
    var hits = new boolean[h][w];

    Queue<Point> queue = new LinkedList<>();
    queue.add(new Point(x, y));

    while (!queue.isEmpty()) {
      var p = queue.remove();
      if (doFloodFill(img, w, h, hits, p.x() , p.y(), srcColor, color.getRGB(), threshold)) {
        queue.add(new Point(p.x(),     p.y() - 1)); 
        queue.add(new Point(p.x(),     p.y() + 1)); 
        queue.add(new Point(p.x() - 1, p.y())); 
        queue.add(new Point(p.x() + 1, p.y())); 
      }
    }
  }

  private static boolean doFloodFill(
    int[] img,
    int w,
    int h,
    boolean[][] hits,
    int x,
    int y,
    int srcColor,
    int tgtColor,
    int threshold
  ) {
    if (y < 0 || x < 0 || y >= h || x >= w) {
      return false;
    }
    if (hits[y][x]) {
      return false;
    }

    // ASSUMPTION: `img` is grayscale
    var srcB = srcColor & 0xFF;
    var curB = arrayPixelAt(img, w, x, y) & 0xFF;
    if (Math.abs(srcB - curB) > threshold) {
      return false;
    }

    arraySetPixelAt(img, w, x, y, tgtColor);
    hits[y][x] = true;

    return true;
  }

  public static BufferedImage arrayToBufferedImage(byte[] arr, int w, int h) {
    int[] pixels = new int[arr.length / 3];
    for (var i = 0; i < pixels.length; i++) {
      var s = i * 3;
      pixels[i] = (arr[s] & 0xFF) << 16 | (arr[s + 1] & 0xFF) << 8 | (arr[s + 2] & 0xFF);
    }
    return arrayToBufferedImage(pixels, w, h);
  }

  public static BufferedImage arrayToBufferedImage(int[] srcArr, int w, int h) {
    var img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
    int[] destArr = ((DataBufferInt) img.getRaster().getDataBuffer()).getData();
    System.arraycopy(srcArr, 0, destArr, 0, destArr.length);
    return img;
  }

  public static BufferedImage grayArrayToBufferedImage(int[] srcArr, int w, int h) {
    var img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
    int[] destArr = ((DataBufferInt) img.getRaster().getDataBuffer()).getData();
    for (var i = 0; i < destArr.length; i++) {
      var p = srcArr[i];
      destArr[i] = rgbInt(p, p, p);
    }
    return img;
  }

  public static void arrayToGrayArray(int[] arr) {
    for (var i = 0; i < arr.length; i++) {
      var px = arr[i];
      arr[i] = rgbGrayLevel(r(px), g(px), b(px));
    }
  }

  public static void grayArrayToArray(int[] arr) {
    for (var i = 0; i < arr.length; i++) {
      var px = arr[i];
      arr[i] = rgbInt(px, px, px);
    }
  }

  public static void grayArrayToBinaryArray(int[] arr) {
    for (var i = 0; i < arr.length; i++) {
      arr[i] = arr[i] == 0 ? 0 : 1;
    }
  }

  public static int[] maskImageToBinaryArray(BufferedImage img) {
    var result = toArray(img);
    for (int i = 0; i < result.length; i++) {
      result[i] = (result[i] & 0xFF) == 0 ? 0 : 1;
    }
    return result;
  }

  public static void otsuThreshold(int[] img) {
    otsuThreshold(img, 0);
  }

  public static void otsuThreshold(int[] img, int bias) {
    int threshold = OtsuThreshold.of(img);
    for (int i = 0; i < img.length; i++) {
      img[i] = img[i] + bias < threshold ? 0 : 255;
    }
  }

  public static ByteArrayOutputStream encodeIntoByteArrayOutputStream(BufferedImage img) {
    try {
      var imgOS = new ByteArrayOutputStream();
      ImageIO.write(img, DEFAULT_IMAGE_FORMAT, imgOS);
      return imgOS;
    } catch (IOException e) {
      throw new RuntimeException("Exeption while writing image to a ByteArrayOutputStream", e);
    }
  }

  public static String convertToBase64(BufferedImage img) {
    var imgOS = new ByteArrayOutputStream();
    try (var b64OS = Base64.getEncoder().wrap(imgOS)) {
      ImageIO.write(img, DEFAULT_IMAGE_FORMAT, b64OS);
      return imgOS.toString();
    } catch (IOException e) {
      throw new RuntimeException("Expection while encoding an image as base64", e);
    }
  }

  private static double rgbLuminance(double r, double g, double b) {
    return 0.2126 * r + 0.7152 * g + 0.0722 * b;
  }

  private static int[] toArray(BufferedImage img) {
    // ASSUMPTION: img is of TYPE_INT_RGB
    var backingArr = ((DataBufferInt) img.getRaster().getDataBuffer()).getData();
    return Arrays.copyOf(backingArr, backingArr.length);
  }

  public static int[] toGrayArray(BufferedImage img) {
    var result = toArray(img);
    for (int i = 0; i < result.length; i++) {
      var px = result[i];
      result[i] = rgbGrayLevel(r(px), g(px), b(px));
    }
    return result;
  }

  public static int arrayPixelAt(int[] arr, int w, int x, int y) {
    return arr[y * w + x];
  }

  public static void arraySetPixelAt(int[] arr, int w, int x, int y, int px) {
    arr[y * w + x] = px;
  }

  private static void transformPixels(
      BufferedImage img,
      TriFunction<Integer, Integer, Integer, Integer> fn) {
    // ASSUMPTION: `img` is of TYPE_INT_RGB
    int[] imgArr = ((DataBufferInt) img.getRaster().getDataBuffer()).getData();
    for (var i = 0; i < imgArr.length; i++) {
      var px = imgArr[i];
      imgArr[i] = fn.apply(r(px), g(px), b(px));
    }
  }

  private static void transformPixels(
      BufferedImage img,
      PentaFunction<Integer, Integer, Integer, Integer, Integer, Integer> fn) {
    // ASSUMPTION: `img` is of TYPE_INT_RGB
    int[] imgArr = ((DataBufferInt) img.getRaster().getDataBuffer()).getData();
    var w = img.getWidth();
    for (var i = 0; i < imgArr.length; i++) {
      var px = imgArr[i];
      imgArr[i] = fn.apply(r(px), g(px), b(px), i % w, i / w);
    }
  }

  private static int rgbGrayLevel(int r, int g, int b) {
    final var gamma = 2.2;
    var rr = Math.pow(r / 255.0, gamma);
    var gg = Math.pow(g / 255.0, gamma);
    var bb = Math.pow(b / 255.0, gamma);
    var luminance = rgbLuminance(rr, gg, bb);
    return (int) (255.0 * Math.pow(luminance, 1 / gamma));
  }

  private static int r(int px) {
    return (px >> 16) & 0xFF;
  }

  private static int g(int px) {
    return (px >> 8) & 0xFF;
  }

  private static int b(int px) {
    return px & 0xFF;
  }

  public static int rgbInt(int r, int g, int b) {
    return (r << 16) | (g << 8) | b;
  }
}
