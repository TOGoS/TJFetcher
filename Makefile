src_files = $(shell find src)

default: TJFetcher.jar

.PHONY: default clean .FORCE
.DELETE_ON_ERROR:

clean:
	rm -rf bin TJFetcher.jar .src.lst

TJFetcher.jar: ${src_files}
	rm -rf bin TJFetcher.jar
	mkdir bin
	find src -name *.java >.src.lst
	javac -source 1.4 -target 1.4 -d bin @.src.lst
	mkdir -p bin/META-INF
	echo 'Version: 1.0' >bin/META-INF/MANIFEST.MF
	echo 'Main-Class: togos.fetcher.Fetcher' >>bin/META-INF/MANIFEST.MF
	cd bin ; zip -r ../TJFetcher.jar . ; cd ..
	wc -c "$@"
