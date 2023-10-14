import type { ChunksState } from "~/chunk";

import {
  characterFunctionalReplacements, kanaResizedVariant, kanaVoiceVariants,
  visuallySimilarCharacters, isCharHiragana, isCharKatakana
} from "./textTransform";

export type Action = (
  | { kind: "undo" }
  | { kind: "redo" }
  | { kind: "select-all" }
  | { kind: "delete-selected" }
  | { kind: "delete-every-second-char" }
  | { kind: "duplicate-selected" }
  | { kind: "copy-all" }
  | { kind: "copy-selected" }
  | { kind: "copy-original" }
  | { kind: "transform-selected", into: string }
  | { kind: "hiragana-to-katakana" }
  | { kind: "katakana-to-hiragana" }
)
& { disabled?: boolean };

export type ActionKind = {
  staticLabel?: string,
  description?: string,
  hasAlternativeInvocation?: boolean,
};

export const actionKinds: Readonly<Record<Action["kind"], ActionKind>> = {
  "undo": { staticLabel: "Undo" },
  "redo": {
    staticLabel: "Redo",
    description: "Next (short click) or last (long click) chunk",
    hasAlternativeInvocation: true,
  },
  "select-all": { staticLabel: "Select all" },
  "delete-selected": { staticLabel: "Delete" },
  "delete-every-second-char": { staticLabel: "Delete every 2<sup>nd</sup> char." },
  "duplicate-selected": { staticLabel: "Duplicate" },
  "copy-all": { staticLabel: "Copy all" },
  "copy-selected": { staticLabel: "Copy" },
  "copy-original": { staticLabel: "Copy original" },
  "transform-selected": {},
  "hiragana-to-katakana": { staticLabel: "To katakana" },
  "katakana-to-hiragana": { staticLabel: "To hiragana" },
};

export type ActionInvocation = "base" | "alternative";

export function actionsInclude(
  actions: Action[] | undefined,
  kind: Action["kind"] | Action["kind"][]
) {
  if (!actions) {
    return;
  }
  const manyKinds = Array.isArray(kind);
  return actions.some(a => manyKinds ? kind.includes(a.kind) : kind === a.kind);
}

export function availableActions(chunks: ChunksState): Action[] {
  const res: Action[] = [];

  if (chunks.editing()) {
    return res;
  }

  res.push({ kind: "undo", disabled: !chunks.canTravelBy(-1) });
  res.push({ kind: "redo", disabled: !chunks.canTravelBy(1) });

  if (chunks.textSelection.get() && chunks.currentEffectiveText().length === 1) {
    const candidateTransforms = [];

    const resizedVariant = kanaResizedVariant(chunks.currentEffectiveText());
    if (resizedVariant) {
      candidateTransforms.push(resizedVariant);
    }
    [kanaVoiceVariants, visuallySimilarCharacters, characterFunctionalReplacements].map(f =>
      candidateTransforms.push(...f(chunks.currentEffectiveText()))
    );

    [...new Set(candidateTransforms)]
      .map(c => res.push({ kind: "transform-selected", into: c }));
  }

  const fullLen = chunks.current().text.length;
  const sel = chunks.textSelection.get();
  if (fullLen > 0) {
    if (!sel || (sel.range[1] - sel.range[0]) < fullLen) {
      res.push({ kind: "select-all" });
    }
    if (!sel) {
      res.push({ kind: "copy-all" });
    }
  }

  if (
    chunks.current().originalText
    && (!chunks.textSelection.get() || chunks.textSelection.isWholeTextSelected())) {
    res.push({ kind: "copy-original" });
  }

  if (chunks.textSelection.get()) {
    const [hiraganaCount, katakanaCount] = chunks.currentEffectiveText()
      .split("")
      .reduce(
        ([h, k], c) => [h + +isCharHiragana(c), k + +isCharKatakana(c)],
        [0, 0]
      );
    if (hiraganaCount > 0 && katakanaCount === 0) {
      res.push({ kind: "hiragana-to-katakana" });
    } else if (katakanaCount > 0 && hiraganaCount === 0) {
      res.push({ kind: "katakana-to-hiragana" });
    }
    res.push({ kind: "copy-selected" });
    res.push({ kind: "duplicate-selected" });
    res.push({ kind: "delete-selected" });
  }

  if (chunks.currentEffectiveText().length > 1) {
    res.push({ kind: "delete-every-second-char" });
  }

  return res;
}
