package io.github.kamitejp.textprocessing.jumanpp;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

// XXX: Warning: This format doesn't work well with half-width spaces and double quotes ("). We recommend convert them to full-width characters before analysis when using this format.
public final class JumanppMorpheme {
  private static final String COMMENT_PREFIX = "#";
  private static final int EXPECTED_MIN_RAW_LINE_SEGS = 12;
  private static final String ALIASING_TOKEN_MARK = "@";
  private static final int SURFACE_FORM_IDX = 0;
  private static final int READING_IDX = 1;
  private static final int DICTIONARY_FORM_IDX = 2;
  private static final int POS_START_IDX = 3;
  private static final int NUM_POS_FIELDS = 8;
  private static final int SEMANTIC_FEATURES_IDX = POS_START_IDX + NUM_POS_FIELDS;
  private static final String POS_EMPTY_MARK = "*";

  private JumanppMorpheme() {}

  private boolean aliasing;
  private String surfaceForm;
  private String reading;
  private String dictionaryForm;
  private List<String> pos;
  private String semanticFeatures;

  public static Optional<JumanppMorpheme> fromJumanLine(String line) {
    if (line == null) {
      return Optional.empty();
    }
    line = line.trim();
    if (line.isBlank() || line.startsWith(COMMENT_PREFIX)) {
      return Optional.empty();
    }

    // XXX: POS are pairs: (probably) 品詞 品詞細分類 活用型(ctype) 活用形(cform)
    //      first in spelled-out form, then in id form. * = id 0. Should probably only
    //      parse the ids

    var m = new JumanppMorpheme();
    var s = line.split(" ");
    var expectedSegs = EXPECTED_MIN_RAW_LINE_SEGS;
    var offset = 0;
    if (s.length > 0 && s[0].equals(ALIASING_TOKEN_MARK)) {
      offset = 1;
      m.aliasing = true;
    }
    if (s.length < expectedSegs + offset) {
      return Optional.empty();
    }
    m.surfaceForm = s[offset + SURFACE_FORM_IDX];
    m.reading = s[offset + READING_IDX];
    m.dictionaryForm = s[offset + DICTIONARY_FORM_IDX];
    m.pos = new ArrayList<String>(NUM_POS_FIELDS);
    var posStartIdx = offset + POS_START_IDX;
    for (var i = posStartIdx; i < posStartIdx + NUM_POS_FIELDS; i++) {
      var pos = s[offset + i];
      if (pos.equals(POS_EMPTY_MARK)) {
        pos = null;
      }
      m.pos.add(pos);
    }

    // TODO: if s[offset + SURFACE_FORM_IDX] starts with "\"", then concat this segment with all
    //       the remaining ones, strip quotes and set m.semanticFeatures to that string.

    return Optional.of(m);
  }

  public boolean isAliasing() {
    return aliasing;
  }

  public String getSurfaceForm() {
    return surfaceForm;
  }

  public String getReading() {
    return reading;
  }

  public String getDictionaryForm() {
    return dictionaryForm;
  }
}
