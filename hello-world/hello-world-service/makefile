build: compile
	rm -rf ./build/distributions/hello-world-service
	tar -C ./build/distributions/ -zxf ./build/distributions/hello-world-service-1.0.0-SNAPSHOT.tar
	mv ./build/distributions/hello-world-service-1.0.0-SNAPSHOT ./build/distributions/hello-world-service

compile: 
	gradle distTar

clean: 
	gradle clean
