package com.networknt.aws.lambda.app;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.networknt.config.Config;
import com.networknt.config.schema.BooleanField;
import com.networknt.config.schema.ConfigSchema;
import com.networknt.config.schema.OutputFormat;
import com.networknt.config.schema.StringField;
import com.networknt.server.ModuleRegistry;

import java.util.Map;

@ConfigSchema(configKey = "lambda-app", configName = "lambda-app", configDescription = "Configuration for Lambda native application.", outputFormats = {
        OutputFormat.JSON_SCHEMA, OutputFormat.YAML })
public class LambdaAppConfig {
    public static final String CONFIG_NAME = "lambda-app";
    public static final String LAMBDA_APP_ID = "lambdaAppId";
    public static final String ENCODE_BASE64_RESPONSE = "encodeBase64Response";
    public static final String ENCODE_BASE64_REQUEST = "encodeBase64Request";

    private final Map<String, Object> mappedConfig;
    private static LambdaAppConfig instance;

    @StringField(configFieldName = LAMBDA_APP_ID, externalizedKeyName = LAMBDA_APP_ID, description = "The lambda application identifier.")
    private String lambdaAppId;

    @BooleanField(
            configFieldName = ENCODE_BASE64_REQUEST,
            externalizedKeyName = ENCODE_BASE64_REQUEST,
            defaultValue = "false",
            description = "Encodes the request payload as base64 if not already. Default value is false."
    )
    @JsonProperty(value = ENCODE_BASE64_REQUEST, defaultValue = "false")
    private boolean encodeBase64Request;

    @BooleanField(
            configFieldName = ENCODE_BASE64_RESPONSE,
            externalizedKeyName = ENCODE_BASE64_RESPONSE,
            defaultValue = "false",
            description = "Encodes the response payload as base64 if not already. Default value is false."
    )
    @JsonProperty(value = ENCODE_BASE64_RESPONSE, defaultValue = "false")
    private boolean encodeBase64Response;



    private LambdaAppConfig() {
        this(CONFIG_NAME);
    }

    private LambdaAppConfig(String configName) {
        mappedConfig = Config.getInstance().getJsonMapConfig(configName);
        setConfigData();
    }

    public static LambdaAppConfig load() {
        return load(CONFIG_NAME);
    }

    public static LambdaAppConfig load(String configName) {
        if (CONFIG_NAME.equals(configName)) {
            Map<String, Object> mappedConfig = Config.getInstance().getJsonMapConfig(configName);
            if (instance != null && instance.getMappedConfig() == mappedConfig) {
                return instance;
            }
            synchronized (LambdaAppConfig.class) {
                mappedConfig = Config.getInstance().getJsonMapConfig(configName);
                if (instance != null && instance.getMappedConfig() == mappedConfig) {
                    return instance;
                }
                instance = new LambdaAppConfig(configName);
                ModuleRegistry.registerModule(CONFIG_NAME, LambdaAppConfig.class.getName(),
                        Config.getNoneDecryptedInstance().getJsonMapConfigNoCache(CONFIG_NAME), null);
                return instance;
            }
        }
        return new LambdaAppConfig(configName);
    }

    private void setConfigData() {
        Object object = mappedConfig.get(LAMBDA_APP_ID);
        if (object instanceof String val) {
            lambdaAppId = val;
        }
        object = mappedConfig.get(ENCODE_BASE64_REQUEST);
        if (object != null) {
            encodeBase64Request = Config.loadBooleanValue(ENCODE_BASE64_REQUEST, object);
        }
        object = mappedConfig.get(ENCODE_BASE64_RESPONSE);
        if (object != null) {
            encodeBase64Response = Config.loadBooleanValue(ENCODE_BASE64_RESPONSE, object);
        }
    }

    public String getLambdaAppId() {
        return lambdaAppId;
    }

    public boolean isEncodeBase64Request() {
        return encodeBase64Request;
    }

    public boolean isEncodeBase64Response() {
        return encodeBase64Response;
    }

    public Map<String, Object> getMappedConfig() {
        return mappedConfig;
    }
}
