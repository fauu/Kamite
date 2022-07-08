import type { MaybeRuby } from "~/backend";

type MaybeRubiesWithOffset = { maybeRuby : MaybeRuby, offset: number };

export type RawChunkText = string | MaybeRuby[];

export class ChunkText {
  #base?: string;
  #maybeRubies?: MaybeRuby[];
  #maybeRubiesWithOffset?: MaybeRubiesWithOffset[];

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

  get maybeRubiesWithOffset(): MaybeRubiesWithOffset[] | undefined {
    if (!this.#maybeRubiesWithOffset && this.maybeRubies) {
      let offset = 0;
      this.#maybeRubiesWithOffset = this.#maybeRubies?.map(mr => {
        const initialOffset = offset;
        offset += mr.base.length;
        return { maybeRuby: mr, offset: initialOffset };
      });
    }
    return this.#maybeRubiesWithOffset;
  }

  get length(): number {
    return this.base.length;
  }

  get hasFurigana(): boolean {
    return this.#maybeRubies !== undefined;
  }

  get withoutFurigana(): ChunkText {
    return new ChunkText(this.#base);
  }
}
