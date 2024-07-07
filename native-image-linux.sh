PROJECT_NAME=lambda-native
PROJECT_VERSION=2.1.34-SNAPSHOT

# mvn clean might fail due to permission issue on Linux, so remove target with root.
sudo rm -rf target;
# Generate Jar file
mvn clean install;
echo "Jar file generated successfully";
echo $(pwd);

# Generate Native Image
docker run --rm --name graal -v $(pwd):/${PROJECT_NAME} springci/graalvm-ce:master-java11 \
    /bin/bash -c "echo 'Building native image'; \
                    native-image \
                    -H:EnableURLProtocols=http \
                    --no-fallback \
                    --verbose \
                    --trace-class-initialization=ch.qos.logback.classic.Logger \
                    --trace-object-instantiation=ch.qos.logback.core.AsyncAppenderBase$Worker \
                    --initialize-at-build-time=org.slf4j.LoggerFactory,org.slf4j.MDC,ch.qos.logback \
                    --initialize-at-run-time=io.netty \
                    --allow-incomplete-classpath \
                    --enable-all-security-services \
		                -H:ReflectionConfigurationFiles=/${PROJECT_NAME}/reflect.json \
                    -H:ResourceConfigurationFiles=/${PROJECT_NAME}/resource-config.json \
                    -H:+ReportExceptionStackTraces \
                    -jar /${PROJECT_NAME}/target/${PROJECT_NAME}-${PROJECT_VERSION}.jar \
                    ; \
                    mkdir /${PROJECT_NAME}/target/custom-runtime \
                    ; \
                    cp ${PROJECT_NAME}-${PROJECT_VERSION} /${PROJECT_NAME}/target/custom-runtime/${PROJECT_NAME}";

# Copy the bootstrap to the custom-runtime
sudo cp bootstrap target/custom-runtime/bootstrap;

# Make bootstrap executable
sudo chmod +x target/custom-runtime/bootstrap;

# Zip
rm $PROJECT_NAME-custom-runtime.zip
cd target/custom-runtime || exit
zip -X -r ../../$PROJECT_NAME-custom-runtime.zip .
