// ------------------------------------------------------------------------------------------------
// KamiteSend for RPG Maker MV/MZ - sends game text to Kamite
//     a modification of "Libellule Text extractor to clipboard" plugin (Clipboard_llule.js)
// ------------------------------------------------------------------------------------------------

/*
 * Usage (might not work for some games):
 *
 *   1. Move this file to `<Game dir>/www/js/plugins/` or `<Game dir>/js/plugins/` (depending on
 *      which directory is present).
 *
 *      If neither `www/` nor `js/` exists, you might try to extract game data from `Game.exe` using
 *      Enigma Virtual Box unpacker: https://lifeinhex.com/tag/enigma-virtual-box/.
 *
 *   2. Edit `/www/js/plugins.js` or `/js/plugins.js`, adding an entry for KamiteSendRPGMaker.
 *      Example:
 *
 *        BEFORE (end of `plugins.js`):
 *
 *        {"name":"SomePluginName","status":true,"description":"Example plugin description","parameters":{}},
 *        {"name":"SomeOtherPluginName","status":true,"description":"Example plugin description","parameters":{}}
 *        ];
 *
 *        AFTER:
 *
 *        {"name":"SomePluginName","status":true,"description":"Example plugin description","parameters":{}},
 *        {"name":"SomeOtherPluginName","status":true,"description":"Example plugin description","parameters":{}},
 *        {"name":"KamiteSendRPGMaker","status":true,"description":"","parameters":{}}
 *        ];
 *
 *        !!! IMPORTANT: Note the added comma at the end of the second line (and the lack of a comma
 *                       at the end of the new line).
 *
 *    3. Start Kamite and the game. Game text should appear in Kamite.
 *
 * Linux tip: You can usually run those games natively by downloading nw.js, extracting it to the
 *            game's directory and running `./nw` directly instead of `Game.exe`.
 *            (This might require filling the `name` field in the `package.json` file in the game
 *            directory, in case it's been left empty by the developer.)
 *
 *            nw.js: https://nwjs.io/downloads/
 */

KamiteHost = "localhost"
KamitePort = 4110

TimerMil = 200;
WantCmdItemSeparator = true;
CmdItemSeparator = "。";
ShowCodeColor = false;
ForceNameSeparator = false;
TextSeparatorLeft = String.fromCharCode(12300);
TextSeparatorRight = String.fromCharCode(12301);
NameCodeColor = ["#ffffa0", "#40c0f0", "#ff80ff", "#80ff80", "#66cc40"];

IgnoreRepeatableItem = true;
BloctextSeparator = true;
IgnoreRepeatablebloc = true;
var IgnoreRegExtextbloc = [/^\d\d:\d\d($|.$|。$)/, /(^([,.\d]+)([,.]\d+)?)(\uFF27($|。$)|G($|。$)|$|。$)/, /^(\uFF27($|。$)|G($|。$))/];
ClipLogerOnStart = true;
WantChoiceSeparator = true;
ChoiceSeparator = "。\r\n";
TextWaitingTimeOFF = true;
LastItem = "";
ColorEnCour = "";
ActualThis = "";
StarTextNamefound = false;
EndTextNamefound = false;
BlocSeparatorLeft = String.fromCharCode(12300);
BlocSeparatorRight = String.fromCharCode(12301);
LastColor = "";
MemText = "";
LastMemTextSend = "";
ClipTimerOn = false;
SaveOrgDrawText = Bitmap.prototype.drawText;
var http = require('http');
var choices_encour = [];

function KamiteShowChunk(text) {
  var data = JSON.stringify({ chunk: text });
  var req = http.request({
    hostname: KamiteHost,
    port: KamitePort,
    path: "/cmd/chunk/show",
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      "Content-Length": Buffer.byteLength(data)
    }
  });
  req.write(data);
  req.end();
}

Bitmap.prototype.drawText = function(text, x, y, maxWidth, lineHeight, align) {
  OptionalText = "";
  pass = true;
  if (text) {

    if (text.length == 1 && y >= this.height) {
      pass = false;
    }

    if (IgnoreRepeatableItem && text.length > 1) {
      if (text == LastItem) {
        pass = false;
      }
      LastItem = text;
    }
    if (pass) {
      if (ShowCodeColor) {
        if (this.textColor != LastColor) {
          OptionalText = "<" + this.textColor + ">";
          LastColor = this.textColor;
        }
      }
      if (ForceNameSeparator && text.length == 1) {
        if (!ClipTimerOn && (NameCodeColor.indexOf(this.textColor) > -1)) {
          LastColor = this.textColor;
          StarTextNamefound = true;
          ColorNameEnCour = this.textColor;
        }
        if (ClipTimerOn && StarTextNamefound && (this.textColor != ColorNameEnCour)) {
          OptionalText = OptionalText + TextSeparatorLeft;
          StarTextNamefound = false;
          EndTextNamefound = true;
        }
      }
      if (BloctextSeparator) {
        if (ActualThis == "") {
          ActualThis = this;
          OptionalText = OptionalText + BlocSeparatorLeft;
        } else {
          if (ActualThis != this) {
            OptionalText = OptionalText + BlocSeparatorRight + "\r\n" + BlocSeparatorLeft;
            ActualThis = this;
          }
        }
        if ($gameMessage != null) {
          if ($gameMessage.isChoice() && !choices_encour.length) {
            choices_encour = $gameMessage._choices;
          }
        }
      }
      if (ClipTimerOn) {
        if (text.length > 1 && WantCmdItemSeparator) {
          MemText = MemText + OptionalText + text + CmdItemSeparator;
        } else {
          MemText = MemText + OptionalText + text;
        }
      } else {
        if (text.length > 1 && WantCmdItemSeparator) {
          MemText = OptionalText + text + CmdItemSeparator;
        } else {
          MemText = OptionalText + text;
        }
        ClipTimerOn = true;
        ClipTimer = setTimeout(ClipTimerSend, TimerMil);
      }
    }
  }
  SaveOrgDrawText.call(this, text, x, y, maxWidth, lineHeight, align);
};

function ClipTimerSend() {
  if (BloctextSeparator) {
    MemText = MemText + BlocSeparatorRight;
    KickOutDuplicateBloc();
  }
  if (EndTextNamefound) {
    MemText = MemText + TextSeparatorRight;
    StarTextNamefound = false;
    EndTextNamefound = false;
    if (BloctextSeparator) {
      KickOutDuplicateBloc();
    }
  }
  if (MemText != "") {
    // Kamite
    KamiteShowChunk(MemText);
    LastMemTextSend = MemText;
  }
  ClipTimerOn = false;
  ActualThis = "";
  LastColor = "";
  ColorNameEnCour = "";
};

var LibWindow_Message_prototype_clearFlags = Window_Message.prototype.clearFlags;
Window_Message.prototype.clearFlags = function() {
  LibWindow_Message_prototype_clearFlags.call(this);
  this._showFast = true;
  this._lineShowFast = true;
  this._pauseSkip = false;
};

function KickOutDuplicateBloc() {
  var Bloc = MemText.split("\r\n");
  var output = [];
  var Deleteone = "";
  if (choices_encour.length && WantChoiceSeparator) {
    var temps = choices_encour[0].replace(/\\C\[\d+\]/gi, "");
    var With = BlocSeparatorLeft + temps;
    var Deleteone = With;
    for (i = 1; i < choices_encour.length; i++) {
      temps = choices_encour[i].replace(/\\C\[\d+\]/gi, "");
      With = With + ChoiceSeparator + temps;
      Deleteone = Deleteone + temps;
    }
    With += BlocSeparatorRight;
    output.push(With);
    Deleteone += BlocSeparatorRight;
    choices_encour = [];
  }
  for (var i = 0; i < Bloc.length; i++) {
    if (output.indexOf(Bloc[i]) < 0) {
      if (RegEXspeIgnore(Bloc[i]) && Deleteone != Bloc[i]) {
        output.push(EraseDoubleSeparator(Bloc[i]));
      }
    }
  }
  MemText = output.join("\r\n");
};

function RegEXspeIgnore(Bloc) {
  BlocS = Bloc.slice(1, Bloc.length - 1);
  if (IgnoreRegExtextbloc.length != 0) {
    for (var i = 0; i < IgnoreRegExtextbloc.length; i++) {
      if (BlocS.search(IgnoreRegExtextbloc[i]) != -1) {
        return false;
      }
    }
    return true;
  } else {
    return true;
  }
};

function EraseDoubleSeparator(Bloc) {
  if ((Bloc.split(BlocSeparatorLeft).length) == 3) {
    if ((Bloc.split(BlocSeparatorRight).length) == 3) {
      return Bloc.slice(1, Bloc.length - 1);
    }
  }
  return Bloc;
};

var Save_Window_Message_prototype_updateWait = Window_Message.prototype.updateWait;
Window_Message.prototype.updateWait = function() {
  if (ClipTimerOn && TextWaitingTimeOFF) {
    this._waitCount = 0;
  }
  return Save_Window_Message_prototype_updateWait.call(this);
};
