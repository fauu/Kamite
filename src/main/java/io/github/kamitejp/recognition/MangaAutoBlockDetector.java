package io.github.kamitejp.recognition;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Optional;
import java.util.function.BiConsumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.kamitejp.geometry.Point;
import io.github.kamitejp.geometry.Rectangle;

public class MangaAutoBlockDetector implements AutoBlockDetector {
  private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  @Override
  public Optional<Rectangle> detect(
    BufferedImage img, boolean debug, BiConsumer<BufferedImage, String> sendDebugImage
  ) {
    // Remove alpha channel
    if (img.getType() != BufferedImage.TYPE_INT_RGB) {
      img = ImageOps.withoutAlphaChannel(img);
      img = ImageOps.copied(img, BufferedImage.TYPE_INT_RGB);
    }

    var center = new Point(img.getWidth() / 2, img.getHeight() / 2);

    var gray = ImageOps.copied(img);
    ImageOps.toGrayscale(gray);

    // Invert if light text on dark background suspected
    var darkCheckAreaDim = 50;
    if (ImageOps.isDarkDominated(gray, Rectangle.around(center, darkCheckAreaDim))) {
      ImageOps.negate(gray);
    }

    var eroded = ImageOps.eroded(gray, 2, 2);
    var imgArr = ImageOps.toGrayArray(eroded);
    ImageOps.otsuThreshold(imgArr);

    BufferedImage debugImg = null;
    Graphics debugGfx = null;
    if (debug) {
      debugImg = ImageOps.copied(img);
      debugGfx = debugImg.createGraphics();
    }

    // Get connected components (ccs), excluding those that almost certainly aren't characters
    var ccExtractor = new ConnectedComponentExtractor();
    var initialCCs = Arrays.stream(ccExtractor.extract(imgArr, img.getWidth(), img.getHeight()))
      .skip(1)
      .map(ConnectedComponent::rectangle)
      .toList();

    // if (debug) {
    //   debugGfx.setColor(Color.GREEN);
    //   for (var cc : initialCCs) {
    //     debugGfx.drawRect(cc.getLeft() - 3, cc.getTop() - 3, cc.getWidth() + 6, cc.getHeight() + 6);
    //   }
    // }

    var prefilteredCCs = initialCCs.stream()
      .filter(cc ->
        cc.dimensionsWithin(2, 150)
        && (cc.getWidth() > 5 || cc.getHeight() > 5)
        && cc.getArea() < 4000
      )
      .toList();

    // if (debug) {
    //   debugGfx.setColor(Color.RED);
    //   for (var cc : prefilteredCCs) {
    //     debugGfx.drawRect(cc.getLeft() - 2, cc.getTop() - 2, cc.getWidth() + 4, cc.getHeight() + 4);
    //   }
    // }
    // sendDebugImage.accept(debugImg);

    // Find ccs near the user's click point (assumed to be the center of the input image)
    var nearCcs = new ArrayList<Rectangle>();
    for (var cc : prefilteredCCs) {
      var dist = cc.getCenter().distanceFrom(center);
      if (dist < 50) {
        nearCcs.add(cc);
      }
    }

    if (nearCcs.isEmpty()) {
      return Optional.empty();
    }

    // if (debug) {
    //   debugGfx.setColor(Color.ORANGE);
    //   for (var cc : nearCcs) {
    //     debugGfx.drawRect(cc.getLeft() - 1, cc.getTop() - 1, cc.getWidth() + 2, cc.getHeight() + 2);
    //   }
    // }

    // Find the average dimensions of the near ccs and take them as the dimensions of an exemplar
    // character cc. We assume that the user has clicked somewhere within the text block to be
    // detected, so the near ccs are most likely to represent characters
    var nearWidthTotal = 0;
    var nearHeightTotal = 0;
    for (var cc : nearCcs) {
      nearWidthTotal += cc.getWidth();
      nearHeightTotal += cc.getHeight();
    }
    final var exemplarCCWidth = nearWidthTotal / nearCcs.size();
    final var exemplarCCHeight = nearHeightTotal / nearCcs.size();
    final var exemplarCCAvgDim = (exemplarCCWidth + exemplarCCHeight) / 2;

    final var maxW = exemplarCCWidth * 3;
    final var maxH = exemplarCCHeight * 4;
    final var maxArea = exemplarCCWidth * exemplarCCHeight * 5;

    var processedCcs = new ArrayList<Rectangle>();
    for (var cc : prefilteredCCs) {
      if (debug) {
        debugGfx.setColor(Color.GRAY);
        debugGfx.drawRect(cc.getLeft(), cc.getTop(), cc.getWidth(), cc.getHeight());
      }

      // Use our assumed exemplar character cc to weed out ccs that, judging from their dimensions,
      // probably don't represent characters
      if (!cc.widthWithin(2, maxW) || !cc.heightWithin(2, maxH) || cc.getArea() > maxArea) {
        continue;
      }

      // Prepare coefficients for growing the cc towards the center of the image (the presumed
      // center of the text block)
      var ccCenter = cc.getCenter();
      var normalizedDist = ccCenter.distanceFrom(center) / exemplarCCAvgDim;
      var distCoeffFactorA = 50;
      var distCoeffFactorB = 1.35;
      var distCoeff =
        distCoeffFactorA
        / (Math.pow(normalizedDist, distCoeffFactorB) + distCoeffFactorA);
      var growX = exemplarCCWidth * 1.7 * distCoeff;
      var growY = exemplarCCHeight * 1.0 * distCoeff;

      // Special case hack: こ, に and similar tend to be detected as separate components, so we
      // need to grow them vertically a lot more than in the ordinary case
      var ratio = cc.getRatio();
      if (cc.getHeight() < exemplarCCHeight && ratio > 1.75 && ratio < 3.25) {
        growY = cc.getHeight() * 3.75 * distCoeff;
      }
      // Analogous hack for certain fonts' い, as well as some characters that are overall slender
      if (cc.getWidth() < exemplarCCWidth && ratio > 0.3 && ratio < 0.55) {
        growX = cc.getWidth() * 3.75 * distCoeff;
      }

      var left = cc.getLeft();
      var right = cc.getRight();
      var top = cc.getTop();
      var bottom = cc.getBottom();

      // Grow the cc towards the center of the image, with clamping
      if (center.x() < left) {
        left = left - (int) growX;
        if (left < 0) {
          left = 0;
        }
      } else if (center.x() > right) {
        right = right + (int) growX;
        if (right >= img.getWidth()) {
          right = img.getWidth() - 1;
        }
      }
      if (center.y() < top) {
        top = top - (int) growY;
        if (top < 0) {
          top = 0;
        }
      } else if (center.y() > bottom) {
        bottom = bottom + (int) growY;
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

    // Fill a mask with the rectangles of the previously filtered and enlarged ccs
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
    //   sendDebugImage.accept(mask);
    // }

    // Find the bounding box of the largest contour in the mask image containing the center point.
    // This is the presumed location of our text block
    var maskArr = ImageOps.maskImageToBinaryArray(mask);
    var contours = ContourFinder.find(maskArr, mask.getWidth(), mask.getHeight());
    if (contours.size() == 0) {
      LOG.debug("Could not find contours of the auto-block mask");
      return Optional.empty();
    }

    if (debug) {
      debugGfx.setColor(Color.BLUE);
    }
    Rectangle largestOverlappingContourBBox = null;
    int largestOverlappingContourIdx = -1;
    int largestBBoxArea = 0;
    for (var i = 0; i < contours.size(); i++) {
      var contour = contours.get(i);
      if (contour.type == Contour.Type.HOLE) {
        continue;
      }
      var bbox = contour.getBoundingBox();

      if (
        bbox.getLeft() <= center.x() && center.x() <= bbox.getRight()
        && bbox.getTop() <= center.y() && center.y() <= bbox.getBottom()
      ) {
        var area = bbox.getArea();
        if (area > largestBBoxArea) {
          largestBBoxArea = area;
          largestOverlappingContourIdx = i;
          largestOverlappingContourBBox = bbox;
        }
      }

      if (debug) {
        bbox.drawWith(debugGfx);
      }
    }

    // This is to detect some cases when the chosen contour contains multiple neighbouring text
    // blocks. If the text blocks are sufficiently vertically displaced, we can limit the result
    // to just our block of interest by following the chosen contour's upper edge in both directions
    // starting at `center.x` and slicing it at the first locations where we encounter significant
    // changes in the edge's y-position. We assume that those are the points at which one text
    // block passess into another.
    //
    // TODO: When calculating the final bounding box, discard sudden drops in the bottom edge's
    //       y-position near the side edges of our sliced contour, since those are contaminations
    //       from the neighbouring blocks we've just cut off.
    var chosenContour = contours.get(largestOverlappingContourIdx);
    var topEdge = new int[largestOverlappingContourBBox.getWidth()];
    Arrays.fill(topEdge, -1);
    // if (debug) {
    //    debugGfx.setColor(Color.RED);
    // }
    for (var p : chosenContour.points) {
      var xRel = p.x() - largestOverlappingContourBBox.getLeft();
      if (topEdge[xRel] == -1 || p.y() < topEdge[xRel]) {
        topEdge[xRel] = p.y();
      }
      // if (debug) {
      //   debugGfx.drawRect(p.x(), p.y(), 3, 3);
      // }
    }

    int leftCutoff = 0;
    int rightCutoff = topEdge.length - 1;
    var centerXRel = center.x() - largestOverlappingContourBBox.getLeft();
    for (var x = centerXRel; x > 0; x--) {
      var delta = Math.abs(topEdge[x] - topEdge[x - 1]);
      if (delta > exemplarCCHeight * 1.5) {
        leftCutoff = x;
        break;
      }
    }
    for (var x = centerXRel; x < topEdge.length - 1; x++) {
      var delta = Math.abs(topEdge[x] - topEdge[x + 1]);
      if (delta > exemplarCCHeight * 1.5) {
        rightCutoff = x;
        break;
      }
    }

    // if (debug) {
    //   debugGfx.setColor(Color.RED);
    //   for (var x = leftCutoff; x <= rightCutoff; x++) {
    //     debugGfx.drawRect(largestOverlappingContourBBox.getLeft() + x, topEdge[x], 1, 3);
    //   }
    // }
    leftCutoff += largestOverlappingContourBBox.getLeft(); // Countour bbox coords -> image coords
    rightCutoff += largestOverlappingContourBBox.getLeft();
    var slicedChosenContourBBox = chosenContour.getBoundingBox(leftCutoff, rightCutoff);

    // Crop the original image to where we've determined the text block is. This is the final result
    var margin = exemplarCCAvgDim / 3;

    var finalRect = slicedChosenContourBBox
      .expanded(margin)
      .clamped(img.getWidth() - 1, img.getHeight() - 1);

    if (debug) {
      debugGfx.setColor(Color.GREEN);
      debugGfx.drawRect(
        finalRect.getLeft(),
        finalRect.getTop(),
        finalRect.getWidth(),
        finalRect.getHeight()
      );
    }

    if (debug) {
      debugGfx.dispose();
      sendDebugImage.accept(debugImg, "Manga auto block detect final");
    }

    return Optional.ofNullable(finalRect);
  }
}
