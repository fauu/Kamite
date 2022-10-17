export function kanaResizedVariant(c: string): string | undefined {
  for (const variants of KANA_VARIANTS_SMALL) {
    const cIdx = variants.indexOf(c);
    if (cIdx === -1) {
      continue;
    }
    return variants[cIdx === 0 ? 1 : 0];
  }
  return undefined;
}

export function kanaVoiceVariants(c: string): string[] {
  return characterReplacementVariants(c, KANA_VARIANTS_VOICED);
}

export function visuallySimilarCharacters(c: string): string[] {
  return characterReplacementVariants(c, VISUALLY_SIMILAR);
}

export function characterFunctionalReplacements(c: string): string[] {
  if (c === "ー") {
    return KATAKANA_VOWELS;
  } else if (KATAKANA_VOWELS.includes(c)) {
    return ["ー"];
  }
  return [];
}

function characterReplacementVariants(c: string, variantTable: string[][]): string[] {
  for (const variants of variantTable) {
    const cIdx = variants.indexOf(c);
    if (cIdx === -1) {
      continue;
    }
    return [...variants.slice(0, cIdx), ...variants.slice(cIdx + 1)];
  }
  return [];
}

export const KANA_VARIANTS_SMALL = [
  ["あ", "ぁ"],
  ["い", "ぃ"],
  ["う", "ぅ"],
  ["え", "ぇ"],
  ["お", "ぉ"],
  ["つ", "っ"],
  ["や", "ゃ"],
  ["ゆ", "ゅ"],
  ["よ", "ょ"],
  ["わ", "ゎ"],
  ["ア", "ァ"],
  ["イ", "ィ"],
  ["ウ", "ゥ"],
  ["エ", "ェ"],
  ["オ", "ォ"],
  ["ツ", "ッ"],
  ["ヤ", "ャ"],
  ["ユ", "ュ"],
  ["ヨ", "ョ"],
  ["ワ", "ヮ"],
  ["カ", "ヵ"],
  ["ケ", "ヶ"],
];

export const KANA_VARIANTS_VOICED = [
  ["か", "が"],
  ["き", "ぎ"],
  ["く", "ぐ"],
  ["け", "げ"],
  ["こ", "ご"],
  ["さ", "ざ"],
  ["し", "じ"],
  ["す", "ず"],
  ["せ", "ぜ"],
  ["そ", "ぞ"],
  ["た", "だ"],
  ["ち", "ぢ"],
  ["つ", "づ"],
  ["て", "で"],
  ["と", "ど"],
  ["は", "ば", "ぱ"],
  ["ほ", "ぼ", "ぽ"],
  ["ひ", "び", "ぴ"],
  ["ふ", "ぶ", "ぷ"],
  ["へ", "べ", "ぺ"],
  ["カ", "ガ"],
  ["キ", "ギ"],
  ["ク", "グ"],
  ["ケ", "ゲ"],
  ["コ", "ゴ"],
  ["サ", "ザ"],
  ["シ", "ジ"],
  ["ス", "ズ"],
  ["セ", "ゼ"],
  ["ソ", "ゾ"],
  ["タ", "ダ"],
  ["チ", "ヂ"],
  ["ツ", "ヅ"],
  ["テ", "デ"],
  ["ト", "ド"],
  ["ハ", "バ", "パ"],
  ["ヒ", "ビ", "ピ"],
  ["フ", "ブ", "プ"],
  ["ヘ", "ベ", "ペ"],
  ["ホ", "ボ", "ポ"],
];

export const VISUALLY_SIMILAR = [
  ["つ", "っ", "う"],
  ["さ", "き"],
  ["ン", "ソ"],
  ["シ", "ツ"],
  ["チ", "テ"],
  ["キ", "エ"],
  ["ー", "一"], // long vowel mark and '1'
  ["ハ", "ル", "へ", "八"],
  ["ク", "タ"],
  ["ロ", "口"], // 'ろ' and 'くち'
  ["人", "入"],
  ["天", "夭"],
  ["土", "士"],
  ["未", "末"],
  ["才", "オ"],
  ["力", "カ"],
  ["二", "ニ"], // '2' and katakana 'に'
];

const KATAKANA_VOWELS = ["ア", "イ", "ウ", "エ", "オ"];

/*
 * Adapted from WanaKana - https://github.com/WaniKani/WanaKana
 *
 *  The MIT License (MIT)
 *
 *  Copyright (c) 2013 WaniKani Community Github
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy of
 *  this software and associated documentation files (the "Software"), to deal in
 *  the Software without restriction, including without limitation the rights to
 *  use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 *  the Software, and to permit persons to whom the Software is furnished to do so,
 *  subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in all
 *  copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 *  FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 *  COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 *  IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 *  CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
const HIRAGANA_START = 0x3041;
const HIRAGANA_END = 0x3096;
const KATAKANA_START = 0x30A1;
const KATAKANA_END = 0x30FC;
const PROLONGED_SOUND_MARK = 0x30FC;
const KANA_SLASH_DOT = 0x30FB;

export function isCharHiragana(c: string): boolean {
  return isCharInRange(c, HIRAGANA_START, HIRAGANA_END);
}

export function isCharKatakana(c: string): boolean {
  return isCharInRange(c, KATAKANA_START, KATAKANA_END);
}

export function hiraganaToKatakana(text: string): string {
  return text.split("")
    .map((c: string) =>
      isCharLongDash(c) || isCharSlashDot(c) || !isCharHiragana(c)
      ? c
      : String.fromCharCode(c.charCodeAt(0) + (KATAKANA_START - HIRAGANA_START))
    )
    .join("");
}

export function katakanaToHiragana(text: string): string {
  let previousKana = "";
  return text
    .split("")
    .reduce((hira, char, index) => {
        if (isCharSlashDot(char) || isCharInitialLongDash(char, index) || isKanaAsSymbol(char)) {
          // Short circuit to avoid incorrect codeshift for 'ー' and '・'
          return hira.concat(char);
        } else if (previousKana && isCharInnerLongDash(char, index)) {
          // Transform long vowels: 'オー' to 'おう'
          return hira.concat(previousKana === "お" ? "う" : previousKana);
        } else if (!isCharLongDash(char) && isCharKatakana(char)) {
          const code = char.charCodeAt(0) + (HIRAGANA_START - KATAKANA_START);
          const hiraChar = String.fromCharCode(code);
          previousKana = hiraChar;
          return hira.concat(hiraChar);
        }
        previousKana = "";
        return hira.concat(char);
      }, [] as string[])
    .join("");
}

function isCharInRange(c: string, start: number, end: number): boolean {
  if (c === "") return false;
  const code = c.charCodeAt(0);
  return start <= code && code <= end;
}

function isCharSlashDot(c: string): boolean {
  if (c === "") return false;
  return c.charCodeAt(0) === KANA_SLASH_DOT;
}

function isCharLongDash(c: string): boolean {
  if (c === "") return false;
  return c.charCodeAt(0) === PROLONGED_SOUND_MARK;
}

function isCharInitialLongDash(c: string, idx: number): boolean {
 return isCharLongDash(c) && idx < 1;
}

function isCharInnerLongDash(c: string, idx: number): boolean {
 return isCharLongDash(c) && idx > 0;
}

function isKanaAsSymbol(c: string): boolean {
  return ["ヶ", "ヵ"].includes(c);
}
