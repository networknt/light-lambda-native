package com.networknt.aws.lambda;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import com.networknt.aws.lambda.proxy.LambdaProxy;


public class Main {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    public static void main(String[] args) throws IOException {


        String testInvoke = "{\n" +
                "  \"body\": \"{\\\"id\\\": 33, \\\"name\\\": \\\"doggie\\\"}\",\n" +
                "  \"resource\": \"/{proxy+}\",\n" +
                "  \"path\": \"/v1/pets\",\n" +
                "  \"httpMethod\": \"POST\",\n" +
                "  \"isBase64Encoded\": true,\n" +
                "  \"queryStringParameters\": {\n" +
                "    \"foo\": \"bar\"\n" +
                "  },\n" +
                "  \"multiValueQueryStringParameters\": {\n" +
                "    \"foo\": [\n" +
                "      \"bar\"\n" +
                "    ]\n" +
                "  },\n" +
                "  \"pathParameters\": {\n" +
                "    \"proxy\": \"/path/to/resource\"\n" +
                "  },\n" +
                "  \"stageVariables\": {\n" +
                "    \"baz\": \"qux\"\n" +
                "  },\n" +
                "  \"headers\": {\n" +
                "    \"Accept\": \"text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8\",\n" +
                "    \"Accept-Encoding\": \"gzip, deflate, sdch\",\n" +
                "    \"Accept-Language\": \"en-US,en;q=0.8\",\n" +
                "    \"Content-Type\": \"application/json\", \n" +
                "    \"Cache-Control\": \"max-age=0\",\n" +
                "    \"CloudFront-Forwarded-Proto\": \"https\",\n" +
                "    \"CloudFront-Is-Desktop-Viewer\": \"true\",\n" +
                "    \"CloudFront-Is-Mobile-Viewer\": \"false\",\n" +
                "    \"CloudFront-Is-SmartTV-Viewer\": \"false\",\n" +
                "    \"CloudFront-Is-Tablet-Viewer\": \"false\",\n" +
                "    \"CloudFront-Viewer-Country\": \"US\",\n" +
                "    \"Authorization\": \"Bearer eyJraWQiOiJabHFicm1LNDluM2xmU1pVeW5MS2tVVVdxYXRsYzhUT0JERmVVNHlhclM0IiwiYWxnIjoiUlMyNTYifQ.eyJ2ZXIiOjEsImp0aSI6IkFULmEtRl9wcGt5WTBtUFMwU1lkckdwbHdkTzQ0YUMxRXpBWHhkQ3d5Tjc4ZFkiLCJpc3MiOiJodHRwczovL3N1bmxpZmVhcGkub2t0YXByZXZpZXcuY29tL29hdXRoMi9hdXM2NnU1Y3liVHJDc2JaczFkNiIsImF1ZCI6ImRldi5jYW5hZGEucmVmZXJlbmNlYXBpLnN1bmxpZmVhcGlzLm9rdGFwcmV2aWV3LmNvbSIsImlhdCI6MTY5NjYxMjczMiwiZXhwIjoxNjk2Njk5MTMyLCJjaWQiOiIwb2E2NnU1NmlyWGlla1FWUTFkNiIsInNjcCI6WyJodHRwczovL3NlcnZpY2VzLnN1bmxpZmUuY29tL29rdGEvb2F1dGgyL2NvcnBvcmF0ZS5yZWZlcmVuY2UtZG9tYWluLnBldHN0b3JlLnBldHMudWFkIl0sInN1YiI6IjBvYTY2dTU2aXJYaWVrUVZRMWQ2In0.crP1filaGy16bQ7q5KQ-o94wlk19QG4X6z6l8gWj6wwJvXvv_ZZ1tabiHMSfBOrm9lnqL6zGJ6VgWa06R-KZMkVe2rS8en7c84d-SlKYR6k8PWv_tTfTzfApLznyr9__n8TC6YqN3KxHOzKQ-BRkljDPBRGoNtKfv7XNy7gese9D3tsGjPxn3Vb9PyiW1JX97zMqbf3xx44sKAOlimbTjxUR8P4OcRyXQhgoIMHU4L46M3D4F67-7JThNlAndN2wlOqXcOkevy_Ffg-yF8DBdN9b5qk47BmR5o-Et-UpzhCh3iXrPBaQUbTfvii4bygSDx9ljyHQQD1vQEvq2rMt7A\",\n" +
                "    \"Host\": \"1234567890.execute-api.us-east-2.amazonaws.com\",\n" +
                "    \"Upgrade-Insecure-Requests\": \"1\",\n" +
                "    \"User-Agent\": \"Custom User Agent String\",\n" +
                "    \"Via\": \"1.1 08f323deadbeefa7af34d5feb414ce27.cloudfront.net (CloudFront)\",\n" +
                "    \"X-Amz-Cf-Id\": \"cDehVQoZnx43VYQb9j2-nvCh-9z396Uhbp027Y2JvkCPNLmGJHqlaA==\",\n" +
                "    \"X-Forwarded-For\": \"127.0.0.1, 127.0.0.2\",\n" +
                "    \"X-Forwarded-Port\": \"443\",\n" +
                "    \"X-Forwarded-Proto\": \"https\",\n" +
                "    \"x-traceability-id\": \"123-123-123\",\n" +
                "    \"x-some-random-header\": \"randomHeaderValue\"\n" +
                "  },\n" +
                "  \"multiValueHeaders\": {\n" +
                "    \"Accept\": [\n" +
                "      \"text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8\"\n" +
                "    ],\n" +
                "    \"Accept-Encoding\": [\n" +
                "      \"gzip, deflate, sdch\"\n" +
                "    ],\n" +
                "    \"Accept-Language\": [\n" +
                "      \"en-US,en;q=0.8\"\n" +
                "    ],\n" +
                "    \"Cache-Control\": [\n" +
                "      \"max-age=0\"\n" +
                "    ],\n" +
                "    \"CloudFront-Forwarded-Proto\": [\n" +
                "      \"https\"\n" +
                "    ],\n" +
                "    \"CloudFront-Is-Desktop-Viewer\": [\n" +
                "      \"true\"\n" +
                "    ],\n" +
                "    \"CloudFront-Is-Mobile-Viewer\": [\n" +
                "      \"false\"\n" +
                "    ],\n" +
                "    \"CloudFront-Is-SmartTV-Viewer\": [\n" +
                "      \"false\"\n" +
                "    ],\n" +
                "    \"CloudFront-Is-Tablet-Viewer\": [\n" +
                "      \"false\"\n" +
                "    ],\n" +
                "    \"CloudFront-Viewer-Country\": [\n" +
                "      \"US\"\n" +
                "    ],\n" +
                "    \"Host\": [\n" +
                "      \"0123456789.execute-api.us-east-2.amazonaws.com\"\n" +
                "    ],\n" +
                "    \"Upgrade-Insecure-Requests\": [\n" +
                "      \"1\"\n" +
                "    ],\n" +
                "    \"User-Agent\": [\n" +
                "      \"Custom User Agent String\"\n" +
                "    ],\n" +
                "    \"Via\": [\n" +
                "      \"1.1 08f323deadbeefa7af34d5feb414ce27.cloudfront.net (CloudFront)\"\n" +
                "    ],\n" +
                "    \"X-Amz-Cf-Id\": [\n" +
                "      \"cDehVQoZnx43VYQb9j2-nvCh-9z396Uhbp027Y2JvkCPNLmGJHqlaA==\"\n" +
                "    ],\n" +
                "    \"X-Forwarded-For\": [\n" +
                "      \"127.0.0.1, 127.0.0.2\"\n" +
                "    ],\n" +
                "    \"X-Forwarded-Port\": [\n" +
                "      \"443\"\n" +
                "    ],\n" +
                "    \"X-Forwarded-Proto\": [\n" +
                "      \"https\"\n" +
                "    ]\n" +
                "  },\n" +
                "  \"requestContext\": {\n" +
                "    \"accountId\": \"123456789012\",\n" +
                "    \"resourceId\": \"123456\",\n" +
                "    \"stage\": \"Prod\",\n" +
                "    \"requestId\": \"c6af9ac6-7b61-11e6-9a41-93e8deadbeef\",\n" +
                "    \"requestTime\": \"09/Apr/2015:12:34:56 +0000\",\n" +
                "    \"requestTimeEpoch\": 1428582896000,\n" +
                "    \"identity\": {\n" +
                "      \"cognitoIdentityPoolId\": null,\n" +
                "      \"accountId\": null,\n" +
                "      \"cognitoIdentityId\": null,\n" +
                "      \"caller\": null,\n" +
                "      \"accessKey\": null,\n" +
                "      \"sourceIp\": \"127.0.0.1\",\n" +
                "      \"cognitoAuthenticationType\": null,\n" +
                "      \"cognitoAuthenticationProvider\": null,\n" +
                "      \"userArn\": null,\n" +
                "      \"userAgent\": \"Custom User Agent String\",\n" +
                "      \"user\": null\n" +
                "    },\n" +
                "    \"path\": \"/prod/path/to/resource\",\n" +
                "    \"resourcePath\": \"/{proxy+}\",\n" +
                "    \"httpMethod\": \"POST\",\n" +
                "    \"apiId\": \"1234567890\",\n" +
                "    \"protocol\": \"HTTP/1.1\"\n" +
                "  }\n" +
                "}";

        APIGatewayProxyRequestEvent apiGatewayProxyRequestEvent = OBJECT_MAPPER.readValue(testInvoke, APIGatewayProxyRequestEvent.class);
        var invocation = InvocationResponse.builder()
                .requestId("12345")
                .event(apiGatewayProxyRequestEvent)
                .build();

        final APIGatewayProxyRequestEvent requestEvent = invocation.getEvent();
        final LambdaContext lambdaContext = new LambdaContext(invocation.getRequestId());

        LambdaProxy lambdaProxy = new LambdaProxy();
        APIGatewayProxyResponseEvent responseEvent = lambdaProxy.handleRequest(requestEvent, lambdaContext);

        System.out.println(responseEvent.toString());


    }
}
