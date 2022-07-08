const DECAY_AFTER_CHUNK_CHANGES = 3;

export type ChunkTranslationSegment = {
  text: string,
  playbackTimeS?: number,
  tentative?: boolean,
};

export class ChunkTranslation {
  #segments: ChunkTranslationSegment[];
  #decayCounter?: number;

  private constructor(segments: ChunkTranslationSegment[], decayCounter?: number) {
    this.#segments = segments;
    this.#decayCounter = decayCounter;
  }

  decaying(): ChunkTranslation | undefined {
    let newCounter = this.#decayCounter || DECAY_AFTER_CHUNK_CHANGES;
    newCounter--;
    return newCounter > 0 ? new ChunkTranslation(this.#segments, newCounter) : undefined;
  }

  supplementedWith(segment: ChunkTranslationSegment): ChunkTranslation {
    // Don't allow duplicate segments when supplementing
    return this.#hasSegment(segment)
      ? this
      : new ChunkTranslation([...this.#segments, segment]);
  }

  removingTentativeSegmentsSharedWith(other: ChunkTranslation): ChunkTranslation | undefined {
    return ChunkTranslation.#withSegments(
      this.#segments.filter(s => !s.tentative || !other.#hasSegment(s))
    );
  }

  #hasSegment(segment: ChunkTranslationSegment) {
    return this.#segments.some(s => ChunkTranslation.segmentsSame(s, segment));
  }

  static segmentsSame(a: ChunkTranslationSegment, b: ChunkTranslationSegment): boolean {
    return a.text === b.text && a.playbackTimeS === b.playbackTimeS;
  }

  static segmentsToString(segments: ChunkTranslationSegment[]) {
    return segments.map(s => s.text).join(" ");
  }

  static withSegment(segment: ChunkTranslationSegment): ChunkTranslation | undefined {
    return new ChunkTranslation([segment]);
  }

  static #withSegments(segments: ChunkTranslationSegment[]): ChunkTranslation | undefined {
    return segments.length > 0 ? new ChunkTranslation(segments) : undefined;
  }

  get text(): string {
    return ChunkTranslation.segmentsToString(this.#segments);
  }

  get segments(): ChunkTranslationSegment[] {
    return this.#segments;
  }

  get lastSegmentPlaybackTimeS(): number | undefined {
    return this.#segments.length === 0
      ? undefined
      : this.#segments[this.#segments.length - 1].playbackTimeS;
  }
}
