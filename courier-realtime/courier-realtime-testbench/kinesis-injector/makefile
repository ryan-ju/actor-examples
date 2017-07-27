build: compile
	rm -rf ./build/distributions/courier-realtime-kinesis-injector
	tar -C ./build/distributions/ -zxf ./build/distributions/courier-realtime-kinesis-injector-1.0.0-SNAPSHOT.tar
	mv ./build/distributions/courier-realtime-kinesis-injector-1.0.0-SNAPSHOT ./build/distributions/courier-realtime-kinesis-injector

compile: 
	gradle distTar

distribute: build
	@if test -z "$(bucket)"; then echo "Must set bucket parameter (used for uploading to S3)"; exit 1; fi
	gradle -DcustomBucket="$(bucket)" uploadS3

clean: 
	gradle clean
