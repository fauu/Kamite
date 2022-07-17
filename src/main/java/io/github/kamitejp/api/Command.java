package io.github.kamitejp.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;

import io.github.kamitejp.chunk.IncomingChunkText;
import io.github.kamitejp.chunk.IncomingChunkTranslation;
import io.github.kamitejp.geometry.Dimension;
import io.github.kamitejp.geometry.Rectangle;
import io.github.kamitejp.util.JSON;
import io.github.kamitejp.util.Result;

public sealed interface Command
  permits Command.OCR,
          Command.Player,
          Command.CharacterCounter,
          Command.SessionTimer,
          Command.Chunk,
          Command.Other {

  sealed interface OCR extends Command
    permits OCR.ManualBlock,
            OCR.ManualBlockVertical,
            OCR.ManualBlockHorizontal,
            OCR.AutoBlock,
            OCR.AutoColumn,
            OCR.Region,
            OCR.Image {
    record ManualBlock() implements OCR {}
    record ManualBlockVertical() implements OCR {}
    record ManualBlockHorizontal() implements OCR {}
    record AutoBlock() implements OCR {}
    record AutoColumn() implements OCR {}
    record Region(Rectangle region, boolean autoNarrow) implements OCR {}
    record Image(String bytesB64, Dimension size) implements OCR {}

    default boolean isGlobalOCRCommand() {
      return !(this instanceof OCR.Region || this instanceof OCR.Image);
    }
  }

  sealed interface Player extends Command
    permits Player.PlayPause,
            Player.SeekBack,
            Player.SeekForward,
            Player.SeekStartSub {
    record PlayPause() implements Player {}
    record SeekBack() implements Player {}
    record SeekForward() implements Player {}
    record SeekStartSub() implements Player {}
  }

  sealed interface CharacterCounter extends Command
    permits CharacterCounter.ToggleFreeze,
            CharacterCounter.Reset {
    record ToggleFreeze() implements CharacterCounter {}
    record Reset() implements CharacterCounter {}
  }

  sealed interface SessionTimer extends Command
    permits SessionTimer.TogglePause,
            SessionTimer.Reset {
    record TogglePause() implements SessionTimer {}
    record Reset() implements SessionTimer {}
  }

  sealed interface Chunk extends Command
    permits Chunk.Show,
            Chunk.ShowTranslation {
    record Show(IncomingChunkText chunk) implements Chunk {}
    record ShowTranslation(IncomingChunkTranslation translation) implements Chunk {}
  }

  sealed interface Other extends Command
    permits Other.Custom {
    record Custom(String[] command) implements Other {}
  }

  static Result<Command, String> fromIncoming(IncomingCommand incoming) {
    CommandKind kind = null;
    String group = null;
    String name = null;
    JsonNode paramsNode;

    switch (incoming) {
      case IncomingCommand.CombinedJSON cmd -> {
        var kindParseRes = CommandKind.fromString(cmd.root().get("kind").textValue());
        if (kindParseRes.isErr()) {
          return Result.Err("parsing command kind");
        }
        kind = kindParseRes.get();
        paramsNode = cmd.root().get("params");
      }

      case IncomingCommand.Segmented cmd -> {
        switch (cmd.kind()) {
          case IncomingCommand.Kind.Joined k -> {
            var kindParseRes = CommandKind.fromString(k.kind());
            if (kindParseRes.isErr()) {
              return Result.Err("parsing command kind");
            }
            kind = kindParseRes.get();
          }
          case IncomingCommand.Kind.Segmented k -> {
            group = k.group();
            name = k.name();
          }
          default -> throw new IllegalStateException("Unhandled incoming command kind");
        }

        switch (cmd.params()) {
          case IncomingCommand.Params.RawJSON p  -> {
            try {
              paramsNode =
                p.paramsJSON() != null
                ? JSON.mapper().readTree(p.paramsJSON())
                : null;
            } catch (JsonProcessingException e) {
              return Result.Err("parsing command params: %s".formatted(e));
            }
          }
          case IncomingCommand.Params.JSONNode p ->
            paramsNode = p.paramsNode();
          default -> throw new IllegalStateException("Unhandled incoming command params type");
        }
      }

      default -> throw new IllegalStateException("Unhandled incoming command type");
    }

    if (group == null && name == null && kind != null) {
      group = kind.group();
      name = kind.name();
    }

    return of(group, name, paramsNode);
  }

  static Result<Command, String> of(String group, String name, JsonNode paramsNode) {
    Command parsedCommand = null;

    try {
      parsedCommand = switch (group) {
        case "ocr" -> switch (name) {
          case "manual-block"            -> new OCR.ManualBlock();
          case "manual-block-vertical"   -> new OCR.ManualBlockVertical();
          case "manual-block-horizontal" -> new OCR.ManualBlockHorizontal();
          case "auto-block"              -> new OCR.AutoBlock();
          case "auto-column"             -> new OCR.AutoColumn();
          case "region" -> {
            var p = JSON.mapper().treeToValue(paramsNode, CommandParams.OCR.Region.class);
            yield new OCR.Region(
              Rectangle.ofStartAndDimensions(p.x(), p.y(), p.width(), p.height()),
              p.autoNarrow()
            );
          }
          case "image" -> {
            var p = JSON.mapper().treeToValue(paramsNode, CommandParams.OCR.Image.class);
            yield new OCR.Image(p.bytesB64(), new Dimension(p.width(), p.height()));
          }
          default -> null;
        };
        case "player" -> switch (name) {
          case "playpause"      -> new Player.PlayPause();
          case "seek-back"      -> new Player.SeekBack();
          case "seek-forward"   -> new Player.SeekForward();
          case "seek-start-sub" -> new Player.SeekStartSub();
          default -> null;
        };
        case "character-counter" -> switch (name) {
          case "toggle-freeze" -> new CharacterCounter.ToggleFreeze();
          case "reset"         -> new CharacterCounter.Reset();
          default -> null;
        };
        case "session-timer" -> switch (name) {
          case "toggle-pause" -> new SessionTimer.TogglePause();
          case "reset"        -> new SessionTimer.Reset();
          default -> null;
        };
        case "chunk" -> switch (name) {
          case "show" -> {
            var p = JSON.mapper()
              .treeToValue(paramsNode, CommandParams.Chunk.Show.class);
            yield new Chunk.Show(
              new IncomingChunkText(p.chunk(), p.playbackTimeS())
            );
          }
          case "show-translation" -> {
            var p = JSON.mapper()
              .treeToValue(paramsNode, CommandParams.Chunk.ShowTranslation.class);
            yield new Chunk.ShowTranslation(
              new IncomingChunkTranslation(p.translation(), p.playbackTimeS())
            );
          }
          default -> null;
        };
        case "other" -> {
          if ("custom".equalsIgnoreCase(name)) {
            var p = JSON.mapper().treeToValue(paramsNode, CommandParams.Other.Custom.class);
            yield new Other.Custom(p.command());
          }
          yield null;
        }
        default -> null;
      };
    } catch (JsonProcessingException e) {
      return Result.Err(
        "parsing command params for input '%s': %s"
          .formatted(debugString(group, name, paramsNode), e)
      );
    }

    if (parsedCommand != null) {
      return Result.Ok(parsedCommand);
    } else {
      return Result.Err(
        "unrecognized command type: %s".formatted(debugString(group, name, paramsNode))
      );
    }
  }

  static String debugString(String group, String name, JsonNode paramsNode) {
    return "%s_%s".formatted(group, name)
      + (paramsNode != null ? " (params: %s)".formatted(paramsNode.toString()) : "");
  }
}
