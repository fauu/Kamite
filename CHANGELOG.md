# Changelog — Kamite

## [Unreleased]

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

[Unreleased]: https://github.com/fauu/Kamite/compare/v0.3...HEAD
[0.3]: https://github.com/fauu/Kamite/releases/tag/v0.3
[0.2]: https://github.com/fauu/Kamite/releases/tag/v0.2
[0.1]: https://github.com/fauu/Kamite/releases/tag/v0.1
