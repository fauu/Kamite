/*
 * Parts adapted with original modifications from kuroshiro - https://github.com/hexenq/kuroshiro.
 * For Kamite project license information, please see the COPYING.md file.
 *
 * The following is the license notice from the underlying work:
 *
 *  The MIT License (MIT)
 *
 *  Copyright (c) 2015-2021 Hexen Qi <hexenq@gmail.com>
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

package io.github.kamitejp.textprocessing;

import static java.util.Map.entry;
import static java.util.stream.Collectors.toList;

import java.lang.Character.UnicodeScript;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import com.atilika.kuromoji.unidic.kanaaccent.Token;
import com.atilika.kuromoji.unidic.kanaaccent.Tokenizer;

public class TextProcessor {
  private static final int KATAKANA_HIRAGANA_SHIFT = 0x3041 - 0x30A1;

  private Tokenizer tokenizer;

  private class ProcessingToken {
    public String surfaceForm;
    public String reading;
    public String partOfSpeech;

    public ProcessingToken(String surfaceForm, String reading, String partOfSpeech) {
      this.surfaceForm = surfaceForm;
      this.reading = reading;
      this.partOfSpeech = partOfSpeech;
    }

    public boolean partOfSpeechNonEmptyEquals(String pos) {
      return partOfSpeech != null && !partOfSpeech.isEmpty() && pos.equals(partOfSpeech); // NOPMD - we need to check for empty before checking for equals, and for that we need to check for null early
    }
  }

  private record ThinToken(String surfaceForm, String reading) {}

  private enum NotationBaseType {
    KANJI,
    KANA,
    OTHER
  }

  private record Notation(
    String base,
    NotationBaseType baseType,
    String notation
  ) {}

  public static String correctForm(String s) {
    return kanaHalfToFull(s)
      .trim()
      .replace("．．．", "…")
      .replace("．．", "‥")
      .replace("...", "…")
      .replace("..", "‥")
      .replaceAll("[\\r\\n]+", "\n")
      .replaceAll("[\\p{Cntrl}&&[^\n]]", "");
  }

  public ChunkWithFurigana addFurigana(String s) {
    if (tokenizer == null) {
      tokenizer = new Tokenizer();
    }

    var tokens = patchTokens(tokenizer.tokenize(s));
    var notations = new ArrayList<Notation>();
    for (var t : tokens) {
      switch (classify(t.surfaceForm())) { // NOPMD
        case KANJI_WITHOUT_KANA ->
          notations.add(new Notation(t.surfaceForm(), NotationBaseType.KANJI, t.reading()));

        case KANJI_WITH_KANA -> {
          var patternBuilder = new StringBuilder();
          var isLastTokenKanji = false;
          var subs = new ArrayList<List<Integer>>();
          for (var cp : (Iterable<Integer>) t.surfaceForm().codePoints()::iterator) {
            if (isKanji(cp)) {
              if (isLastTokenKanji) {
                subs.get(subs.size() - 1).add(cp);
              } else {
                isLastTokenKanji = true;
                patternBuilder.append("(.+)");
                subs.add(Stream.of(cp).collect(toList()));
              }
            } else {
              isLastTokenKanji = false;
              subs.add(Stream.of(cp).collect(toList()));
              patternBuilder.append(codePointToString(isKatakana(cp) ? toRawHiragana(cp) : cp));
            }
          }
          var re = Pattern.compile("^%s$".formatted(patternBuilder));
          var m = re.matcher(toRawHiragana(t.reading()));
          if (m.find()) {
            var pickKanji = 1;
            for (var sub : subs) {
              var subStr = codePointListToString(sub);
              if (isKanji(sub.get(0))) {
                notations.add(new Notation(subStr, NotationBaseType.KANJI, m.group(pickKanji)));
                pickKanji++;
              } else {
                notations.add(new Notation(subStr, NotationBaseType.KANA, toRawHiragana(subStr)));
              }
            }
          } else {
            notations.add(new Notation(t.surfaceForm(), NotationBaseType.KANJI, t.reading()));
          }
        }

        case KANA_ONLY -> {
          for (var cp : (Iterable<Integer>) t.surfaceForm().codePoints()::iterator) {
            notations.add(new Notation(codePointToString(cp), NotationBaseType.KANA, null));
          }
        }

        case NO_JAPANESE_SCRIPT -> {
          for (var cp : (Iterable<Integer>) t.surfaceForm().codePoints()::iterator) {
            notations.add(new Notation(codePointToString(cp), NotationBaseType.OTHER, null));
          }
        }
      }
    }

    return new ChunkWithFurigana(
      notations.stream()
        .map(n ->
          n.baseType() == NotationBaseType.KANJI
            ? MaybeRuby.ruby(n.base(), n.notation())
            : MaybeRuby.notRuby(n.base())
        )
        .toList()
    );
  }

  private enum TextClassification {
    KANJI_WITHOUT_KANA,
    KANJI_WITH_KANA,
    KANA_ONLY,
    NO_JAPANESE_SCRIPT
  }

  private TextClassification classify(String s) {
    var hasKanji = false;
    var hasKana = false;
    for (var cp : (Iterable<Integer>) s.codePoints()::iterator) {
      var script = Character.UnicodeScript.of(cp);
      if (isKanji(script)) {
        hasKanji = true;
      } else if (isKana(script)) {
        hasKana = true;
      }
      if (hasKanji && hasKana) {
        return TextClassification.KANJI_WITH_KANA;
      }
    }

    if (hasKanji) {
      return TextClassification.KANJI_WITHOUT_KANA;
    }
    if (hasKana) {
      return TextClassification.KANA_ONLY;
    }
    return TextClassification.NO_JAPANESE_SCRIPT;
  }

  private List<ThinToken> patchTokens(List<Token> tokens) {
    var processingTokens = tokens.stream().map(t ->
      new ProcessingToken(t.getSurface(), t.getKana(), t.getPartOfSpeechLevel1())
    ).collect(toList());

    // Patch for token structure
    for (var t : processingTokens) {
      String newReading = t.surfaceForm;
      if (hasJapanese(t.surfaceForm)) {
        if ("*".equals(t.reading)) {
          if (isKana(t.surfaceForm)) {
            newReading = toRawHiragana(t.surfaceForm);
          }
        } else if (hasKatakana(t.reading)) {
          newReading = toRawHiragana(t.reading);
        } else {
          newReading = t.reading;
        }
      }
      t.reading = newReading;
    }

    // PERF: Verify if the following patches are necessary for UniDic

    // Patch for 助動詞"う" after 動詞
    for (var i = 0; i < processingTokens.size(); i++) {
      var t = processingTokens.get(i);
      if (
        t.partOfSpeechNonEmptyEquals("助動詞")
        && ("う".equals(t.surfaceForm) || "ウ".equals(t.surfaceForm))
      ) {
        if (i - 1 >= 0) {
          var prevT = processingTokens.get(i - 1);
          if (prevT.partOfSpeechNonEmptyEquals("動詞")) {
            prevT.surfaceForm += "う";
            // Pronunciation patch omitted here
            prevT.reading += "ウ";
            processingTokens.remove(i);
            i--;
          }
        }
      }
    }

    // Patch for "っ" at the tail of 動詞、形容詞
    for (var i = 0; i < processingTokens.size(); i++) {
      var t = processingTokens.get(i);
      if (t.partOfSpeechNonEmptyEquals("動詞") || t.partOfSpeechNonEmptyEquals("形容詞")) {
        var cpLength = t.surfaceForm.codePointCount(0, t.surfaceForm.length());
        if (cpLength > 1) {
          var lastCp = t.surfaceForm.codePointAt(cpLength - 1);
          if (lastCp == (int) 'っ' || lastCp == (int) 'ッ') {
            if (i + 1 < processingTokens.size()) {
              var nextT = processingTokens.get(i + 1);
              t.surfaceForm += nextT.surfaceForm;
              // Pronunciation patch omitted here
              t.reading += nextT.reading;
              processingTokens.remove(i + 1);
              i--;
            }
          }
        }
      }
    }

    return processingTokens.stream().map(t -> new ThinToken(t.surfaceForm, t.reading)).toList();
  }

  private static boolean hasJapanese(String s) {
    return s.codePoints().anyMatch(TextProcessor::isJapanese);
  }

  private static boolean hasKatakana(String s) {
    return s.codePoints().anyMatch(c -> isKatakana(Character.UnicodeScript.of(c)));
  }

  private static boolean isJapanese(int c) {
    return isJapanese(Character.UnicodeScript.of(c));
  }

  private static boolean isJapanese(UnicodeScript s) {
    return isKanji(s) || isKana(s);
  }

  private static boolean isKana(String s) {
    return s.codePoints().allMatch(TextProcessor::isKana);
  }

  private static boolean isKanji(int c) {
    return isKanji(Character.UnicodeScript.of(c));
  }

  private static boolean isKanji(UnicodeScript s) {
    return s == Character.UnicodeScript.HAN;
  }

  private static boolean isKana(int c) {
    return isKana(Character.UnicodeScript.of(c));
  }

  private static boolean isKana(UnicodeScript s) {
    return isHiragana(s) || isKatakana(s);
  }

  private static boolean isHiragana(UnicodeScript s) {
    return s == Character.UnicodeScript.HIRAGANA;
  }

  private static boolean isKatakana(int c) {
    return isKatakana(Character.UnicodeScript.of(c));
  }

  private static boolean isKatakana(UnicodeScript s) {
    return s == Character.UnicodeScript.KATAKANA;
  }

  private static String toRawHiragana(String s) {
    return s.codePoints()
      .map(TextProcessor::toRawHiragana)
      .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
      .toString();
  }

  private static int toRawHiragana(int c) {
   return c + (c > 0x30A0 && c < 0x30F7 ? KATAKANA_HIRAGANA_SHIFT : 0);
  }

  private static String codePointToString(int cp) {
    return new String(new int[]{ cp }, 0, 1);
  }

  private static String codePointListToString(List<Integer> cpList) {
    return new String(
      cpList.stream().mapToInt(Integer::intValue).toArray(),
      0,
      cpList.size()
    );
  }

  private static String kanaHalfToFull(String s) {
    var lineBuilder = new StringBuilder();
    var skip = 0;
    var cps = s.codePoints().toArray();
    for (var i = 0; i < cps.length; i++) {
      if (skip > 0) {
        skip--;
        continue;
      }

      var cp = cps[i];

      if (cp < 0xFF61 || cp > 0xFF9F || !KANA_HALF2FULL_BASE.containsKey(cp)) {
        lineBuilder.appendCodePoint(cp);
        continue;
      }

      Integer newCP = null;

      if (i + 2 < cps.length) {
        if (cps[i + 1] == ' ') {
          if (cps[i + 2] == 0x3099) {
            newCP = KANA_HALF2FULL_VOICED.get(cp);
            skip = 2;
          } else if (cps[i + 2] == 0x309A) {
            newCP = KANA_HALF2FULL_SEMIVOICED.get(cp);
            skip = 2;
          }
        }
      }
      if (newCP == null && i + 1 < cps.length) {
        var lookahead = cps[i + 1];
        if (lookahead == 0x309B || lookahead == 0xFF9E) {
          newCP = KANA_HALF2FULL_VOICED.get(cp);
          skip = 1;
        } else if (lookahead == 0x309C || lookahead == 0xFF9F) {
          newCP = KANA_HALF2FULL_SEMIVOICED.get(cp);
          skip = 1;
        }
      }

      if (newCP == null) {
        newCP = KANA_HALF2FULL_BASE.get(cp);
      }

      if (newCP != null) {
        lineBuilder.appendCodePoint(newCP);
      }
    }
    return lineBuilder.toString();
  }

  private static final Map<Integer, Integer> KANA_HALF2FULL_BASE = Map.ofEntries(
    entry(0xFF61, 0x3002),
    entry(0xFF62, 0x300C),
    entry(0xFF63, 0x300D),
    entry(0xFF64, 0x3001),
    entry(0xFF65, 0x30FB),
    entry(0xFF66, 0x30F2),
    entry(0xFF67, 0x30A1),
    entry(0xFF68, 0x30A3),
    entry(0xFF69, 0x30A5),
    entry(0xFF6A, 0x30A7),
    entry(0xFF6B, 0x30A9),
    entry(0xFF6C, 0x30E3),
    entry(0xFF6D, 0x30E5),
    entry(0xFF6E, 0x30E7),
    entry(0xFF6F, 0x30C3),
    entry(0xFF70, 0x30FC),
    entry(0xFF71, 0x30A2),
    entry(0xFF72, 0x30A4),
    entry(0xFF73, 0x30A6),
    entry(0xFF74, 0x30A8),
    entry(0xFF75, 0x30AA),
    entry(0xFF76, 0x30AB),
    entry(0xFF77, 0x30AD),
    entry(0xFF78, 0x30AF),
    entry(0xFF79, 0x30B1),
    entry(0xFF7A, 0x30B3),
    entry(0xFF7B, 0x30B5),
    entry(0xFF7C, 0x30B7),
    entry(0xFF7D, 0x30B9),
    entry(0xFF7E, 0x30BB),
    entry(0xFF7F, 0x30BD),
    entry(0xFF80, 0x30BF),
    entry(0xFF81, 0x30C1),
    entry(0xFF82, 0x30C4),
    entry(0xFF83, 0x30C6),
    entry(0xFF84, 0x30C8),
    entry(0xFF85, 0x30CA),
    entry(0xFF86, 0x30CB),
    entry(0xFF87, 0x30CC),
    entry(0xFF88, 0x30CD),
    entry(0xFF89, 0x30CE),
    entry(0xFF8A, 0x30CF),
    entry(0xFF8B, 0x30D2),
    entry(0xFF8C, 0x30D5),
    entry(0xFF8D, 0x30D8),
    entry(0xFF8E, 0x30DB),
    entry(0xFF8F, 0x30DE),
    entry(0xFF90, 0x30DF),
    entry(0xFF91, 0x30E0),
    entry(0xFF92, 0x30E1),
    entry(0xFF93, 0x30E2),
    entry(0xFF94, 0x30E4),
    entry(0xFF95, 0x30E6),
    entry(0xFF96, 0x30E8),
    entry(0xFF97, 0x30E9),
    entry(0xFF98, 0x30EA),
    entry(0xFF99, 0x30EB),
    entry(0xFF9A, 0x30EC),
    entry(0xFF9B, 0x30ED),
    entry(0xFF9C, 0x30EF),
    entry(0xFF9D, 0x30F3),
    entry(0xFF9E, 0x3099),
    entry(0xFF9F, 0x309A)
  );

  private static final Map<Integer, Integer> KANA_HALF2FULL_SEMIVOICED = Map.of(
    0xFF8A, 0x30D1,
    0xFF8B, 0x30D4,
    0xFF8C, 0x30D7,
    0xFF8D, 0x30DA,
    0xFF8E, 0x30DD
  );

  private static final Map<Integer, Integer> KANA_HALF2FULL_VOICED = Map.ofEntries(
    entry(0xFF66, 0x30FA),
    entry(0xFF73, 0x30F4),
    entry(0xFF76, 0x30AC),
    entry(0xFF77, 0x30AE),
    entry(0xFF78, 0x30B0),
    entry(0xFF79, 0x30B2),
    entry(0xFF7A, 0x30B4),
    entry(0xFF7B, 0x30B6),
    entry(0xFF7C, 0x30B8),
    entry(0xFF7D, 0x30BA),
    entry(0xFF7E, 0x30BC),
    entry(0xFF7F, 0x30BE),
    entry(0xFF80, 0x30C0),
    entry(0xFF81, 0x30C2),
    entry(0xFF82, 0x30C5),
    entry(0xFF83, 0x30C7),
    entry(0xFF84, 0x30C9),
    entry(0xFF8A, 0x30D0),
    entry(0xFF8B, 0x30D3),
    entry(0xFF8C, 0x30D6),
    entry(0xFF8D, 0x30D9),
    entry(0xFF8E, 0x30DC),
    entry(0xFF9C, 0x30F7)
  );
}
