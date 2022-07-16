.PHONY: clean dist gen-config jar lint lint-docs lint-java lint-ts runtime textractor

clean:
	rm -rf target/; \

dist: gen-config jar runtime textractor
	rm -rf target/dist; \
	rm -rf target/*.zip; \
	mkdir -p target/dist; \
	cp -r target/java-runtime target/dist/runtime; \
	mkdir -p target/dist/lib; \
	cp -r lib/generic target/dist/lib; \
	cp target/java/kamite-0.0.0.jar target/dist/lib/generic/kamite.jar; \
	mkdir target/dist/extra; \
	cp -r extra/mpv target/dist/extra; \
	cp -r target/textractor target/dist/extra/textractor; \
	cp -r target/dist target/kamite; \
	cp -r bin target/kamite; \
	cp -r res target/kamite; \
	cp scripts/install.sh target/kamite; \
	cp README.md target/kamite; \
	cp COPYING.md target/kamite; \
	cp CHANGELOG.md target/kamite; \
	pushd target; \
	zip -r Kamite_$(shell target/java-runtime/bin/java --enable-preview -jar target/java/kamite-0.0.0.jar --version | tr " " "\n" | tail -1)_Linux.zip kamite/; \
	rm -rf kamite; \

gen-config:
	support/scripts/generate-config-classes.sh; \

jar:
	mvn package; \

lint: lint-docs lint-java lint-js

lint-docs:
	markdownlint README.md; \
	markdownlint CHANGELOG.md; \

lint-java:
	PMD_JAVA_OPTS=--enable-preview \
		pmd -language java -version 17-preview -d src/main/java -f text \
			-R rulesets/java/quickstart.xml; \

lint-ts:
	yarn typecheck; \
	yarn lint; \

runtime:
	rm -rf target/java-runtime; \
	jlink --no-header-files --no-man-pages --compress=2 --strip-debug \
		--add-modules "java.datatransfer,java.desktop,java.logging,java.management,java.naming,java.net.http,java.rmi,java.scripting,java.sql,java.transaction.xa,java.xml,jdk.jsobject,jdk.security.auth,jdk.unsupported,jdk.unsupported.desktop,jdk.xml.dom" \
		--output target/java-runtime; \

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
