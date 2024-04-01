package com.networknt.aws.lambda.middleware;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.networknt.aws.lambda.handler.middleware.LightLambdaExchange;
import com.networknt.config.Config;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.InvokeRequest;
import software.amazon.awssdk.services.lambda.model.LambdaException;

import java.io.IOException;

import static org.testcontainers.containers.BindMode.READ_WRITE;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class MiddlewareTestBase {
    private static final String LOCAL_STACK_ACCESS_KEY = "testAccessKey";
    private static final String LOCAL_STACK_ACCESS_ID = "testUser";
    private static final String LOCAL_STACK_LAMBDA_FUNCTION_NAME = "local-lambda";
    private static final DockerImageName LOCAL_STACK_IMAGE = DockerImageName.parse("localstack/localstack");

    @Container
    private static final LocalStackContainer LOCAL_STACK_CONTAINER = new LocalStackContainer(LOCAL_STACK_IMAGE)
            .withExposedPorts(4566, 4510)
            .withFileSystemBind("../light-aws-lambda/local-lambda/volume", "/var/lib/localstack", READ_WRITE)
            .withEnv("AWS_ACCESS_KEY_ID", LOCAL_STACK_ACCESS_ID)
            .withEnv("AWS_SECRET_ACCESS_KEY", LOCAL_STACK_ACCESS_KEY)
            .withEnv("DOCKER_HOST", "unix:///var/run/docker.sock")
            .withEnv("LAMBDA_EXECUTOR", "local")
            .withEnv("HOSTNAME_EXTERNAL", "localhost")
            .withEnv("LOCALSTACK_HOSTNAME", "localhost")
            .withServices(LocalStackContainer.Service.LAMBDA);

    private LambdaClient lambdaClient = null;

    @BeforeAll
    void setupLambdaClient() {
        lambdaClient = LambdaClient.builder()
                .endpointOverride(LOCAL_STACK_CONTAINER.getEndpoint())
                .credentialsProvider(
                        StaticCredentialsProvider.create(
                                AwsBasicCredentials.create(LOCAL_STACK_CONTAINER.getAccessKey(), LOCAL_STACK_CONTAINER.getSecretKey())
                        ))
                .region(Region.of(LOCAL_STACK_CONTAINER.getRegion()))
                .build();

        try {
            LOCAL_STACK_CONTAINER.execInContainer("awslocal", "lambda", "create-function",
                    "--function-name", LOCAL_STACK_LAMBDA_FUNCTION_NAME,
                    "--runtime", "java11",
                    "--handler", "com.networknt.aws.LocalLambdaFunction::handleRequest",
                    "--zip-file", "fileb:///var/lib/localstack/lib/local-lambda.jar",
                    "--role", "arn:aws:iam::000000000000:role/lambda-role");
            LOCAL_STACK_CONTAINER.execInContainer(
                    "awslocal", "lambda", "wait", "function-active",
                    "--function-name", LOCAL_STACK_LAMBDA_FUNCTION_NAME);
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    protected String invokeLocalLambdaFunction(final LightLambdaExchange eventWrapper) {
        final LambdaClient client = this.lambdaClient;
        String serializedEvent = null;
        try {
            serializedEvent = Config.getInstance().getMapper().writeValueAsString(eventWrapper.getRequest());
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        String response = null;

        try {
            var payload = SdkBytes.fromUtf8String(serializedEvent);
            var request = InvokeRequest.builder()
                    .functionName(LOCAL_STACK_LAMBDA_FUNCTION_NAME)
                    .logType("Tail")
                    .payload(payload)
                    .build();
            var res = client.invoke(request);

            response = res.payload().asUtf8String();
        } catch (LambdaException e) {
            throw new RuntimeException(e);
        }
        return response;
    }

}
