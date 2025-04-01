export interface Config {
  controlWindow: boolean,
  launchBrowser: boolean,
  ocr: Ocr,
  ui: Ui,
  commands: Commands,
  server: Server,
  chunk: Chunk,
  keybindings: Keybindings,
  dev: Dev,
  lookup: Lookup,
  sessionTimer: SessionTimer,
}

export type UiLayout = "STANDARD" | "STANDARD_FLIPPED";

export type OcrEngine = "TESSERACT" | "MANGAOCR";

interface Ocr {
  engine: OcrEngine,
  regions: OcrRegion[] | null,
}

export interface OcrRegion {
  symbol: string,
  description: string | null,
  x: number,
  y: number,
  width: number,
  height: number,
  autoNarrow: boolean,
}

interface Ui {
  focusMode: boolean,
  layout: UiLayout,
  notebook: Notebook,
}

interface Server {
  port: number,
}

interface Chunk {
  flash: boolean,
  furigana: Furigana,
  translationOnlyMode: boolean,
}

interface Furigana {
  enable: boolean,
  conceal: boolean,
}

interface Keybindings {
  global: Global,
}

interface Global {
  ocr: KeybindingsOcr,
}

interface KeybindingsOcr {
  manualBlock: string | null,
  manualBlockRotated: string | null,
  autoBlock: string | null,
  autoBlockSelect: string | null,
}

interface Commands {
  player: PlayerCommands,
  custom: CustomCommand[] | null,
}

interface PlayerCommands {
  showExtra: boolean,
}

export interface CustomCommand {
  symbol: string,
  name: string,
  command: string[],
}

interface Notebook {
  collapse: boolean,
  height: number | null,
}

interface Dev {
  serveStaticInDevMode: boolean,
}

interface Lookup {
  targets: LookupTarget[],
}

export interface LookupTarget {
  symbol: string,
  name: string,
  url: string,
  newTab: boolean,
}

export function lookupTargetFillURL(t: LookupTarget, q: string) {
  return t.url.replace("{}", q);
}

interface SessionTimer {
  autoPause: AutoPause,
}

interface AutoPause {
  enable: boolean,
  after: number | null,
}
