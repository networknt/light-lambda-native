package com.networknt.aws.lambda.handler.middleware.invoke;

import java.util.Map;

public class LambdaInvokerConfig {
    public static final String CONFIG_NAME = "lambda-invoker";
    private boolean enabled;
    private String region;
    private String endpointOverride;
    private String logType;
    private String lambdaAppId;
    private Map<String, String> functions;
    private boolean metricsInjection;
    private String metricsName;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getEndpointOverride() {
        return endpointOverride;
    }

    public void setEndpointOverride(String endpointOverride) {
        this.endpointOverride = endpointOverride;
    }

    public String getLogType() {
        return logType;
    }

    public void setLogType(String logType) {
        this.logType = logType;
    }

    public String getLambdaAppId() {
        return lambdaAppId;
    }

    public void setLambdaAppId(String lambdaAppId) {
        this.lambdaAppId = lambdaAppId;
    }

    public Map<String, String> getFunctions() {
        return functions;
    }

    public void setFunctions(Map<String, String> functions) {
        this.functions = functions;
    }

    public boolean isMetricsInjection() {
        return metricsInjection;
    }

    public void setMetricsInjection(boolean metricsInjection) {
        this.metricsInjection = metricsInjection;
    }

    public String getMetricsName() {
        return metricsName;
    }

    public void setMetricsName(String metricsName) {
        this.metricsName = metricsName;
    }
}
