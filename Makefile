.PHONY: clean client dist dist-prepare-generic dist-linux dist-win dist-write-version gen-config jar launcher-linux lint lint-docs lint-java lint-ts runtime-linux textractor

LAUNCHER_TARGET_DIR=../target/launcher

ifneq (,$(wildcard make-env))
  include make-env
endif

clean:
	rm -rf target/; \

client:
	yarn build; \

dist: dist-linux dist-win

# Makes a platform-independent base for the release packages in `target/dist-base/`
dist-prepare-generic: jar runtime-linux textractor dist-write-version
	pushd target; \
	rm -rf dist-base *.zip; \
	mkdir -p dist-base/lib/generic; \
	cp -r ../lib/generic dist-base/lib; \
	cp java/kamite-0.0.0.jar dist-base/lib/generic/kamite.jar; \
	mkdir dist-base/extra; \
	cp -r textractor dist-base/extra/textractor; \
	cp ../README.md ../COPYING.md ../CHANGELOG.md dist-base; \

# Writes the version of the Kamite jar in `target/java` to `target/VERSION`
dist-write-version: runtime-linux
	pushd target; \
	runtime-linux/bin/java --enable-preview -jar java/kamite-0.0.0.jar --version | tr " " "\n" | tail -1 > VERSION; \

# Makes the Linux release package on the basis of the platform-independent base
dist-linux: launcher-linux dist-prepare-generic
	pushd target; \
	cp -r dist-base kamite; \
	cp -r runtime-linux kamite/runtime; \
	mkdir kamite/bin; \
	cp launcher/release/kamite-launcher-linux kamite/bin/kamite; \
	cp -r ../res kamite; \
	cp ../scripts/install.sh kamite; \
	zip -r Kamite_$(shell cat target/VERSION)_Linux.zip kamite/; \
	rm -rf kamite; \

# Makes the Windows release package on the basis of the platform-independent base.
# Assumes the Windows runtime is already built in a location specified by WIN_RUNTIME (can be put in
# the make-env file)
dist-win: launcher-win dist-prepare-generic
	pushd target; \
	cp -r dist-base kamite; \
	cp -r "$(WIN_RUNTIME)" kamite; \
	pushd kamite/runtime/bin; \
	rm java.exe javaw.exe jrunscript.exe keytool.exe rmiregistry.exe kinit.exe klist.exe ktab.exe; \
	popd; \
	cp launcher/x86_64-pc-windows-gnu/release/kamite-launcher-win-main.exe kamite/Kamite.exe; \
	cp launcher/x86_64-pc-windows-gnu/release/kamite-launcher-win-console-wrapper.exe kamite/Kamite.com; \
	cp "../launcher/win/aux/Kamite (Debug).bat" kamite; \
	zip -r Kamite_$(shell cat target/VERSION)_Windows.zip kamite/; \
	rm -rf kamite; \

gen-config:
	support/scripts/codegen-config.sh; \

jar: gen-config client
	mvn package; \

launcher-linux:
	pushd launcher; \
	cargo build -p kamite-launcher-linux --target-dir "$(LAUNCHER_TARGET_DIR)" --release; \

launcher-win:
	mkdir -p target/launcher/res; \
	convert res/icon/icon-16.png res/icon/icon-32.png res/icon/icon-48.png res/icon/icon-256.png target/launcher/res/icon.ico; \
	pushd launcher; \
	x86_64-w64-mingw32-windres win/res/resources.rc --output "$(LAUNCHER_TARGET_DIR)/res/resources.o"; \
	cargo build -p kamite-launcher-win --target x86_64-pc-windows-gnu --target-dir "$(LAUNCHER_TARGET_DIR)" --release; \

lint: lint-docs lint-java lint-js

lint-docs:
	markdownlint README.md; \
	markdownlint CHANGELOG.md; \

lint-java:
	PMD_JAVA_OPTS=--enable-preview \
		pmd -language java -version 19-preview -d src/main/java -f text \
			-R support/definitions/pmd-ruleset.xml; \

lint-ts:
	yarn typecheck; \
	yarn lint; \

runtime-linux:
	rm -rf target/runtime-linux; \
	jlink --no-header-files --no-man-pages --compress=2 --strip-debug \
		--add-modules "java.datatransfer,java.desktop,java.logging,java.management,java.naming,java.net.http,java.rmi,java.scripting,java.sql,java.transaction.xa,java.xml,jdk.jsobject,jdk.security.auth,jdk.unsupported,jdk.unsupported.desktop,jdk.xml.dom" \
		--output target/runtime-linux; \
	pushd target/runtime-linux/bin; \
	rm jrunscript keytool rmiregistry; \

textractor:
	rm -rf target/textractor; \
	mkdir -p target/textractor/x86; \
	mkdir -p target/textractor/x64; \
	zig c++ -shared -target i386-windows-gnu -l ws2_32 \
		-o "target/textractor/x86/Kamite Send.xdll" \
		extra/textractor/src/KamiteSend.cpp extra/textractor/src/KamiteSendImpl.cpp; \
	zig c++ -shared -target x86_64-windows-gnu -l ws2_32 \
		-o "target/textractor/x64/Kamite Send.xdll" \
		extra/textractor/src/KamiteSend.cpp extra/textractor/src/KamiteSendImpl.cpp; \
