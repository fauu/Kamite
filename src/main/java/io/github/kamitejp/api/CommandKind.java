package io.github.kamitejp.api;

import io.github.kamitejp.util.Result;

record CommandKind(String group, String name) {
  static Result<CommandKind, String> fromString(String kind) {
    var segs = kind.split("_");
    if (segs.length != 2) {
      return Result.Err("Two segments expected");
    }
    return Result.Ok(new CommandKind(segs[0], segs[1]));
  }
}
