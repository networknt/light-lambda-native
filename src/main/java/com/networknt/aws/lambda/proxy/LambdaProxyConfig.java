package com.networknt.aws.lambda.proxy;

public class LambdaProxyConfig {
    public static final String CONFIG_NAME = "lambda-proxy";
    private String lambdaAppId;
    private String env;
    private boolean metricsInjection;
    private String metricsName;

    public boolean isMetricsInjection() {
        return metricsInjection;
    }

    public String getMetricsName() {
        return metricsName;
    }

    public String getLambdaAppId() {
        return lambdaAppId;
    }

    public String getEnv() {
        return env;
    }

}
