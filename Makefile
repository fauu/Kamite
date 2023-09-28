# --- PREAMBLE
SHELL := bash
.ONESHELL:
.SHELLFLAGS := -eu -o pipefail -c
MAKEFLAGS += --warn-undefined-variables

# --- LOAD ENV
ifneq (,$(wildcard make-env))
  include make-env
endif

# --- CONFIG
LAUNCHER_TARGET_DIR=../target/launcher
BUILD_JAR_NAME=kamite-0.0.0.jar
DIST_JAR_NAME=kamite.jar

clean:
	rm -rf target/
.PHONY: clean

client:
	pnpm build
.PHONY: client

dist: dist-linux dist-win
.PHONY: dist

# Makes a platform-independent base for the release packages in `target/dist-base/`
dist-prepare-generic: jar runtime-linux textractor dist-write-version
	pushd target
	rm -rf dist-base *.zip
	mkdir -p dist-base/lib/generic
	cp -r ../lib/generic dist-base/lib
	cp java/"$(BUILD_JAR_NAME)" dist-base/lib/generic/"$(DIST_JAR_NAME)"
	mkdir dist-base/extra
	mkdir dist-base/extra/textractor
	mkdir dist-base/extra/textractor/x86
	mkdir dist-base/extra/textractor/x64
	cp "textractor/x86/Kamite Send.xdll" dist-base/extra/textractor/x86
	cp "textractor/x64/Kamite Send.xdll" dist-base/extra/textractor/x64
	cp ../README.md ../COPYING.md ../CHANGELOG.md dist-base
.PHONY: dist-prepare-generic

# Writes the version of the Kamite jar in `target/java` to `target/VERSION`
dist-write-version: runtime-linux
	pushd target
	runtime-linux/bin/java --enable-preview -jar java/"$(BUILD_JAR_NAME)" --version \
    | tr " " "\n" \
    | tail -1 \
    > VERSION
.PHONY: dist-write-version

# Makes the Linux release package on the basis of the platform-independent base
dist-linux: launcher-linux dist-prepare-generic
	pushd target
	cp -r dist-base kamite
	cp -r runtime-linux kamite/runtime
	mkdir kamite/bin
	cp launcher/release/kamite-launcher-linux kamite/bin/kamite
	cp -r ../res kamite
	cp ../scripts/install.sh kamite
	zip -r Kamite_$(shell cat target/VERSION)_Linux.zip kamite/
	rm -rf kamite
.PHONY: dist-linux

# Makes the Windows release package on the basis of the platform-independent base.
# Assumes the Windows runtime is already built in a location specified by WIN_RUNTIME (can be put in
# the make-env file)
dist-win: launcher-win dist-prepare-generic
	pushd target
	cp -r dist-base kamite
	cp -r "$(WIN_RUNTIME)" kamite
	pushd kamite/runtime/bin
	rm java.exe javaw.exe jrunscript.exe keytool.exe rmiregistry.exe kinit.exe klist.exe ktab.exe
	popd
	LAUNCHER_OUT_DIR="launcher/x86_64-pc-windows-gnu/release"
	cp "$$LAUNCHER_OUT_DIR"/kamite-launcher-win-main.exe kamite/Kamite.exe
	cp "$$LAUNCHER_OUT_DIR"/kamite-launcher-win-console-wrapper.exe kamite/Kamite.com
	cp "../launcher/win/aux/Kamite (Debug).bat" kamite
	zip -r Kamite_$(shell cat target/VERSION)_Windows.zip kamite/
	rm -rf kamite
.PHONY: dist-win

gen-config:
	support/scripts/codegen-config.sh
.PHONY: gen-config

jar: gen-config client
	mvn package
.PHONY: jar

launcher-linux:
	pushd launcher
	cargo build -p kamite-launcher-linux --target-dir "$(LAUNCHER_TARGET_DIR)" --release
.PHONY: launcher-linux

launcher-win:
	mkdir -p target/launcher/res
	convert res/icon/icon-16.png res/icon/icon-32.png res/icon/icon-48.png res/icon/icon-256.png \
    target/launcher/res/icon.ico
	pushd launcher
	x86_64-w64-mingw32-windres win/res/resources.rc --output "$(LAUNCHER_TARGET_DIR)/res/resources.o"
	cargo build -p kamite-launcher-win --target x86_64-pc-windows-gnu \
    --target-dir "$(LAUNCHER_TARGET_DIR)" --release
.PHONY: launcher-win

lint: lint-docs lint-java lint-ts
.PHONY: lint

lint-docs:
	markdownlint README.md
	markdownlint CHANGELOG.md
.PHONY: lint-docs

lint-java: lint-spotbugs lint-pmd
.PHONY: lint-java

lint-spotbugs:
	mvn compile site
	xdg-open target/java/site/spotbugs.html
.PHONY: lint-spotbugs

lint-pmd:
	export PMD_JAVA_OPTS=--enable-preview
	pmd check --use-version java-21-preview -d src/main/java -f text \
	  -R support/definitions/pmd-ruleset.xml
.PHONY: lint-pmd

lint-ts:
	pnpm typecheck
	pnpm lint
.PHONY: lint-ts

outdated:
	set +e
	pnpm outdated
	set -e
	mvn versions:display-dependency-updates
.PHONY: outdated

runtime-linux:
	rm -rf target/runtime-linux
	jlink --no-header-files --no-man-pages --compress=2 --strip-debug \
    --add-modules "java.datatransfer,java.desktop,java.logging,java.management,java.naming,java.net.http,java.rmi,java.scripting,java.sql,java.transaction.xa,java.xml,jdk.jsobject,jdk.security.auth,jdk.unsupported,jdk.unsupported.desktop,jdk.xml.dom" \
    --output target/runtime-linux
	pushd target/runtime-linux/bin
	rm jrunscript keytool rmiregistry
.PHONY: runtime-linux

textractor:
	rm -rf target/textractor
	mkdir -p target/textractor/x86
	# The x86 dll produced by the Zig toolchain (0.10.1) doesn't load on Textractor's alpha 203 build
	# specifically in Wine (works on Windows). Use mingw-w64 for now, but it'd be preferable to return
	# to zig later, because the toolchain is lighter and, more importantly, so is the produced dll
	# (significantly).
	#zig c++ -shared -target i386-windows-gnu -l ws2_32 \
  #  -o "target/textractor/x86/Kamite Send.xdll" \
  #  extra/textractor/src/KamiteSend.cpp extra/textractor/src/KamiteSendImpl.cpp
	i686-w64-mingw32-g++ --shared \
		-o target/textractor/x86/Kamite\ Send.xdll \
		extra/textractor/src/KamiteSend.cpp extra/textractor/src/KamiteSendImpl.cpp \
		-lws2_32 -static
	mkdir -p target/textractor/x64
	zig c++ -shared -target x86_64-windows-gnu -lws2_32 \
    -o "target/textractor/x64/Kamite Send.xdll" \
    extra/textractor/src/KamiteSend.cpp extra/textractor/src/KamiteSendImpl.cpp
.PHONY: textractor
