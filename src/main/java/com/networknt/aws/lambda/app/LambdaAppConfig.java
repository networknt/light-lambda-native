package com.networknt.aws.lambda.app;

import com.networknt.config.Config;
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

    private final Map<String, Object> mappedConfig;
    private static LambdaAppConfig instance;

    @StringField(configFieldName = LAMBDA_APP_ID, externalizedKeyName = LAMBDA_APP_ID, description = "The lambda application identifier.")
    private String lambdaAppId;

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
        if (object != null) {
            lambdaAppId = (String) object;
        }
    }

    public String getLambdaAppId() {
        return lambdaAppId;
    }

    public void setLambdaAppId(String lambdaAppId) {
        this.lambdaAppId = lambdaAppId;
    }

    public Map<String, Object> getMappedConfig() {
        return mappedConfig;
    }
}
