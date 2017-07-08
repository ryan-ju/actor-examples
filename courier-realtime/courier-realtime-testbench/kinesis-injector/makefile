build: compile
	rm -rf ./build/distributions/courier-realtime-kinesis-injector
	tar -C ./build/distributions/ -zxf ./build/distributions/courier-realtime-kinesis-injector-1.0.0-SNAPSHOT.tar
	mv ./build/distributions/courier-realtime-kinesis-injector-1.0.0-SNAPSHOT ./build/distributions/courier-realtime-kinesis-injector

compile: 
	gradle distTar

clean: 
	gradle clean
