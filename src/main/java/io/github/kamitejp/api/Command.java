package io.github.kamitejp.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;

import io.github.kamitejp.chunk.ChunkTranslationDestination;
import io.github.kamitejp.chunk.IncomingChunkText;
import io.github.kamitejp.chunk.IncomingChunkTranslation;
import io.github.kamitejp.geometry.Dimension;
import io.github.kamitejp.geometry.Rectangle;
import io.github.kamitejp.recognition.PointSelectionMode;
import io.github.kamitejp.util.JSON;
import io.github.kamitejp.util.Result;

public sealed interface Command
    permits Command.OCR,
    Command.OCRSetup,
    Command.Player,
    Command.CharacterCounter,
    Command.SessionTimer,
    Command.Chunk,
    Command.Misc {

  sealed interface OCR extends Command
      permits OCR.ManualBlock,
      OCR.ManualBlockRotated,
      OCR.AutoBlock,
      OCR.AutoColumn,
      OCR.Region,
      OCR.Image {
    String ocrConfigurationName();

    record ManualBlock(String ocrConfigurationName) implements OCR {
    }

    record ManualBlockRotated(String ocrConfigurationName) implements OCR {
    }

    record AutoBlock(String ocrConfigurationName, PointSelectionMode mode) implements OCR {
    }

    record AutoColumn(String ocrConfigurationName, PointSelectionMode mode) implements OCR {
    }

    record Region(
        String ocrConfigurationName,
        Rectangle region,
        boolean autoNarrow) implements OCR {
      @Override
      public boolean isGlobalOCRCommand() {
        return false;
      }
    }

    record Image(String ocrConfigurationName, String bytesB64, Dimension size) implements OCR {
      @Override
      public boolean isGlobalOCRCommand() {
        return false;
      }
    }

    default boolean isGlobalOCRCommand() {
      return true;
    }
  }

  sealed interface OCRSetup extends Command
      permits OCRSetup.SetActiveOCRConfiguration {
    record SetActiveOCRConfiguration(String ocrConfigurationName) implements OCRSetup {
    };
  }

  sealed interface Player extends Command
      permits Player.PlayPause,
      Player.SeekBack,
      Player.SeekForward,
      Player.SeekStartSub {
    record PlayPause() implements Player {
    }

    record SeekBack() implements Player {
    }

    record SeekForward() implements Player {
    }

    record SeekStartSub() implements Player {
    }
  }

  sealed interface CharacterCounter extends Command
      permits CharacterCounter.ToggleFreeze,
      CharacterCounter.Reset {
    record ToggleFreeze() implements CharacterCounter {
    }

    record Reset() implements CharacterCounter {
    }
  }

  sealed interface SessionTimer extends Command
      permits SessionTimer.Start,
      SessionTimer.Stop,
      SessionTimer.Toggle,
      SessionTimer.Reset {
    record Start() implements SessionTimer {
    }

    record Stop() implements SessionTimer {
    }

    record Toggle() implements SessionTimer {
    }

    record Reset() implements SessionTimer {
    }
  }

  sealed interface Chunk extends Command
      permits Chunk.Show,
      Chunk.ShowTranslation {
    record Show(IncomingChunkText chunk) implements Chunk {
    }

    record ShowTranslation(IncomingChunkTranslation translation) implements Chunk {
    }
  }

  sealed interface Misc extends Command
      permits Misc.Custom,
      Misc.Lookup {
    record Custom(String[] command) implements Misc {
    }

    record Lookup(String targetSymbol, String customText) implements Misc {
    }
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
          case IncomingCommand.Params.RawJSON p -> {
            try {
              paramsNode = p.paramsJSON() != null
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
    var paramsMissing = false;

    try {
      parsedCommand = switch (group) {
        case "ocr" -> {
          var ocrConfigurationName = paramsNode != null
            ? paramsNode.get("configuration").asText(null)
            : null;

          yield switch (name) {
            case "manual-block" -> new OCR.ManualBlock(ocrConfigurationName);
            case "manual-block-rotated" -> new OCR.ManualBlockRotated(ocrConfigurationName);

            case "auto-block" -> {
              // QUAL: We could maybe remove `ocrConfigurationName` from CommandParams but maybe the
              //       auto conversion breaks without it
              var p = JSON.mapper().treeToValue(paramsNode, CommandParams.OCR.AutoBlock.class);
              yield new OCR.AutoBlock(
                  ocrConfigurationName,
                  p == null || p.mode() == null
                      ? PointSelectionMode.INSTANT
                      : p.mode());
            }

            case "auto-column" -> {
              var p = JSON.mapper().treeToValue(paramsNode, CommandParams.OCR.AutoColumn.class);
              yield new OCR.AutoColumn(
                  ocrConfigurationName,
                  p == null || p.mode() == null
                      ? PointSelectionMode.INSTANT
                      : p.mode());
            }

            case "region" -> {
              var p = JSON.mapper().treeToValue(paramsNode, CommandParams.OCR.Region.class);
              if (p == null) {
                paramsMissing = true;
                yield null;
              }
              yield new OCR.Region(
                  ocrConfigurationName,
                  Rectangle.ofStartAndDimensions(p.x(), p.y(), p.width(), p.height()),
                  p.autoNarrow());
            }

            case "image" -> {
              var p = JSON.mapper().treeToValue(paramsNode, CommandParams.OCR.Image.class);
              if (p == null) {
                paramsMissing = true;
                yield null;
              }
              yield new OCR.Image(
                  ocrConfigurationName,
                  p.bytesB64(),
                  new Dimension(p.width(), p.height()));
            }

            default -> null;
          };
        }

        case "ocr-setup" -> switch (name) {
          case "set-active-configuration" -> {
            var p = JSON.mapper().treeToValue(
              paramsNode,
              CommandParams.OCRSetup.SetActiveOCRConfiguration.class
            );
            if (p == null) {
              paramsMissing = true;
              yield null;
            }
            yield new OCRSetup.SetActiveOCRConfiguration(p.name());
          }
          default -> null;
        };

        case "player" -> switch (name) {
          case "playpause" -> new Player.PlayPause();
          case "seek-back" -> new Player.SeekBack();
          case "seek-forward" -> new Player.SeekForward();
          case "seek-start-sub" -> new Player.SeekStartSub();
          default -> null;
        };

        case "character-counter" -> switch (name) {
          case "toggle-freeze" -> new CharacterCounter.ToggleFreeze();
          case "reset" -> new CharacterCounter.Reset();
          default -> null;
        };

        case "session-timer" -> switch (name) {
          case "start" -> new SessionTimer.Start();
          case "stop" -> new SessionTimer.Stop();
          case "toggle" -> new SessionTimer.Toggle();
          case "reset" -> new SessionTimer.Reset();
          default -> null;
        };

        case "chunk" -> switch (name) {
          case "show" -> {
            var p = JSON.mapper().treeToValue(paramsNode, CommandParams.Chunk.Show.class);
            if (p == null) {
              paramsMissing = true;
              yield null;
            }
            yield new Chunk.Show(new IncomingChunkText(p.chunk(), p.playbackTimeS()));
          }

          case "show-translation" -> {
            var p = JSON.mapper()
                .treeToValue(paramsNode, CommandParams.Chunk.ShowTranslation.class);
            if (p == null) {
              paramsMissing = true;
              yield null;
            }
            var destination = p.destination();
            if (destination == null) {
              destination = ChunkTranslationDestination.LATEST;
            }
            yield new Chunk.ShowTranslation(
                new IncomingChunkTranslation(p.translation(), destination, p.playbackTimeS()));
          }

          default -> null;
        };

        case "misc" -> switch (name) {
          case "custom" -> {
            var p = JSON.mapper().treeToValue(paramsNode, CommandParams.Misc.Custom.class);
            if (p == null) {
              paramsMissing = true;
              yield null;
            }
            yield new Misc.Custom(p.command());
          }

          case "lookup" -> {
            var p = JSON.mapper().treeToValue(paramsNode, CommandParams.Misc.Lookup.class);
            if (p == null || p.targetSymbol() == null) {
              paramsMissing = true;
              yield null;
            }
            yield new Misc.Lookup(
                p.targetSymbol(),
                p.customText() == null ? null : p.customText());
          }

          default -> null;
        };

        default -> null;
      };
    } catch (JsonProcessingException e) {
      return Result.Err(
          "parsing command parameters of `%s`: %s"
              .formatted(debugString(group, name, paramsNode), e));
    }

    if (parsedCommand != null) {
      return Result.Ok(parsedCommand);
    } else {
      var cmdStr = debugString(group, name, paramsNode);
      var errMsgTpl = paramsMissing
          ? "missing parameters for command: %s"
          : "unrecognized command: %s";
      return Result.Err(errMsgTpl.formatted(cmdStr));
    }
  }

  static String debugString(String group, String name, JsonNode paramsNode) {
    return "%s_%s".formatted(group, name)
        + (paramsNode != null ? " (params: %s)".formatted(paramsNode.toString()) : "");
  }
}
