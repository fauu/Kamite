package io.github.kamitejp.recognition;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Optional;
import java.util.function.BiConsumer;

import io.github.kamitejp.recognition.imagefeature.ConnectedComponentExtractor;
import io.github.kamitejp.recognition.imagefeature.Contour;
import io.github.kamitejp.recognition.imagefeature.ContourFinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.kamitejp.geometry.Point;
import io.github.kamitejp.geometry.Rectangle;
import io.github.kamitejp.image.ImageOps;

public class GameTextboxAutoBlockDetector implements AutoBlockDetector {
  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  // NOTE: This is a temporary stand-in algorithm copy-pasted with few modifications from
  //       an older version of the manga block detector. It doesn't need to be
  //       refactored/deduplicated, since it is to be reworked
  @Override
  public Optional<Rectangle> detect(
    BufferedImage img, boolean debug, BiConsumer<BufferedImage, String> sendDebugImage
  ) {
    if (img.getType() != BufferedImage.TYPE_INT_RGB) {
      img = ImageOps.withoutAlphaChannel(img);
    }

    var startX = img.getWidth() <= 50 ? img.getWidth() - 1 : 50;
    var startY = img.getHeight() <= 10 ? img.getHeight() - 1 : 10;
    var start = new Point(startX, startY);

    var gray = ImageOps.copied(img);
    ImageOps.toGrayscale(gray);

    var darkCheckAreaDim = 50;
    if (ImageOps.isDarkDominated(gray, Rectangle.around(start, darkCheckAreaDim))) {
      ImageOps.negate(gray);
    }

    var eroded = ImageOps.eroded(gray, 2, 2);
    var imgArr = ImageOps.toGrayArray(eroded);
    ImageOps.otsuThreshold(imgArr);

    var ccExtractor = new ConnectedComponentExtractor();
    var ccs = Arrays.stream(ccExtractor.extract(imgArr, img.getWidth(), img.getHeight()))
      .skip(1)
      .map(cc -> cc.rectangle())
      .filter(cc -> cc.dimensionsWithin(2, 150) && cc.getArea() < 4000)
      .toList();

    Graphics debugGfx = null;
    if (debug) {
      debugGfx = img.createGraphics();
    }

    var nearCcs = new ArrayList<Rectangle>();
    for (var cc : ccs) {
      var dist = cc.getCenter().distanceFrom(start);
      if (dist < 50) {
        nearCcs.add(cc);
      }
    }

    if (nearCcs.isEmpty()) {
      return Optional.empty();
    }

    if (debug) {
      debugGfx.setColor(Color.PINK);
      for (var cc : nearCcs) {
        debugGfx.drawRect(cc.getLeft(), cc.getTop(), cc.getWidth(), cc.getHeight());
      }
    }

    var nearWidthTotal = 0;
    var nearHeightTotal = 0;
    for (var cc : nearCcs) {
      nearWidthTotal += cc.getWidth();
      nearHeightTotal += cc.getHeight();
    }
    var nearWidthAvg = nearWidthTotal / nearCcs.size();
    var nearHeightAvg = nearHeightTotal / nearCcs.size();

    var processedCcs = new ArrayList<Rectangle>();
    for (var cc : ccs) {
      if (debug) {
        debugGfx.setColor(Color.GRAY);
        debugGfx.drawRect(cc.getLeft(), cc.getTop(), cc.getWidth(), cc.getHeight());
      }

      if (
        cc.getWidth() < 5 && cc.getHeight() < 5
        || cc.getWidth() > nearWidthAvg * 3
        || cc.getHeight() > nearHeightAvg * 4 
        || cc.getArea() > nearWidthAvg * nearHeightAvg * 5
      ) {
        continue;
      }

      var growX = (int) (nearWidthAvg * 5);
      var growY = (int) (nearHeightAvg * 1.5);

      var ratio = cc.getRatio();
      if (cc.getHeight() < nearHeightAvg && ratio > 1.75 && ratio < 3.25) {
        growY = cc.getHeight() * 3;
      }

      var left = cc.getLeft();
      var right = cc.getRight();
      var top = cc.getTop();
      var bottom = cc.getBottom();

      if (start.x() < left) {
        left = left - growX;
        if (left < 0) {
          left = 0;
        }
      } else if (start.x() > right) {
        right = right + growX;
        if (right >= img.getWidth()) {
          right = img.getWidth() - 1;
        }
      }

      if (start.y() < top) {
        top = top - growY;
        if (top < 0) {
          top = 0;
        }
      } else if (start.y() > bottom) {
        bottom = bottom + growY;
        if (bottom >= img.getHeight()) {
          bottom = img.getHeight() - 1;
        }
      }

      var grown = Rectangle.ofEdges(left, top, right, bottom);
      processedCcs.add(grown);

      if (debug) {
        debugGfx.setColor(Color.MAGENTA);
        debugGfx.drawRect(grown.getLeft(), grown.getTop(), grown.getWidth(), grown.getHeight());
      }
    }

    var mask = new BufferedImage(img.getWidth(), img.getHeight(), img.getType());
    var maskGfx = mask.createGraphics();
    maskGfx.setColor(Color.BLACK);
    maskGfx.fillRect(0, 0, img.getWidth(), img.getHeight());
    maskGfx.setColor(Color.WHITE);
    for (var b : processedCcs) {
      maskGfx.fillRect(b.getLeft(), b.getTop(), b.getWidth(), b.getHeight());
    }
    maskGfx.dispose();
    // if (debug) {
    //   sendDebugImageFn.accept(mask);
    // }

    var maskArr = ImageOps.maskImageToBinaryArray(mask);
    var contours = ContourFinder.find(maskArr, mask.getWidth(), mask.getHeight());
    if (debug) {
      debugGfx.setColor(Color.BLUE);
    }
    Rectangle largestContourBBoxContainingCenter = null;
    var largestBBoxArea = 0;
    for (var c : contours) {
      if (c.type == Contour.Type.HOLE) {
        continue;
      }
      var bbox = c.getBoundingBox();

      if (
        bbox.getLeft() <= start.x() && start.x() <= bbox.getRight()
        && bbox.getTop() <= start.y() && start.y() <= bbox.getBottom()
      ) {
        var area = bbox.getArea();
        if (area > largestBBoxArea) {
          largestBBoxArea = area;
          largestContourBBoxContainingCenter = bbox;
        }
      }

      if (debug) {
        bbox.drawWith(debugGfx);
      }
    }

    Rectangle result = null;
    if (largestContourBBoxContainingCenter != null) {
      result = largestContourBBoxContainingCenter
        .expandedNonNegative(2)
        .clamped(img.getWidth() - 1, img.getHeight() - 1);

      if (debug) {
        debugGfx.setColor(Color.GREEN);
        debugGfx.drawRect(result.getLeft(), result.getTop(), result.getWidth(), result.getHeight());
      }
    }

    if (debug) {
      debugGfx.dispose();
      sendDebugImage.accept(img, "Game textbox auto block final");
    }

    return Optional.ofNullable(result);
  }
}
