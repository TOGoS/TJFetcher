src_files = $(shell find src)
fetch = java -jar TJFetcher-fat.jar -repo @.ccouch-repos.lst

.PHONY: default
default: TJFetcher.jar.urn

.PHONY: clean
.DELETE_ON_ERROR:

clean:
	rm -rf bin TJFetcher.jar .src.lst temp

util/%.jar: util/%.jar.urn | TJFetcher-fat.jar
	${fetch} -nc -o "$@" @"$<"

TJFetcher-fat.jar: ${src_files}
	rm -rf bin "$@"
	mkdir bin
	find src/main/java -name *.java >.src.lst
	javac -source 1.6 -target 1.6 -d bin @.src.lst
	mkdir -p bin/META-INF
	echo 'Version: 1.0' >bin/META-INF/MANIFEST.MF
	echo 'Main-Class: togos.fetcher.Fetcher' >>bin/META-INF/MANIFEST.MF
	cd bin ; zip -r ../"$@" . ; cd ..
	wc -c "$@"

TJFetcher.jar: TJFetcher-fat.jar util/ProGuard.jar util/rt.jar
	java -jar util/ProGuard.jar @TJFetcher.pro

TJFetcher.jar.urn: TJFetcher.jar util/TJBuilder.jar
	java -jar util/TJBuilder.jar id "$<" >"$@"

.PHONY: run-tests
run-tests: TJFetcher.jar
	mkdir -p temp
	rm -f temp/cool.txt
	java -jar TJFetcher.jar -repo @.ccouch-repos.lst urn:bitprint:4AB4UUN3FC5XFA6EURQPQH5YHTU4KBFY.UZC5SD3FMHI5LMPEONE2MVXTG54SYLT3PHB7JTA -o temp/cool.txt
	rm -f temp/cool.txt
	java -jar TJFetcher.jar -repo @.ccouch-repos.lst urn:sha1:4AB4UUN3FC5XFA6EURQPQH5YHTU4KBFY -o temp/cool.txt
	echo @.ccouch-repos.lst > .recursive-repo-list.lst
	rm -f temp/cool.txt
	java -jar TJFetcher.jar -repo @.recursive-repo-list.lst urn:sha1:4AB4UUN3FC5XFA6EURQPQH5YHTU4KBFY -o temp/cool.txt
