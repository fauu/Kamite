import type { MaybeRuby } from "~/backend";

export type RawChunkText = string | MaybeRuby[];

export class ChunkText {
  #base?: string;
  #maybeRubies?: MaybeRuby[];

  private constructor(base?: string, maybeRubies?: MaybeRuby[]) {
    this.#base = base;
    this.#maybeRubies = maybeRubies;
  }

  static of(input: RawChunkText) {
    return typeof input === "string" ? new ChunkText(input) : new ChunkText(undefined, input);
  }

  get base(): string {
    if (this.#base === undefined) {
      this.#base = this.#maybeRubies!.map(mr => mr.base).join("");
    }
    return this.#base;
  }

  get maybeRubies(): MaybeRuby[] | undefined {
    return this.#maybeRubies;
  }

  get length(): number {
    return this.base.length;
  }

  get isEmpty(): boolean {
    return this.base.length < 1;
  }

  get hasFurigana(): boolean {
    return this.#maybeRubies !== undefined;
  }

  get withoutFurigana(): ChunkText {
    return new ChunkText(this.#base);
  }
}
