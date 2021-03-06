export interface Config {
  controlWindow: boolean,
  launchBrowser: boolean,
  ocr: OCR,
  ui: UI,
  commands: Commands,
  server: Server,
  chunk: Chunk,
  keybindings: Keybindings,
  dev: Dev,
}

export type UILayout = "STANDARD" | "STANDARD_FLIPPED";

export type OCREngine = "TESSERACT" | "MANGAOCR";

interface OCR {
  engine: OCREngine,
  regions: OCRRegion[] | null,
}

export interface OCRRegion {
  symbol: string,
  description: string | null,
  x: number,
  y: number,
  width: number,
  height: number,
  autoNarrow: boolean,
}

interface UI {
  layout: UILayout,
  notebook: Notebook,
}

interface Server {
  port: number,
}

interface Chunk {
  breakLines: boolean,
  flash: boolean,
  showFurigana: boolean,
  translationOnlyMode: boolean,
}

interface Keybindings {
  global: Global,
}

interface Global {
  recognize: Recognize,
}

interface Recognize {
  autoBlock: string | null,
  manualBlock: string | null,
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
  command: string,
}

interface Notebook {
  height: number | null,
}

interface Dev {
  serveStaticInDevMode: boolean,
}
