src_files = $(shell find src)
fetch = java -jar TJFetcher.jar -repo robert.nuke24.net:8080 -repo fs.marvin.nuke24.net -repo pvps1.nuke24.net

default: TJFetcher.jar.urn

.PHONY: default clean .FORCE
.DELETE_ON_ERROR:

clean:
	rm -rf bin TJFetcher.jar .src.lst

util/TJBuilder.jar: util/TJBuilder.jar.urn TJFetcher.jar 
	${fetch} -o "$@" `cat "$<"`

TJFetcher.jar: ${src_files}
	rm -rf bin TJFetcher.jar
	mkdir bin
	find src -name *.java >.src.lst
	javac -g:none -source 1.4 -target 1.4 -d bin @.src.lst
	mkdir -p bin/META-INF
	echo 'Version: 1.0' >bin/META-INF/MANIFEST.MF
	echo 'Main-Class: togos.fetcher.Fetcher' >>bin/META-INF/MANIFEST.MF
	cd bin ; zip -r -9 ../TJFetcher.jar . ; cd ..
	wc -c "$@"

TJFetcher.jar.urn: TJFetcher.jar util/TJBuilder.jar
	java -jar util/TJBuilder.jar id "$<" >"$@"
