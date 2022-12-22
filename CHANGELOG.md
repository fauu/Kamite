<!-- vim: set textwidth=80 colorcolumn=80: -->
<!-- markdownlint-configure-file
{
  "no-duplicate-heading": { "siblings_only": true }
}
-->
# Changelog — Kamite

## [Unreleased]

### Added

* Conceal furigana: An option to hide furigana annotations behind rectangles,
  reveal on mouse hover (`chunk.furigana.conceal`).
* Focus mode: An option to hide client UI elements other than the current chunk
  (toolbar, status panel, notebook) until mouse hover (`ui.focusMode`).
* Client keyboard shortcuts: <kbd>Ctrl</kbd> + <kbd>Enter</kbd> to enter chunk
  edit mode (+ <kbd>Shift</kbd> to enter edit mode immediately clearing the text
  in the input field).
* `--chunk-line-height-scale` CSS variable to make current chunk text line
  height customization easier in `custom.css` (see
  [„Current chunk” line height][wiki-current-chunk-line-height] in the Wiki).

[wiki-current-chunk-line-height]: https://github.com/fauu/Kamite/wiki/Styling-recipes#current-chunk-line-height

### Changed

* Remade the DeepL mod userscript. Instead of trying to remold the existing
  DeepL UI, the script now hides it and recreates a very minimal one in its
  place.
* Updated the jpdb mod userscript to add a top margin to compensate for a
  removed padding in Kamite’s UI.
* **BREAKING** Renamed the `chunk.showFurigana` option to
  `chunk.furigana.enable`.
* **BREAKING** Values of non-config launch options (`--debug`) interpreted as
  `no` changed from `[0, false]` to `[no, off, false]`
  to make the interpretation consistent with that of config values.
* Redesigned chunk text selection visuals; tweaked chunk highlight (Yomichan
  selection) and flash visuals. (New theme color introduced:
  `--color-accB2-hl2`.)
* Tweaked client settings UI.
* Bumped chunk history size from `50` to `100`.
* Slightly improved the client’s performance by limiting the number of status
  panel’s fade state recalculations.
* The UI layout setting is now an on-off toggle instead of a dropdown selection,
  since it will only have two options for the forseeable future.
* Removed the inner page padding of the notebook UI element for frames embedding
  external websites.
* Disabled the default right-click browser context menu in the client.

### Fixed

* The 32-bit (x86) Textractor extension no longer crashes on recent alpha builds
  of Textractor in Wine.
* Slightly decreased scaling of furigana in current chunk view to reduce
  inconsistencies in base text character spacing.
* Chunk text selection is now again cleared when switching to another chunk from
  chunk history.
* Current chunk character hover underline is now drawn at a roughly consistent
  distance from the characters in all supported browsers.
* Made text line height of current chunk more consistent across different
  modes (furigana on and off) and different browsers.
* Made current chunk text top padding consistent between normal mode and
  edit mode.
* Status panel fade state is now properly updated on browser window resize.
* Improved notebook concealing behaviour in the case of mouse cursor leaving
  the browser window.
* Action palette button click no longer registers when the mouse button is
  pressed outside the button and released inside it.
* The backend no longer gets stuck in a state where a client cannot properly
  connect to it in some cases after the previous connection has been terminated
  abruptly due to a browser crash.
* A typo in an OCR error message.

## [0.10] – 2022-11-15

### Changed

* User selection within current chunk outside Edit mode is no longer removed
  when the cursor leaves the client browser tab.
* If there is a user selection within current chunk outside edit mode when a new
  chunk comes in, the current chunk is now replaced entirely, not just the
  selected part. (To have just a part of it replaced instead, enter Edit mode by
  double-clicking the current chunk area and select the text to be replaced
  there.)
* Startup time with OCR enabled has been improved in some cases by initializing
  Recognizer in a separate thread.
* Remote OCR request that has failed to complete is now retried twice in some
  cases before a failure is reported to the client.
* The current chunk is now faded out instead of hidden while OCR is in progress.
* Characters from unicode block [Specials][unicode-specials] are now stripped
  from incoming chunks if `chunk.correct = true` (it is so by default).
* Default furigana font size has been decreased and further downscaling is now
  applied to it depending on the length of the annotation relatively to the base
  text—this in order to prevent the furigana overflowing and introducing
  inconsistencies in spacing between characters. (For how to restore previous
  behaviour, see the
  [„Current chunk” font sizing][wiki-current-chunk-font-sizing] section in the
  Wiki.)
* Furigana font can now be scaled in `custom.css` using the variable
  `--chunk-furigana-font-scale`. It is now also scaled with `--chunk-font-size`.
  (For details, see the
  [„Current chunk” font sizing][wiki-current-chunk-font-sizing] section
  in the Wiki.)
* Removed the CSS variable `--chunk-line-height` for now, since due to browser
  quirks and furigana interactions it was not fully functional for regulating
  the line height in `custom.css`.
* Added CSS variables `--font-ui` and `--font-jp` for easier customization of
  font faces. (See the [Font faces][wiki-font-faces] section in the Wiki)
* Added CSS variables `--chunk-font-weight` and `--chunk-furigana-font-weight`
  for easier customization of chunk font weight. (See the
  [„Current chunk” font weight][wiki-current-chunk-font-weight]
  section in the Wiki.)
* Make user selection in current chunk visually stand out a bit more.

[unicode-specials]: https://en.wikipedia.org/wiki/Specials_(Unicode_block)
[wiki-current-chunk-font-sizing]: https://github.com/fauu/Kamite/wiki/Styling-recipes#current-chunk-font-sizing
[wiki-font-faces]: https://github.com/fauu/Kamite/wiki/Styling-recipes#font-faces
[wiki-current-chunk-font-weight]: https://github.com/fauu/Kamite/wiki/Styling-recipes#current-chunk-font-weight

### Fixed

* “Manga OCR” Online OCR engine works again (switched to
  [a Hugging Face Space by Detomo][manga-ocr-hf-detomo]).
* Current chunk label now has correct line height on Chrome.
* Status panel fade is now correctly recalculated in a certain situation where
  it previously was not.
* Empty incoming chunks are now rejected in some circumstances when they might
  not have been previously.
* Likely reduced the incidence of Tesseract executable availability check
  failing when it should not.
* *Select highlighted* action no longer appears when it would be a no-op because
  of the chunk selection already being equal to the highlight.

[manga-ocr-hf-detomo]: https://huggingface.co/spaces/Detomo/Japanese-OCR

## [0.9] – 2022-10-31

### Fixed

* “Manga OCR” Online OCR engine works again.

## [0.8] – 2022-10-21

### Added

* Option to log to a text file chunks that appear in the client in each session
  (`chunk.log.dir`).
* Rejecting chunks based on user-provided regular expressions
  (`chunk.filter.rejectPatterns`). **BREAKING CHANGE: A pattern used for
  filtering Textractor messages has been moved from within the program to the
  default config. If you already have a config, to have the filter back on, you
  need to set `chunk.filter.rejectPatterns` in your config to `["^Textractor"]`.**
* Transforming text of incoming chunks according to user-provided replacement
  rules before displaying them (`chunk.transforms`).
* Partial config reload: Config is reloaded on config file modification and
  *selected* changes are applied immediately, while Kamite is running.
* Option to automatically collapse the notebook UI component, so that just its
  tab bar is visible unless the notebook is being interacted with in some way
  (`ui.notebook.collapse`).
* Custom CSS rules can now be applied per profile, using CSS classes named
  `profile-PROFILENAME`.

### Changed

* Rewrote the *current chunk label* UI component with improved performance.
* Shortened the long-click duration needed to reset the character counter and
  the session timer.
* Client UI icons can now be recolored with custom CSS.
* Warnings are now displayed when unknown keys are present in user config.
* Text transform action has gained an additional visually similar character.
* Max. notebook height has been increased to 90%.
* Updated the runtime from Java 18 to Java 19.

### Fixed

* No longer occasionally quitely hangs during initialization when Control GUI is
  enabled.

## [0.7] – 2022-09-12

### Fixed

* (Windows) No longer crashes at startup when JRE dlls do not happen to be on
  `PATH`.

## [0.6] – 2022-09-10

### Added

* Combined profile: Multiple extra config files can now be loaded
  (`--profile=first-profile,second-profile,third-profile`).
* An alternative OCR.space engine “3” (`ocr.ocrspace.engine = 3`).
* Global keybindings for user-defined OCR regions
  (`keybindings.global.ocr.region`).
* Long-clicking the “Redo” button in the action palette will now skip
  forward all the way to the latest chunk.
* New `misc_lookup` command in the command API for externally invoking lookups
  within Kamite’s client.
* (Windows) `Kamite (Debug).bat` script for convenient launching in debug mode
  with a console window.

### Changed

* Custom commands are now specified in the form of a list. **BREAKING CHANGE:
  Custom command definitions in the config file must be changed accordingly,
  e.g., `command = "the-command first-argument second-argument …"` → `command =
  ["the-command", "first-argument", "second-argument", …]`.**
* (API) When sending commands through HTTP, the params JSON object is now
  expected directly in the request body instead of in a form param. **BREAKING
  CHANGE (API): command HTTP request body must be changed into the form
  `{"chunk": "text"}` instead of `params={"chunk": "text"}`.**
  * **The Textractor extension must be updated to the current version.**
  * **Gomics-v must be updated to version 0.2.**
* Improved the accuracy of auto-generated furigana by switching the dictionary
  from IPADIC to UniDic.
* The library used for furigana generation is no longer included in the release
  package. **BREAKING CHANGE: The “Show furigana” option now requires an extra
  download (see
  [README](https://github.com/fauu/Kamite#auto-generated-furigana)).**
* (Windows) Replaced the `Kamite.ps1` console launcher script with `Kamite.com`.
* (Windows) The launcher now loads the Java Virtual Machine as a library instead
  of launching it in the form of an external executable.
* When chunk correction is enabled (default), some garbage characters, as well
  as leading and trailing whitespace, are now removed from incoming chunks.
* Character counter now only counts characters in Unicode categories Letter,
  Number, and Symbol.
* Overflowing action palette scrolling UX no longer involves a scrollbar.
* OCR input images are now displayed in the Debug tab (available when launched
  with `--debug`).

### Fixed

* Custom commands with placeholder parameters no longer break after first use.
* No longer crashes when some duplicate launch options are provided.
* Certain special characters in params of commands sent through HTTP, such as
  `&` or `$`, no longer cause command processing to fail.
* (Windows) “Manga OCR” no longer fails to start when executing Kamite through
  the launcher.
* (Windows) Can now be pinned to the taskbar properly.
* Some weird behaviours have been eliminated around client
  connecting/disconnecting in the presence of competing client tabs.
* (Windows) An overflowing action palette no longer pushes the chunk view down
  in Firefox.
* A missing indicator icon to distinguish buttons for lookups that open in a
  new tab has been restored.
* When switching to a different chunk from chunk history after changing the
  “Show furigana” setting, that setting is now applied to the current chunk
  switched into.
* Chunk text selection now behaves correctly in Flipped interface layout in
  cases when the cursor is dragged above the chunk while selecting.
* Overflowing current chunk area can now be scrolled when in Flipped layout.
* Clicking a command button no longer removes selection from the current chunk
  (this made impossible passing selected text to a custom command invoked by a
  click).
* Vertical label alignment on some UI controls.

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

[Unreleased]: https://github.com/fauu/Kamite/compare/v0.10...HEAD
[0.10]: https://github.com/fauu/Kamite/releases/tag/v0.10
[0.9]: https://github.com/fauu/Kamite/releases/tag/v0.9
[0.8]: https://github.com/fauu/Kamite/releases/tag/v0.8
[0.7]: https://github.com/fauu/Kamite/releases/tag/v0.7
[0.6]: https://github.com/fauu/Kamite/releases/tag/v0.6
[0.5]: https://github.com/fauu/Kamite/releases/tag/v0.5
[0.4]: https://github.com/fauu/Kamite/releases/tag/v0.4
[0.3]: https://github.com/fauu/Kamite/releases/tag/v0.3
[0.2]: https://github.com/fauu/Kamite/releases/tag/v0.2
[0.1]: https://github.com/fauu/Kamite/releases/tag/v0.1
