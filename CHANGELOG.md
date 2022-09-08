# Changelog — Kamite

## [Unreleased]

### Added

* Combined profile: Multiple extra config files can now be loaded
  (`--profile=first-profile,second-profile,third-profile`).
* An alternative OCR.space engine “3” can now be used
  (`ocr.ocrspace.engine = 3`).
* Long-clicking the “Redo” button in the action palette will now skip
  forward all the way to the latest chunk.
* New `misc_lookup` command in the command API for externally invoking lookups
  within Kamite’s client.

### Changed

* (API) When sending commands through HTTP, the params JSON object is now
  expected directly in the request body instead of in a form param. **BREAKING
  CHANGE (API): command HTTP request body must be changed into the form
  `{"chunk": "Example text"}` instead of `params={"chunk": "Example text"}`.**
  * **The Textractor extension must be updated to the current version.**
  * **Gomics-v must be updated to version 0.2.**
* Switched tokenizer dictionary from IPADIC to UniDic for improved accuracy of
  auto-generated furigana.
* (Windows) The `Kamite.ps1` console launcher script has been replaced with
  `Kamite.com`.
* (Windows) `Kamite.bat` script has been added for convenient launching in
  debug mode with console window.
* (Windows) The launcher now uses a different launch method.
* Overflowing action palette scrolling UX no longer involves a scrollbar.
* When chunk correction is enabled (default), some garbage characters, as well
  as leading and trailing whitespace, are now removed from incoming chunks.
* OCR input images are now displayed in the Debug tab (available when launched
  with `--debug`).
* Parameters in custom commands can now be put in single quotemarks to avoid
  splitting by spaces.
* Character counter now only counts characters in Unicode categories Letter,
  Number, and Symbol.

### Fixed

* Custom commands with placeholder parameters no longer break after first use.
* No longer crashes when some duplicate launch options are provided.
* Certain special characters in params of commands sent through HTTP, such as
  `&` or `$`, no longer cause command processing to fail.
* (Windows) Can now be pinned to the taskbar properly.
* Some weird behaviours have been eliminated around client
  connecting/disconnecting in the presence of competing client tabs.
* (Windows) An overflowing action palette no longer pushes the chunk view down
  in Firefox.
* A missing indicator icon to distinguish buttons for lookups that open in a
  new tab has been restored.
* Chunk text selection now behaves correctly in Flipped interface layout when
  cursor is dragged above the chunk while selecting.
* When switching to a different chunk from chunk history after changing the
  “Show furigana” setting, that setting is now applied to the current chunk
  switched into.
* Overflowing current chunk area can now be scrolled when in flipped layout.
* Clicking a command button no longer removes selection from the current chunk
  (this made impossible passing selected text to a custom command invoked by a
  click).

## [0.5] – 2022-08-21

### Added

* Windows support.
* Region Helper mode: Launch Kamite with `--regionHelper` to obtain desired
  region parameters for OCR Region setup.

### Changed

* Monospace UI text elements are now rendered using embedded Roboto Mono font.

### Fixed

* Client now initializes properly when superseding a previously connected
  client.
* Some minor UI details on Chrome now look like they are supposed to.

## [0.4] – 2022-08-19

### Added

* New OCR engine: “Manga OCR” Online (via [a Hugging Face Space by Gryan
  Galario][manga-ocr-hf-gg]) (`ocr.engine = mangaocr_online`).
* Auto Block OCR Instant mode: the Auto Block OCR command now takes a parameter
  that determines whether to ask the user to click a point or to immediately
  take the current mouse position. `keybindings.global.ocr.autoBlock` now binds
  to the instant variant, while `.autoBlockSelect` binds to the variant
  mediated by a mouse click.
* Translation-only mode: Option that treats incoming chunks as translations and
  creates a new chunk for each translation. Useful when watching media with
  just the translation subtitles (`chunk.translationOnlyMode = true`).
* Clipboard Inserter ([Firefox][clipboard-inserter-ff],
  [Chrome][clipboard-inserter-chrome]) browser extension integration—when
  active, clipboard text will be automatically grabbed as a new Kamite chunk.

[manga-ocr-hf-gg]: https://huggingface.co/spaces/gryan-galario/manga-ocr-demo
[clipboard-inserter-ff]: https://addons.mozilla.org/en-US/firefox/addon/clipboard-inserter/
[clipboard-inserter-chrome]: https://chrome.google.com/webstore/detail/clipboard-inserter/deahejllghicakhplliloeheabddjajm

### Changed

* mpv integration rework: The mpv script has been removed, the IPC connection
  with mpv is now used for extracting subtitles. **BREAKING CHANGE: please
  remove the old mpv script (`~/.config/mpv/scripts/kamite.lua`) and make sure
  that mpv is launched with the parameter
  `--input-ipc-server="/tmp/kamite-mpvsocket"`**.
* “Manga OCR” integration rework: The `mangaocr.sh` wrapper script is no longer
  used and in its absence the Python executable used is no longer the `PATH`
  `python`, but the default pipx manga-ocr installation path. **BREAKING
  CHANGE: please either provide a path to a Python launcher that can access the
  `manga_ocr` module under the config key `ocr.mangaocr.pythonPath` or simply
  reinstall “Manga OCR” using pipx (`pipx install manga-ocr`)**.
* Text transform action has gained additional visually similar character pairs.
* Tesseract executable can now be customized (`ocr.tesseract.path`; default is
  `tesseract`).
* Tesseract error reporting has been improved.
* Linux launcher bash script has been replaced with a launcher program.
* The client UI now uses scalable icons.

### Fixed

* Successfully loading a profile config file is no longer falsely reported when
  the file is not readable.
* A rare failure in manga text block detector.
* Command palette button tooltip is now correctly hidden when the browser loses
  focus to a point/area selection program.

## [0.3] – 2022-07-21

### Added

* OCR directory watcher: watches a specified directory for new/modified images
  and automatically performs text recognition on them.
* Linux / GNOME Wayland and Linux / Plasma Wayland support (partial—no global
  OCR commands).

### Changed

* The mpv script now works in the absence of D-Bus on the system (using *curl*).
* Improved accuracy and performance of manga text block detection.
* The Textractor extension’s name has slightly changed.
* Tesseract version requirement has been relaxed so that versions other than 5.X
  are now allowed.

### Fixed

* When exiting the chunk edit mode by clicking a lookup button, the edit
  changes are now committed first, so that the new chunk version is used for the
  lookup.
* “Show furigana” setting is now honored when exiting chunk edit mode.
* Some chunk insertion operations no longer inadvertently cause travel to the most
  recent item in chunk history.
* Copying a part of the current chunk no longer flashes the entirety of it but just
  the copied characters.
* The plaform is now identified correctly in some cases where it has not been
  previously.
* Not providing all possible global keybindings on a platform that supports them
  no longer causes a startup failiure.
* The Auto block OCR command now waits for confirmation of the screen point
  selection like on other platforms instead of being activated instantaneously.
* The control window now has a correct name on GNOME.

## [0.2] – 2022-07-12

### Fixed

* Images incoming through the `ocr_image` command are now converted properly and
  no longer produce degraded OCR results.

## [0.1] – 2022-07-12

Initial release.

[Unreleased]: https://github.com/fauu/Kamite/compare/v0.5...HEAD
[0.5]: https://github.com/fauu/Kamite/releases/tag/v0.5
[0.4]: https://github.com/fauu/Kamite/releases/tag/v0.4
[0.3]: https://github.com/fauu/Kamite/releases/tag/v0.3
[0.2]: https://github.com/fauu/Kamite/releases/tag/v0.2
[0.1]: https://github.com/fauu/Kamite/releases/tag/v0.1
