build: compile
	rm -rf ./build/distributions/courier-realtime-service
	tar -C ./build/distributions/ -zxf ./build/distributions/courier-realtime-service-1.0.0-SNAPSHOT.tar
	mv ./build/distributions/courier-realtime-service-1.0.0-SNAPSHOT ./build/distributions/courier-realtime-service

compile: 
	gradle distTar

clean: 
	gradle clean
