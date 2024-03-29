package io.github.kamitejp.chunk;

import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.jsoup.Jsoup;

import io.github.kamitejp.geometry.Rectangle;
import io.github.kamitejp.textprocessing.TextProcessor;

public class Chunk {
  private static final String HOCR_WORD_TITLE_CONF_RE_RAW = "x_wconf (?<conf>\\d+)";
  private static final Pattern HOCR_WORD_TITLE_CONF_RE =
    Pattern.compile(HOCR_WORD_TITLE_CONF_RE_RAW);
  private static final Pattern HOCR_WORD_TITLE_FULL_RE = Pattern.compile(
    "bbox (?<x0>\\d+) (?<y0>\\d+) (?<x1>\\d+) (?<y1>\\d+); %s"
      .formatted(HOCR_WORD_TITLE_CONF_RE_RAW)
  );
  private static final Pattern HOCR_GARBAGE_CHARS_RE =
    Pattern.compile("[^A-Z!?\\-–—ーｦ-ﾟァ-ヶぁ-ゞＡ-ｚ０-９ｧ-ﾝﾞﾟァ-ンぁ-ん一-龯]+");

  private String content;
  private String originalContent;
  private final List<String> labels;
  private int score;
  private final Rectangle firstWordBox;

  private Chunk(String content, List<String> labels, int score, Rectangle firstWordBox) {
    this.content = content;
    this.labels = labels;
    this.score = score;
    this.firstWordBox = firstWordBox;
  }

  public Chunk(String content, List<String> labels, int score) {
    this(content, labels, score, null);
  }

  public Chunk(String content, String label, int score) {
    this(content, Stream.of(label).collect(toList()), score);
  }

  private Chunk(List<String> lines, String label, float avgConfidence, Rectangle firstWordBox) {
    this(
      /*      content */ String.join("\n", lines),
      /*       labels */ Stream.of(label).collect(toList()),
      /*        score */ 0,
      /* firstWordBox */ firstWordBox
    );
    score = calculateScore(avgConfidence);
  }

  public static Optional<Chunk> fromHOCRWithLabel(String s, String label) {
    final var firstWordBox = new Rectangle[1];
    final var sumConfidences = new int[]{0};
    final var numConfidences = new int[]{0};

    var doc = Jsoup.parse(s);
    var lines = doc.select(".ocr_line").stream().map(lineEl ->
      lineEl.select(".ocrx_word").stream().map(wordEl -> {
        var title = wordEl.attr("title");
        Matcher m;
        if (firstWordBox[0] == null) {
          m = HOCR_WORD_TITLE_FULL_RE.matcher(title);
          if (!m.find()) {
            throw new IllegalArgumentException("Failed to match `.ocrx_word` title (full)");
          }
          var left = Integer.parseInt(m.group("x0"));
          var right = Integer.parseInt(m.group("x1"));
          var top = Integer.parseInt(m.group("y0"));
          var bottom = Integer.parseInt(m.group("y1"));
          firstWordBox[0] = Rectangle.ofEdges(left, top, right, bottom);
        } else {
          m = HOCR_WORD_TITLE_CONF_RE.matcher(title);
          if (!m.find()) {
            throw new IllegalArgumentException("Failed to match `.ocrx_word` title (conf.)");
          }
        }
        var confidence = Integer.parseInt(m.group("conf"));
        numConfidences[0]++;
        sumConfidences[0] += confidence;
        return wordEl.text();
      })
        .collect(joining())
    )
      .map(Chunk::hocrPreprocessContentLine)
      .filter(not(String::isBlank))
      .collect(toList());

    var avgConfidence = (float) sumConfidences[0] / numConfidences[0];
    return lines.isEmpty()
      ? Optional.empty()
      : Optional.of(new Chunk(lines, label, avgConfidence, firstWordBox[0]));
  }

  public String getContent() {
    return content;
  }

  public void modifyContent(String newContent) {
    if (newContent.equals(content)) {
      return;
    }
    if (originalContent == null) {
      originalContent = content;
    }
    content = newContent;
  }

  public String getOriginalContent() {
    return originalContent;
  }

  public String getCorrectedContent() {
    return TextProcessor.correctForm(content);
  }

  public List<String> getLabels() {
    return Collections.unmodifiableList(labels);
  }

  public int getScore() {
    return score;
  }

  public void setScore(int score) {
    this.score = score;
  }

  public Rectangle getFirstWordBox() {
    return firstWordBox;
  }

  private static int calculateScore(float avgConfidence) {
    return Math.round(avgConfidence);
  }

  private static String hocrPreprocessContentLine(String l) {
    return HOCR_GARBAGE_CHARS_RE.matcher(l).replaceAll("");
  }
}
