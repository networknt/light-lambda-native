package com.networknt.aws.lambda;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

public class TestUtils {

    private static final String TEST_EVENT = "{\n" +
            "  \"body\": \"eyJ0ZXN0IjoiYm9keSJ9\",\n" +
            "  \"resource\": \"/{proxy+}\",\n" +
            "  \"path\": \"/petstore\",\n" +
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
            "    \"Cache-Control\": \"max-age=0\",\n" +
            "    \"CloudFront-Forwarded-Proto\": \"https\",\n" +
            "    \"CloudFront-Is-Desktop-Viewer\": \"true\",\n" +
            "    \"CloudFront-Is-Mobile-Viewer\": \"false\",\n" +
            "    \"CloudFront-Is-SmartTV-Viewer\": \"false\",\n" +
            "    \"CloudFront-Is-Tablet-Viewer\": \"false\",\n" +
            "    \"CloudFront-Viewer-Country\": \"US\",\n" +
            "    \"Authorization\": \"Bearer eyJraWQiOiIxMDAiLCJhbGciOiJSUzI1NiJ9.eyJpc3MiOiJ1cm46Y29tOm5ldHdvcmtudDpvYXV0aDI6djEiLCJhdWQiOiJ1cm46Y29tLm5ldHdvcmtudCIsImV4cCI6MTc5MDAzNTcwOSwianRpIjoiSTJnSmdBSHN6NzJEV2JWdUFMdUU2QSIsImlhdCI6MTQ3NDY3NTcwOSwibmJmIjoxNDc0Njc1NTg5LCJ2ZXJzaW9uIjoiMS4wIiwidXNlcl9pZCI6InN0ZXZlIiwidXNlcl90eXBlIjoiRU1QTE9ZRUUiLCJjbGllbnRfaWQiOiJmN2Q0MjM0OC1jNjQ3LTRlZmItYTUyZC00YzU3ODc0MjFlNzIiLCJzY29wZSI6WyJ3cml0ZTpwZXRzIiwicmVhZDpwZXRzIl19.mue6eh70kGS3Nt2BCYz7ViqwO7lh_4JSFwcHYdJMY6VfgKTHhsIGKq2uEDt3zwT56JFAePwAxENMGUTGvgceVneQzyfQsJeVGbqw55E9IfM_uSM-YcHwTfR7eSLExN4pbqzVDI353sSOvXxA98ZtJlUZKgXNE1Ngun3XFORCRIB_eH8B0FY_nT_D1Dq2WJrR-re-fbR6_va95vwoUdCofLRa4IpDfXXx19ZlAtfiVO44nw6CS8O87eGfAm7rCMZIzkWlCOFWjNHnCeRsh7CVdEH34LF-B48beiG5lM7h4N12-EME8_VDefgMjZ8eqs1ICvJMxdIut58oYbdnkwTjkA\",\n" +
            "    \"Host\": \"1234567890.execute-api.us-east-2.amazonaws.com\",\n" +
            "    \"Upgrade-Insecure-Requests\": \"1\",\n" +
            "    \"User-Agent\": \"Custom User Agent String\",\n" +
            "    \"Via\": \"1.1 08f323deadbeefa7af34d5feb414ce27.cloudfront.net (CloudFront)\",\n" +
            "    \"X-Amz-Cf-Id\": \"cDehVQoZnx43VYQb9j2-nvCh-9z396Uhbp027Y2JvkCPNLmGJHqlaA==\",\n" +
            "    \"X-Forwarded-For\": \"127.0.0.1, 127.0.0.2\",\n" +
            "    \"X-Forwarded-Port\": \"443\",\n" +
            "    \"X-Forwarded-Proto\": \"https\"\n" +
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

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    public static APIGatewayProxyRequestEvent createTestRequestEvent() {
        APIGatewayProxyRequestEvent apiGatewayProxyRequestEvent;
        try {
            apiGatewayProxyRequestEvent = OBJECT_MAPPER.readValue(TEST_EVENT, APIGatewayProxyRequestEvent.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }

        return apiGatewayProxyRequestEvent;
    }
}
