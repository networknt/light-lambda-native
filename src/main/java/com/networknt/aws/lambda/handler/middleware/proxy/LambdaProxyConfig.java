package com.networknt.aws.lambda.handler.middleware.proxy;

import com.networknt.config.Config;
import com.networknt.config.schema.*;
import com.networknt.server.ModuleRegistry;

import java.util.Map;

@ConfigSchema(configKey = "lambda-proxy", configName = "lambda-proxy", configDescription = "Configuration for Lambda native proxy handler.", outputFormats = {
        OutputFormat.JSON_SCHEMA, OutputFormat.YAML })
public class LambdaProxyConfig {
    public static final String CONFIG_NAME = "lambda-proxy";
    public static final String ENABLED = "enabled";
    public static final String REGION = "region";
    public static final String ENDPOINT_OVERRIDE = "endpointOverride";
    public static final String READ_TIMEOUT = "readTimeout";
    public static final String WRITE_TIMEOUT = "writeTimeout";
    public static final String CONNECTION_TIMEOUT = "connectionTimeout";
    public static final String API_CALL_ATTEMPT_TIMEOUT = "apiCallAttemptTimeout";
    public static final String API_CALL_TIMEOUT = "apiCallTimeout";
    public static final String MAX_RETRY_ATTEMPTS = "maxRetryAttempts";
    public static final String LOG_TYPE = "logType";
    public static final String FUNCTIONS = "functions";
    public static final String METRICS_INJECTION = "metricsInjection";
    public static final String METRICS_NAME = "metricsName";

    private final Map<String, Object> mappedConfig;
    private static volatile LambdaProxyConfig instance;

    @BooleanField(configFieldName = ENABLED, externalizedKeyName = ENABLED, description = "Whether the lambda-proxy is enabled or not.")
    private boolean enabled;

    @StringField(configFieldName = REGION, externalizedKeyName = REGION, description = "The aws region that is used to create the LambdaClient.", defaultValue = "us-east-1")
    private String region;

    @StringField(configFieldName = ENDPOINT_OVERRIDE, externalizedKeyName = ENDPOINT_OVERRIDE, description = "endpoint override if for lambda function deployed in virtual private cloud.")
    private String endpointOverride;

    @IntegerField(configFieldName = READ_TIMEOUT, externalizedKeyName = READ_TIMEOUT, description = "Read timeout in milliseconds.")
    private int readTimeout;

    @IntegerField(configFieldName = WRITE_TIMEOUT, externalizedKeyName = WRITE_TIMEOUT, description = "Write timeout in milliseconds.")
    private int writeTimeout;

    @IntegerField(configFieldName = CONNECTION_TIMEOUT, externalizedKeyName = CONNECTION_TIMEOUT, description = "Connection timeout in milliseconds.")
    private int connectionTimeout;

    @IntegerField(configFieldName = API_CALL_ATTEMPT_TIMEOUT, externalizedKeyName = API_CALL_ATTEMPT_TIMEOUT, description = "Api call attempt timeout in milliseconds.")
    private int apiCallAttemptTimeout;

    @IntegerField(configFieldName = API_CALL_TIMEOUT, externalizedKeyName = API_CALL_TIMEOUT, description = "Api call timeout in milliseconds.")
    private int apiCallTimeout;

    @IntegerField(configFieldName = MAX_RETRY_ATTEMPTS, externalizedKeyName = MAX_RETRY_ATTEMPTS, description = "The maximum number of retries for the Lambda function invocation.")
    private int maxRetryAttempts;

    @StringField(configFieldName = LOG_TYPE, externalizedKeyName = LOG_TYPE, description = "The LogType of the execution log of Lambda. Set Tail to include and None to not include.", defaultValue = "Tail")
    private String logType;

    @MapField(configFieldName = FUNCTIONS, externalizedKeyName = FUNCTIONS, description = "Mapping of the endpoints to Lambda functions.", valueType = String.class)
    private Map<String, String> functions;

    @BooleanField(configFieldName = METRICS_INJECTION, externalizedKeyName = METRICS_INJECTION, description = "Whether to inject metrics info for the total response time of the downstream Lambda functions.")
    private boolean metricsInjection;

    @StringField(configFieldName = METRICS_NAME, externalizedKeyName = METRICS_NAME, description = "The metric name to use when metrics info is injected into the metrics handler.", defaultValue = "lambda-response")
    private String metricsName;

    private LambdaProxyConfig() {
        this(CONFIG_NAME);
    }

    private LambdaProxyConfig(String configName) {
        mappedConfig = Config.getInstance().getJsonMapConfig(configName);
        setConfigData();
    }

    public static LambdaProxyConfig load() {
        return load(CONFIG_NAME);
    }

    public static LambdaProxyConfig load(String configName) {
        if (CONFIG_NAME.equals(configName)) {
            Map<String, Object> mappedConfig = Config.getInstance().getJsonMapConfig(configName);
            if (instance != null && instance.getMappedConfig() == mappedConfig) {
                return instance;
            }
            synchronized (LambdaProxyConfig.class) {
                mappedConfig = Config.getInstance().getJsonMapConfig(configName);
                if (instance != null && instance.getMappedConfig() == mappedConfig) {
                    return instance;
                }
                instance = new LambdaProxyConfig(configName);
                ModuleRegistry.registerModule(
                        LambdaProxyConfig.CONFIG_NAME,
                        LambdaProxyMiddleware.class.getName(),
                        Config.getNoneDecryptedInstance().getJsonMapConfigNoCache(LambdaProxyConfig.CONFIG_NAME),
                        null);
                return instance;
            }
        }
        return new LambdaProxyConfig(configName);
    }

    private void setConfigData() {
        Object object = mappedConfig.get(ENABLED);
        if (object != null)
            enabled = Config.loadBooleanValue(ENABLED, object);
        object = mappedConfig.get(REGION);
        if (object != null)
            region = (String) object;
        object = mappedConfig.get(ENDPOINT_OVERRIDE);
        if (object != null)
            endpointOverride = (String) object;
        object = mappedConfig.get(READ_TIMEOUT);
        if (object != null)
            readTimeout = Config.loadIntegerValue(READ_TIMEOUT, object);
        object = mappedConfig.get(WRITE_TIMEOUT);
        if (object != null)
            writeTimeout = Config.loadIntegerValue(WRITE_TIMEOUT, object);
        object = mappedConfig.get(CONNECTION_TIMEOUT);
        if (object != null)
            connectionTimeout = Config.loadIntegerValue(CONNECTION_TIMEOUT, object);
        object = mappedConfig.get(API_CALL_ATTEMPT_TIMEOUT);
        if (object != null)
            apiCallAttemptTimeout = Config.loadIntegerValue(API_CALL_ATTEMPT_TIMEOUT, object);
        object = mappedConfig.get(API_CALL_TIMEOUT);
        if (object != null)
            apiCallTimeout = Config.loadIntegerValue(API_CALL_TIMEOUT, object);
        object = mappedConfig.get(MAX_RETRY_ATTEMPTS);
        if (object != null)
            maxRetryAttempts = Config.loadIntegerValue(MAX_RETRY_ATTEMPTS, object);
        object = mappedConfig.get(LOG_TYPE);
        if (object != null)
            logType = (String) object;
        object = mappedConfig.get(FUNCTIONS);
        if (object != null)
            functions = (Map<String, String>) object;
        object = mappedConfig.get(METRICS_INJECTION);
        if (object != null)
            metricsInjection = Config.loadBooleanValue(METRICS_INJECTION, object);
        object = mappedConfig.get(METRICS_NAME);
        if (object != null)
            metricsName = (String) object;
    }

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

    public Map<String, Object> getMappedConfig() {
        return mappedConfig;
    }
}
