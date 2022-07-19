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

  private static boolean DEBUG_INITIAL_CCS     = false;
  private static boolean DEBUG_PREFILTERED_CCS = false;
  private static boolean DEBUG_NEAR_CCS        = false;
  private static boolean DEBUG_EXEMPLAR_CC     = false;
  private static boolean DEBUG_GROWN_CCS       = true;
  private static boolean DEBUG_MASK            = false;
  private static boolean DEBUG_PRELIMINARY_BOX = false;
  private static boolean DEBUG_REDUCED_BOX     = true;
  private static boolean DEBUG_CONTOUR         = false;
  private static boolean DEBUG_TOP_EDGE_CUTOFF = false;

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
    if (debug && DEBUG_INITIAL_CCS) {
      debugGfx.setColor(Color.GREEN);
      initialCCs.forEach(cc -> cc.drawWith(debugGfx));
      sendDebugImage.accept(debugImg, "Initial connected components");
    }
    var prefilteredCCs = prefilteredCCs(initialCCs);
    if (debug && DEBUG_PREFILTERED_CCS) {
      debugGfx.setColor(Color.RED);
      prefilteredCCs.forEach(cc -> cc.drawWith(debugGfx));
      sendDebugImage.accept(debugImg, "Pre-filtered connected components");
    }

    // Find ccs near the user's click point (assumed to be the center of the input image)
    var nearCCs = nearCCs(prefilteredCCs, center);
    if (nearCCs.isEmpty()) {
      LOG.debug("Found no near connected compoments");
      return Optional.empty();
    }
    if (debug && DEBUG_NEAR_CCS) {
      debugGfx.setColor(Color.ORANGE);
      nearCCs.forEach(cc -> cc.drawWith(debugGfx));
      sendDebugImage.accept(debugImg, "Near connected components");
    }

    // Find the average dimensions of the near ccs and take them as the dimensions of an exemplar
    // character cc. We assume that the user has clicked somewhere within the text block to be
    // detected, so the near ccs are most likely to represent characters
    var exemplarCC = createExemplarCC(nearCCs);
    if (debug && DEBUG_EXEMPLAR_CC) {
      debugGfx.setColor(Color.ORANGE);
      Rectangle.ofStartAndDimensions(
        center.x(), center.y() - 300, exemplarCC.width(), exemplarCC.height()
      )
        .drawWith(debugGfx);
      sendDebugImage.accept(debugImg, "Exemplar CC");
    }

    // Use our assumed exemplar character cc to weed out ccs that probably don't represent chars
    var exemplarFilteredCCs = exemplarFilteredCCs(exemplarCC, prefilteredCCs);

    // Grow ccs towards the center so that they hopefully mege into one blob per text block
    var grownCCs = growCCs(
      exemplarFilteredCCs, center, img.getWidth() - 1, img.getHeight() - 1, exemplarCC
    );
    if (debug && DEBUG_GROWN_CCS) {
      debugGfx.setColor(Color.MAGENTA);
      grownCCs.forEach(cc -> cc.drawWith(debugGfx));
      sendDebugImage.accept(debugImg, "Grown connected components");
    }

    // Fill a mask with the rectangles of the previously filtered and enlarged ccs
    var mask = createMaskWithCCs(img, grownCCs);
    if (debug && DEBUG_MASK) {
      sendDebugImage.accept(mask, "Mask with grown connected components");
    }

    // Find the bounding box of the largest contour in the mask image containing the center point
    var maskArr = ImageOps.maskImageToBinaryArray(mask);
    var contours = ContourFinder.find(maskArr, mask.getWidth(), mask.getHeight());
    var maybePreliminaryBoxRes = preliminaryBox(contours, center, debug, debugGfx);
    if (maybePreliminaryBoxRes.isEmpty()) {
      LOG.debug("Could not determine preliminary block");
      return Optional.empty();
    }
    var preliminaryBoxRes = maybePreliminaryBoxRes.get();

    // The preliminary box can still contain undesirable parts, especially neighbouring text blocks
    // that were either too close to it to be distinguished as separate or linked were to it by
    // elements misidentified as characters.
    //   We can remove some of those parts by examining the top edge of the contour from which the
    // preliminary box was made. Assuming that the our target text box has an approximately flat
    // top edge (since this is how the text is generally laid out), we will slice off the sides
    // of the preliminary box where the y-position of the top edge abruptly changes and doesn't
    // promptly change back.
    var reducedBox = cutOffBoxSidesByTopEdgeDiscongruity(
      preliminaryBoxRes.box(),
      preliminaryBoxRes.sourceContour(),
      center,
      exemplarCC,
      debug,
      debugGfx
    );
    if (debug && DEBUG_REDUCED_BOX) {
      debugGfx.setColor(Color.GREEN);
      reducedBox.drawWith(debugGfx);
    }

    // The box we have now might've been extended by CCs which lie mostly beyond its bounds. It's
    // useless to include them, however, because even if they're characters, they probably aren't
    // going to be recognized when half or more of each of them is cut off.
    //   We can get rid of this possible excessive extension by going over all the viable CCs again
    // and creating a new box according to the extends of only those CCs whose centers lie within
    // `reducedBox`.
    var ccsWithinReducedBox = exemplarFilteredCCs.stream()
      .filter(cc -> reducedBox.contains(cc.getCenter()))
      .toList();
    var remadeBox = Rectangle.around(ccsWithinReducedBox);

    // Add a margin
    var margin = exemplarCC.averageDimension() / 3;
    var finalBox = remadeBox
      .expanded(margin)
      .clamped(img.getWidth() - 1, img.getHeight() - 1);

    if (debug) {
      debugGfx.setColor(Color.RED);
      finalBox.drawWith(debugGfx);
      sendDebugImage.accept(debugImg, "Manga auto block final");
      debugGfx.dispose();
    }

    return Optional.ofNullable(finalBox);
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
      var growX = exemplar.width() * 1.9 * distCoeff;
      var growY = exemplar.height() * 1.3 * distCoeff;

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

  private record PreliminaryBoxResult(Rectangle box, Contour sourceContour) {}

  private Optional<PreliminaryBoxResult> preliminaryBox(
    List<Contour> contours, Point center, boolean debug, Graphics debugGfx
  ) {
    if (debug && DEBUG_PRELIMINARY_BOX) {
      debugGfx.setColor(Color.BLUE);
    }
    Rectangle box = null;
    Contour sourceContour = null;
    int largestViableBoxArea = 0;
    for (var i = 0; i < contours.size(); i++) {
      var contour = contours.get(i);
      if (contour.type == Contour.Type.HOLE) {
        continue;
      }

      var bbox = contour.getBoundingBox();
      if (bbox.contains(center)) {
        var area = bbox.getArea();
        if (area > largestViableBoxArea) {
          largestViableBoxArea = area;
          sourceContour = contour;
          box = bbox;
        }
      }

      if (debug && DEBUG_PRELIMINARY_BOX) {
        bbox.drawWith(debugGfx);
      }
    }
    return box == null
      ? Optional.empty()
      : Optional.of(new PreliminaryBoxResult(box, sourceContour));
  }

  private Rectangle cutOffBoxSidesByTopEdgeDiscongruity(
    Rectangle box,
    Contour sourceContour,
    Point center,
    ExemplarConnectedComponent exemplar,
    boolean debug,
    Graphics debugGfx
  ) {
    var topEdge = new int[box.getWidth()];
    Arrays.fill(topEdge, -1);
    if (debug && DEBUG_CONTOUR) {
      debugGfx.setColor(Color.RED);
    }
    for (var p : sourceContour.points) {
      var xRel = p.x() - box.getLeft();
      if (topEdge[xRel] == -1 || p.y() < topEdge[xRel]) {
        topEdge[xRel] = p.y();
      }
      if (debug && DEBUG_CONTOUR) {
        debugGfx.drawRect(p.x(), p.y(), 2, 2);
      }
    }

    // Traverse from the center towards left and right edges until sufficiently wide discongruities
    // are found (or until we reach the edges of the edge)

    // Treat this much of a change in height as a discongruity
    var discongruityThreshold = exemplar.height() * 1.75;
    // Ignore discongruities below this width
    var relevantDiscongruityWidth = (int) (exemplar.width() * 2);

    var centerXRel = center.x() - box.getLeft();

    var normalXSampleRadius = exemplar.width();
    var normalXSampleStart = Math.max(0, centerXRel - normalXSampleRadius);
    var normalXSampleEnd = Math.min(centerXRel + normalXSampleRadius, box.getWidth());
    var ySum = 0;
    for (var x = normalXSampleStart; x < normalXSampleEnd; x++) {
      ySum += topEdge[x];
    }
    var normalY = ySum / (normalXSampleEnd - normalXSampleStart);

    var leftCutoff = findCutoffByEdgeDiscongruity(
      topEdge,
      centerXRel,
      normalY,
      EdgeTraversalDirection.LEFTWARD,
      discongruityThreshold,
      relevantDiscongruityWidth
    );
    var rightCutoff = findCutoffByEdgeDiscongruity(
      topEdge,
      centerXRel,
      normalY,
      EdgeTraversalDirection.RIGHTWARD,
      discongruityThreshold,
      relevantDiscongruityWidth
    );
    if (debug && DEBUG_TOP_EDGE_CUTOFF) {
      debugGfx.setColor(Color.RED);
      for (var x = leftCutoff; x <= rightCutoff; x++) {
        debugGfx.drawRect(box.getLeft() + x, topEdge[x], 1, 3);
      }
    }


    leftCutoff += box.getLeft(); // Countour bbox coords -> image coords
    rightCutoff += box.getLeft();
    return sourceContour.getBoundingBox(leftCutoff, rightCutoff);
  }

  private enum EdgeTraversalDirection {
    LEFTWARD,
    RIGHTWARD;
  }

  private enum EdgeTraversalState {
    NORMAL,
    IN_DISCONGRUITY;
  }

  private int findCutoffByEdgeDiscongruity(
    int[] topEdge,
    int startX,
    int normalY,
    EdgeTraversalDirection direction,
    double discongruityThreshold,
    int relevantDiscongruityWidth
  ) {
    // Will compare the y-coordinate of the top edge at x and x+-compareDist to detect discongruity.
    // This is to not miss changes that are quick but not instantaneous.
    var compareDist = 4;

    var state = EdgeTraversalState.NORMAL;
    Integer currDiscongruityStartX = null;

    // For leftward direction
    var cutoff = 0;
    var xStep = -1;
    var stopMod = 1;
    if (direction == EdgeTraversalDirection.RIGHTWARD) {
      cutoff = topEdge.length - 1;
      xStep = 1;
      stopMod = -1; // For reversing the stop condition
    }
    var stopX = cutoff;

    traversal: for (var x = startX; x * stopMod > stopX * stopMod; x += xStep) {
      switch (state) {
        case NORMAL:
          var earlierX = x - (xStep * compareDist);
          var earlierY = topEdge[earlierX];
          var yDiffFromEarlier = Math.abs(topEdge[x] - earlierY);
          if (yDiffFromEarlier >= discongruityThreshold) {
            state = EdgeTraversalState.IN_DISCONGRUITY;
            currDiscongruityStartX = earlierX + 1; // Inexact
          }
          break;

        case IN_DISCONGRUITY:
          var yDiffFromNormal = Math.abs(topEdge[x] - normalY);
          if (yDiffFromNormal < discongruityThreshold) {
            // Discongruity has ended before reaching a width that would classify it as relevant
            state = EdgeTraversalState.NORMAL;
            currDiscongruityStartX = null;
          } else if (Math.abs(x - currDiscongruityStartX) >= relevantDiscongruityWidth) {
            break traversal;
          }
          break;
      }
    }
    if (state == EdgeTraversalState.IN_DISCONGRUITY) {
      // We treat irrelevant discongruities as relevant if they persist until the edge of the edge
      cutoff = currDiscongruityStartX;
    }

    return cutoff;
  }
}
