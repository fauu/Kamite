import io
import sys

import loguru
import PIL.Image

from manga_ocr import MangaOcr

loguru.logger.disable("manga_ocr")

mocr = MangaOcr()

sys.stdout.reconfigure(encoding="utf-8") # Needed on Windows

print("READY", flush=True)

with open(sys.stdin.fileno(), "rb", closefd=False) as f:
    while True:
        size = int.from_bytes(f.read(4), byteorder='big')
        img_bytes = f.read(size)
        img = PIL.Image.open(io.BytesIO(img_bytes))
        text = mocr(img)
        print(text, flush=True)
