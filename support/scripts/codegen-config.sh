#!/usr/bin/env bash

# --- CONFIG ---------------------------------------------------------------------------------------
PKG_NAME="io.github.kamitejp.config"
PKG_BASE="src/main/java/io/github/kamitejp/config/"
CLASS_NAME="Config"
TSCFG_PATH="support/lib/tscfg/tscfg-1.2.1.jar"
SPEC_PATH="config/config.spec.hocon"
CONFIG_FILE_NAME="$CLASS_NAME.java"
CONFIG_FILE_PATH="$PKG_BASE$CONFIG_FILE_NAME"
KNOWN_CONFIG_SCRIPT_PATH="support/scripts/ListKnownConfigKeys.java"
KNOWN_CONFIG_KEYS_FILE_PATH="src/main/resources/known_config_keys.txt"

# --- GENERATION -----------------------------------------------------------------------------------
java -jar "$TSCFG_PATH" \
  --spec "$SPEC_PATH" \
  --pn "$PKG_NAME" \
  --cn "$CLASS_NAME" \
  --dd "$PKG_BASE" \
  --java:records \
  --withoutTimestamp

# --- PATCHES --------------------------------------------------------------------------------------
# Make list element type names prettier so that they can be used directly
while read -r name; do
  read -r new_name
  sed -i "s/$name\([^a-z]\)/$new_name\1/g" "$CONFIG_FILE_PATH"
done << '___HERE'
Transforms\$Elm
Transform
Custom\$Elm
CustomCommand
Targets\$Elm
Target
Region\$Elm
RegionBinding
Ocr2
OCR
Ocr
GlobalKeybindingsOCR
Regions\$Elm
Region
Handlers\$Elm
Handler
___HERE
# Make PMD ignore the generated file
sed -ri "/^public record Config/i @SuppressWarnings(\"PMD\")" "$CONFIG_FILE_PATH"
# Make enum value parsing case-insensitive and add default values
patch_enum () {
  local name=$1
  local default_val=$2
  sed -ri "s/$name.valueOf\(c.getString\(\"(.*)\"\)\)/c.hasPath(\"\1\") ? $name.valueOf(c.getString(\"\1\").toUpperCase()) : $name.$default_val/g" \
    "$CONFIG_FILE_PATH"
}
patch_enum "UILayout" "STANDARD"
patch_enum "OCREngine" "NONE"

# --- KNOWN KEY LIST -------------------------------------------------------------------------------
java -cp "$TSCFG_PATH" "$KNOWN_CONFIG_SCRIPT_PATH" "$SPEC_PATH" "$KNOWN_CONFIG_KEYS_FILE_PATH"
