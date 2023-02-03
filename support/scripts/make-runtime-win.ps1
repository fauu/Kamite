jlink --no-header-files --no-man-pages --compress=2 --strip-debug `
	    --add-modules "java.datatransfer,java.desktop,java.logging,java.management,java.naming,java.net.http,java.rmi,java.scripting,java.sql,java.transaction.xa,java.xml,jdk.jsobject,jdk.security.auth,jdk.unsupported,jdk.unsupported.desktop,jdk.xml.dom,jdk.accessibility" `
	    --output runtime
