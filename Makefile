.PHONY: clean client dist dist-only-linux dist-only-win-post-linux gen-config jar launcher-linux lint lint-docs lint-java lint-ts runtime-linux textractor

ifneq (,$(wildcard make-env))
  include make-env
endif

clean:
	echo $(TESTVAR); \
	rm -rf target/; \

client:
	yarn build; \

dist: jar runtime-linux launcher-linux launcher-win textractor dist-only-linux dist-only-win-post-linux

dist-only-linux:
	rm -rf target/dist; \
	rm -rf target/*.zip; \
	rm -rf target/kamite; \
	mkdir -p target/dist; \
	cp -r target/java-runtime target/dist/runtime; \
	mkdir -p target/dist/lib; \
	cp -r lib/generic target/dist/lib; \
	cp target/java/kamite-0.0.0.jar target/dist/lib/generic/kamite.jar; \
	mkdir target/dist/extra; \
	cp -r target/textractor target/dist/extra/textractor; \
	cp -r target/dist target/kamite; \
	mkdir target/kamite/bin; \
	cp target/launcher/release/kamite-launcher target/kamite/bin/kamite; \
	cp -r res target/kamite; \
	cp scripts/install.sh target/kamite; \
	cp README.md target/kamite; \
	cp COPYING.md target/kamite; \
	cp CHANGELOG.md target/kamite; \
	pushd target; \
	zip -r Kamite_$(shell target/java-runtime/bin/java --enable-preview -jar target/java/kamite-0.0.0.jar --version | tr " " "\n" | tail -1)_Linux.zip kamite/; \

# Assumes the Windows runtime is already built in a location specified by WIN_RUNTIME (can be put in
# the make-env file)
dist-only-win-post-linux:
	cp launcher/scripts/Kamite.ps1 target/kamite; \
	rm -rf target/kamite/runtime; \
	cp -r "$(WIN_RUNTIME)" target/kamite; \
	pushd target; \
	pushd kamite/runtime/bin; \
	rm jrunscript.exe keytool.exe rmiregistry.exe kinit.exe klist.exe ktab.exe; \
	popd; \
	cp launcher/x86_64-pc-windows-gnu/release/kamite-launcher.exe kamite/Kamite.exe; \
	rm kamite/install.sh; \
	rm -rf kamite/res; \
	rm -rf kamite/scripts; \
	rm -rf kamite/bin; \
	zip -r Kamite_$(shell target/java-runtime/bin/java --enable-preview -jar target/java/kamite-0.0.0.jar --version | tr " " "\n" | tail -1)_Windows.zip kamite/; \

gen-config:
	support/scripts/generate-config-classes.sh; \

jar: gen-config client
	mvn package; \

launcher-linux:
	pushd launcher; \
	cargo build --release; \

launcher-win:
	mkdir -p target/launcher/res; \
	convert res/icon/icon-16.png res/icon/icon-32.png res/icon/icon-48.png res/icon/icon-256.png target/launcher/res/icon.ico; \
	pushd launcher; \
	cargo build --target x86_64-pc-windows-gnu --release; \

lint: lint-docs lint-java lint-js

lint-docs:
	markdownlint README.md; \
	markdownlint CHANGELOG.md; \

lint-java:
	PMD_JAVA_OPTS=--enable-preview \
		pmd -language java -version 18-preview -d src/main/java -f text \
			-R rulesets/java/quickstart.xml; \

lint-ts:
	yarn typecheck; \
	yarn lint; \

runtime-linux:
	rm -rf target/java-runtime; \
	jlink --no-header-files --no-man-pages --compress=2 --strip-debug \
		--add-modules "java.datatransfer,java.desktop,java.logging,java.management,java.naming,java.net.http,java.rmi,java.scripting,java.sql,java.transaction.xa,java.xml,jdk.jsobject,jdk.security.auth,jdk.unsupported,jdk.unsupported.desktop,jdk.xml.dom" \
		--output target/java-runtime; \
	pushd target/java-runtime/bin; \
	rm jrunscript keytool rmiregistry \

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
