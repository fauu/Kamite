package io.github.kamitejp.recognition;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.kamitejp.geometry.Point;
import io.github.kamitejp.geometry.Rectangle;

public class MangaAutoBlockDetector implements AutoBlockDetector {
  private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private static final int NEAR_CC_MAX_DISTANCE = 75;

  @Override
  public Optional<Rectangle> detect(
    BufferedImage img, boolean debug, BiConsumer<BufferedImage, String> sendDebugImage
  ) {
    if (img.getType() != BufferedImage.TYPE_INT_RGB) {
      img = ImageOps.withoutAlphaChannel(img);
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

    // Init debug
    BufferedImage debugImg = null;
    Graphics debugGfxInit = null;
    if (debug) {
      debugImg = ImageOps.copied(img);
      debugGfxInit = debugImg.createGraphics();
    }
    final Graphics debugGfx = debugGfxInit;

    // Extract and pre-filter connected components (ccs)
    var initialCCs = extractCCs(imgArr, img.getWidth(), img.getHeight());
    // if (debug) {
    //   debugGfx.setColor(Color.GREEN);
    //   initialCCs.forEach(cc -> cc.drawWith(debugGfx));
    //   sendDebugImage.accept(debugImg, "Initial connected components");
    // }
    var prefilteredCCs = prefilteredCCs(initialCCs);
    // if (debug) {
    //   debugGfx.setColor(Color.RED);
    //   prefilteredCCs.forEach(cc -> cc.drawWith(debugGfx));
    //   sendDebugImage.accept(debugImg, "Pre-filtered connected components");
    // }

    // Find ccs near the user's click point (assumed to be the center of the input image)
    var nearCCs = nearCCs(prefilteredCCs, center);
    if (nearCCs.isEmpty()) {
      LOG.debug("Found no near connected compoments");
      return Optional.empty();
    }
    // if (debug) {
    //   debugGfx.setColor(Color.ORANGE);
    //   nearCCs.forEach(cc -> cc.drawWith(debugGfx));
    //   sendDebugImage.accept(debugImg, "Near connected components");
    // }

    // Find the average dimensions of the near ccs and take them as the dimensions of an exemplar
    // character cc. We assume that the user has clicked somewhere within the text block to be
    // detected, so the near ccs are most likely to represent characters
    var exemplarCC = createExemplarCC(nearCCs);
    // if (debug) {
    //   debugGfx.setColor(Color.ORANGE);
    //   Rectangle.ofStartAndDimensions(
    //     center.x(), center.y() - 300, exemplarCC.width(), exemplarCC.height()
    //   )
    //     .drawWith(debugGfx);
    //   sendDebugImage.accept(debugImg, "Exemplar CC");
    // }

    // Use our assumed exemplar character cc to weed out ccs that probably don't represent chars
    var exemplarFilteredCCs = exemplarFilteredCCs(exemplarCC, prefilteredCCs);

    // Grow ccs towards the center so that they hopefully mege into one blob per text block
    var grownCCs = growCCs(
      exemplarFilteredCCs, center, img.getWidth() - 1, img.getHeight() - 1, exemplarCC
    );
    if (debug) {
      debugGfx.setColor(Color.MAGENTA);
      grownCCs.forEach(cc -> cc.drawWith(debugGfx));
      sendDebugImage.accept(debugImg, "Grown connected components");
    }

    // Fill a mask with the rectangles of the previously filtered and enlarged ccs
    var mask = createMaskWithCCs(img, grownCCs);
    // if (debug) {
    //   sendDebugImage.accept(mask, "Mask with grown connected components");
    // }

    // Find the bounding box of the largest contour in the mask image containing the center point.
    // This is the presumed location of our text block
    var maskArr = ImageOps.maskImageToBinaryArray(mask);
    var contours = ContourFinder.find(maskArr, mask.getWidth(), mask.getHeight());
    if (contours.size() == 0) {
      LOG.debug("Could not find contours of the auto-block mask");
      return Optional.empty();
    }
    var preliminaryBlockRes = preliminaryBlock(contours, center, debug, debugGfx);

    // This is to detect some cases when the preliminary block contains multiple neighbouring text
    // blocks. If the text blocks are sufficiently vertically displaced, we can limit the result
    // to just our block of interest by following the preliminary block's contour's upper edge in
    // both directions starting at `center.x` and slicing it at the first locations where we
    // encounter significant changes in the edge's y-position. We assume that those are the points
    // at which one text block passess into another.
    var finalBlock = sliceOffNeighbouringBlocks(
      preliminaryBlockRes.block(),
      preliminaryBlockRes.blockContour(),
      center,
      exemplarCC,
      debug,
      debugGfx
    );

    // Crop the original image to where we've determined the text block is. This is the final result
    var margin = exemplarCC.averageDimension() / 3;
    var finalRect = finalBlock
      .expanded(margin)
      .clamped(img.getWidth() - 1, img.getHeight() - 1);

    if (debug) {
      debugGfx.setColor(Color.GREEN);
      finalRect.drawWith(debugGfx);
      sendDebugImage.accept(debugImg, "Manga auto block final");
      debugGfx.dispose();
    }

    return Optional.ofNullable(finalRect);
  }

  private List<Rectangle> extractCCs(int[] imgArr, int w, int h) {
    var rawCCs = new ConnectedComponentExtractor().extract(imgArr, w, h);
    return Arrays.stream(rawCCs)
      .skip(1)
      .map(ConnectedComponent::rectangle)
      .toList();
  }

  private List<Rectangle> prefilteredCCs(List<Rectangle> ccs) {
    return ccs.stream()
      .filter(cc ->
        cc.dimensionsWithin(2, 150)
        && (cc.getWidth() > 5 || cc.getHeight() > 5)
        && !(cc.getRatio() > 0.65 && cc.getRatio() < 1.5 && cc.getWidth() < 8)
        && cc.getArea() < 4000
      )
      .toList();
  }

  private List<Rectangle> nearCCs(List<Rectangle> ccs, Point p) {
    var res = new ArrayList<Rectangle>();
    for (var cc : ccs) {
      var dist = cc.getCenter().distanceFrom(p);
      if (dist <= NEAR_CC_MAX_DISTANCE) {
        res.add(cc);
      }
    }
    return res;
  }

  private record ExemplarConnectedComponent(int width, int height, int averageDimension) {}

  private ExemplarConnectedComponent createExemplarCC(List<Rectangle> sourceCCs) {
    var wSum = 0;
    var hSum = 0;
    for (var cc : sourceCCs) {
      wSum += cc.getWidth();
      hSum += cc.getHeight();
    }
    var w = wSum / sourceCCs.size();
    var h = hSum / sourceCCs.size();
    return new ExemplarConnectedComponent(w, h, (w + h) / 2);
  }

  private List<Rectangle> exemplarFilteredCCs(
    ExemplarConnectedComponent exemplar, List<Rectangle> ccs
  ) {
    final var maxW = exemplar.width() * 3;
    final var maxH = exemplar.height() * 4;
    final var maxArea = exemplar.averageDimension() * exemplar.averageDimension() * 5;
    final var minWForSquareish = exemplar.width() / 2;
    return ccs.stream()
      .filter(cc ->
        cc.widthWithin(2, maxW)
        && cc.heightWithin(2, maxH)
        && cc.getArea() <= maxArea
        && !(cc.getRatio() > 0.65 && cc.getRatio() < 1.5 && cc.getWidth() < minWForSquareish)
      )
        .toList();
  }

  private List<Rectangle> growCCs(
    List<Rectangle> ccs,
    Point center,
    int xMax,
    int yMax,
    ExemplarConnectedComponent exemplar
  ) {
    var res = new ArrayList<Rectangle>();
    for (var cc : ccs) {
      // Prepare coefficients for growing the cc towards the center of the image (the presumed
      // center of the text block)
      var ccCenter = cc.getCenter();
      var normalizedDist = ccCenter.distanceFrom(center) / exemplar.averageDimension();
      var distCoeffFactorA = 50;
      var distCoeffFactorB = 1.35;
      var distCoeff =
        distCoeffFactorA
        / (Math.pow(normalizedDist, distCoeffFactorB) + distCoeffFactorA);
      var growX = exemplar.width() * 1.7 * distCoeff;
      var growY = exemplar.height() * 1.0 * distCoeff;

      // Special case hack: こ, に and similar tend to be detected as separate components, so we
      // need to grow them vertically a lot more than in the ordinary case
      var ratio = cc.getRatio();
      if (cc.getHeight() < exemplar.height() && ratio > 1.75 && ratio < 3.25) {
        growY = cc.getHeight() * 3.75 * distCoeff;
      }
      // Analogous hack for certain fonts' い, as well as some characters that are overall slender
      if (cc.getWidth() < exemplar.width() && ratio > 0.3 && ratio < 0.55) {
        growX = cc.getWidth() * 3.75 * distCoeff;
      }

      var left = cc.getLeft();
      var right = cc.getRight();
      var top = cc.getTop();
      var bottom = cc.getBottom();

      // Grow towards the center of the image, with clamping
      if (center.x() < left) {
        left = left - (int) growX;
        if (left < 0) {
          left = 0;
        }
      } else if (center.x() > right) {
        right = right + (int) growX;
        if (right > xMax) {
          right = xMax;
        }
      }
      if (center.y() < top) {
        top = top - (int) growY;
        if (top < 0) {
          top = 0;
        }
      } else if (center.y() > bottom) {
        bottom = bottom + (int) growY;
        if (bottom > yMax) {
          bottom = yMax;
        }
      }

      var grown = Rectangle.ofEdges(left, top, right, bottom);
      res.add(grown);
    }
    return res;
  }

  private BufferedImage createMaskWithCCs(BufferedImage sourceImg, List<Rectangle> ccs) {
    var mask = new BufferedImage(sourceImg.getWidth(), sourceImg.getHeight(), sourceImg.getType());
    var gfx = mask.createGraphics();
    gfx.setColor(Color.BLACK);
    gfx.fillRect(0, 0, sourceImg.getWidth(), sourceImg.getHeight());
    gfx.setColor(Color.WHITE);
    ccs.forEach(cc -> cc.drawFilledWith(gfx));
    gfx.dispose();
    return mask;
  }

  private record PreliminaryBlockResult(Rectangle block, Contour blockContour) {}

  private PreliminaryBlockResult preliminaryBlock(
    List<Contour> contours, Point center, boolean debug, Graphics debugGfx
  ) {
    if (debug) {
      debugGfx.setColor(Color.BLUE);
    }
    Rectangle block = null;
    Contour blockContour = null;
    int largestViableBlockArea = 0;
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
        if (area > largestViableBlockArea) {
          largestViableBlockArea = area;
          blockContour = contour;
          block = bbox;
        }
      }

      if (debug) {
        bbox.drawWith(debugGfx);
      }
    }
    return new PreliminaryBlockResult(block, blockContour);
  }

  private Rectangle sliceOffNeighbouringBlocks(
    Rectangle block,
    Contour blockContour,
    Point center,
    ExemplarConnectedComponent exemplar,
    boolean debug,
    Graphics debugGfx
  ) {
    var topEdge = new int[block.getWidth()];
    Arrays.fill(topEdge, -1);
    // if (debug) {
    //   debugGfx.setColor(Color.RED);
    // }
    for (var p : blockContour.points) {
      var xRel = p.x() - block.getLeft();
      if (topEdge[xRel] == -1 || p.y() < topEdge[xRel]) {
        topEdge[xRel] = p.y();
      }
      // if (debug) {
      //   debugGfx.drawRect(p.x(), p.y(), 3, 3);
      // }
    }

    int leftCutoff = 0;
    int rightCutoff = topEdge.length - 1;
    var centerXRel = center.x() - block.getLeft();
    for (var x = centerXRel; x > 0; x--) {
      var delta = Math.abs(topEdge[x] - topEdge[x - 1]);
      if (delta > exemplar.height() * 1.5) {
        leftCutoff = x;
        break;
      }
    }
    for (var x = centerXRel; x < topEdge.length - 1; x++) {
      var delta = Math.abs(topEdge[x] - topEdge[x + 1]);
      if (delta > exemplar.height() * 1.5) {
        rightCutoff = x;
        break;
      }
    }

    // TODO: When calculating the final bounding box, discard sudden drops in the bottom edge's
    //       y-position near the side edges of our sliced contour, since those are contaminations
    //       from the neighbouring blocks we've just cut off.

    // if (debug) {
    //   debugGfx.setColor(Color.RED);
    //   for (var x = leftCutoff; x <= rightCutoff; x++) {
    //     debugGfx.drawRect(largestOverlappingContourBBox.getLeft() + x, topEdge[x], 1, 3);
    //   }
    // }
    leftCutoff += block.getLeft(); // Countour bbox coords -> image coords
    rightCutoff += block.getLeft();
    return blockContour.getBoundingBox(leftCutoff, rightCutoff);
  }
}
