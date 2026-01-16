package com.networknt.aws.lambda.handler.middleware.proxy;

import java.util.Map;

public class LambdaProxyConfig {
    public static final String CONFIG_NAME = "lambda-proxy";
    private boolean enabled;
    private String region;
    private String endpointOverride;
    private int readTimeout;
    private int writeTimeout;
    private int connectionTimeout;
    private int apiCallAttemptTimeout;
    private int apiCallTimeout;
    private int maxRetryAttempts;
    private String logType;
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

    public int getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(int readTimeout) {
        this.readTimeout = readTimeout;
    }

    public int getWriteTimeout() {
        return writeTimeout;
    }

    public void setWriteTimeout(int writeTimeout) {
        this.writeTimeout = writeTimeout;
    }

    public int getConnectionTimeout() {
        return connectionTimeout;
    }

    public void setConnectionTimeout(int connectionTimeout) {
        this.connectionTimeout = connectionTimeout;
    }

    public int getApiCallAttemptTimeout() {
        return apiCallAttemptTimeout;
    }

    public void setApiCallAttemptTimeout(int apiCallAttemptTimeout) {
        this.apiCallAttemptTimeout = apiCallAttemptTimeout;
    }

    public int getApiCallTimeout() {
        return apiCallTimeout;
    }

    public void setApiCallTimeout(int apiCallTimeout) {
        this.apiCallTimeout = apiCallTimeout;
    }

    public int getMaxRetryAttempts() {
        return maxRetryAttempts;
    }

    public void setMaxRetryAttempts(int maxRetryAttempts) {
        this.maxRetryAttempts = maxRetryAttempts;
    }

    public String getLogType() {
        return logType;
    }

    public void setLogType(String logType) {
        this.logType = logType;
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
