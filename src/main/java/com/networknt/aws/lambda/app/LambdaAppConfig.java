package com.networknt.aws.lambda.app;

public class LambdaAppConfig {
    public static final String CONFIG_NAME = "lambda-app";
    private String lambdaAppId;

    public LambdaAppConfig() {
    }

    public String getLambdaAppId() {
        return lambdaAppId;
    }

    public void setLambdaAppId(String lambdaAppId) {
        this.lambdaAppId = lambdaAppId;
    }
}
