src_files = $(shell find src)
fetch = java -jar TJFetcher-fat.jar -repo @.ccouch-repos.lst

default: TJFetcher.jar.urn

.PHONY: default clean .FORCE
.DELETE_ON_ERROR:

clean:
	rm -rf bin TJFetcher.jar .src.lst

util/%.jar: util/%.jar.urn TJFetcher-fat.jar 
	${fetch} -o "$@" `cat "$<"`

TJFetcher-fat.jar: ${src_files}
	rm -rf bin "$@"
	mkdir bin
	find src -name *.java >.src.lst
	javac -source 1.4 -target 1.4 -d bin @.src.lst
	mkdir -p bin/META-INF
	echo 'Version: 1.0' >bin/META-INF/MANIFEST.MF
	echo 'Main-Class: togos.fetcher.Fetcher' >>bin/META-INF/MANIFEST.MF
	cd bin ; zip -r ../"$@" . ; cd ..
	wc -c "$@"

TJFetcher.jar: TJFetcher-fat.jar util/ProGuard.jar util/rt.jar
	java -jar util/ProGuard.jar @TJFetcher.pro

TJFetcher.jar.urn: TJFetcher.jar util/TJBuilder.jar
	java -jar util/TJBuilder.jar id "$<" >"$@"
