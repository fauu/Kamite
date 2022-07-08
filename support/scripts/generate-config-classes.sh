#!/usr/bin/env bash

# --- CONFIG ---------------------------------------------------------------------------------------
PKG_NAME="io.github.kamitejp.config"
PKG_BASE="src/main/java/io/github/kamitejp/config/"
CLASS_NAME="Config"
TSCFG_PATH="support/lib/tscfg/tscfg-0.9.998.jar"
SPEC_PATH="config/config.spec.hocon"

FILE_NAME="$CLASS_NAME.java"
FILE_PATH="$PKG_BASE$FILE_NAME"

# --- GENERATION -----------------------------------------------------------------------------------
java -jar "$TSCFG_PATH" \
  --spec "$SPEC_PATH" \
  --pn "$PKG_NAME" \
  --cn "$CLASS_NAME" \
  --dd "$PKG_BASE" \
  --java:records \
  --withoutTimestamp

# --- PATCHES --------------------------------------------------------------------------------------
# Make PMD ignore the generated file
sed -ri "/^public record Config/i @SuppressWarnings(\"PMD\")" "$FILE_PATH"
# Make list element type names prettier so that they can be used directly
sed -i "s/Custom\$Elm/CustomCommand/g" "$FILE_PATH"
sed -i "s/Targets\$Elm/Target/g" "$FILE_PATH"
sed -i "s/Regions\$Elm/Region/g" "$FILE_PATH"
# Make enum value parsing case-insensitive and add default values
patch_enum () {
  local name=$1
  local default_val=$2
  sed -ri "s/$name.valueOf\(c.getString\(\"(.*)\"\)\)/c.hasPath(\"\1\") ? $name.valueOf(c.getString(\"\1\").toUpperCase()) : $name.$default_val/g" \
    "$FILE_PATH"
}
patch_enum "UILayout" "STANDARD"
patch_enum "OCREngine" "NONE"
