package com.networknt.aws.lambda.handler.chain;

import com.networknt.config.Config;
import com.networknt.config.schema.BooleanField;
import com.networknt.config.schema.ConfigSchema;
import com.networknt.config.schema.IntegerField;
import com.networknt.config.schema.OutputFormat;
import com.networknt.server.ModuleRegistry;

import java.util.Map;

@ConfigSchema(configKey = "pooled-chain", configName = "pooled-chain", configDescription = "Configuration for pooled handler chain.", outputFormats = {
        OutputFormat.JSON_SCHEMA, OutputFormat.YAML })
public class PooledChainConfig {
    public static final String CONFIG_NAME = "pooled-chain";
    public static final String MAX_POOL_SIZE = "maxPoolSize";
    public static final String CORE_POOL_SIZE = "corePoolSize";
    public static final String MAX_CHAIN_SIZE = "maxChainSize";
    public static final String KEEP_ALIVE_TIME = "keepAliveTime";
    public static final String FORCE_SYNCHRONOUS_EXECUTION = "forceSynchronousExecution";
    public static final String EXIT_ON_MIDDLEWARE_INSTANCE_CREATION_FAILURE = "exitOnMiddlewareInstanceCreationFailure";

    private final Map<String, Object> mappedConfig;
    private static PooledChainConfig instance;

    @IntegerField(configFieldName = MAX_POOL_SIZE, externalizedKeyName = MAX_POOL_SIZE, description = "The maximum number of handler chains in the pool.")
    private int maxPoolSize;

    @IntegerField(configFieldName = CORE_POOL_SIZE, externalizedKeyName = CORE_POOL_SIZE, description = "The core number of handler chains in the pool.")
    private int corePoolSize;

    @IntegerField(configFieldName = MAX_CHAIN_SIZE, externalizedKeyName = MAX_CHAIN_SIZE, description = "The maximum size of each handler chain.")
    private int maxChainSize;

    @IntegerField(configFieldName = KEEP_ALIVE_TIME, externalizedKeyName = KEEP_ALIVE_TIME, description = "The keep alive time for handler chains in the pool in milliseconds.")
    private long keepAliveTime;

    @BooleanField(configFieldName = FORCE_SYNCHRONOUS_EXECUTION, externalizedKeyName = FORCE_SYNCHRONOUS_EXECUTION, description = "Whether to force synchronous execution of the handler chain.")
    private boolean forceSynchronousExecution;

    @BooleanField(configFieldName = EXIT_ON_MIDDLEWARE_INSTANCE_CREATION_FAILURE, externalizedKeyName = EXIT_ON_MIDDLEWARE_INSTANCE_CREATION_FAILURE, description = "Whether to exit the application if a middleware instance creation fails.")
    private boolean exitOnMiddlewareInstanceCreationFailure;

    private PooledChainConfig() {
        this(CONFIG_NAME);
    }

    private PooledChainConfig(String configName) {
        mappedConfig = Config.getInstance().getJsonMapConfig(configName);
        setConfigData();
    }

    public static PooledChainConfig load() {
        return load(CONFIG_NAME);
    }

    public static PooledChainConfig load(String configName) {
        if (CONFIG_NAME.equals(configName)) {
            Map<String, Object> mappedConfig = Config.getInstance().getJsonMapConfig(configName);
            if (instance != null && instance.getMappedConfig() == mappedConfig) {
                return instance;
            }
            synchronized (PooledChainConfig.class) {
                mappedConfig = Config.getInstance().getJsonMapConfig(configName);
                if (instance != null && instance.getMappedConfig() == mappedConfig) {
                    return instance;
                }
                instance = new PooledChainConfig(configName);
                ModuleRegistry.registerModule(CONFIG_NAME, PooledChainConfig.class.getName(),
                        Config.getNoneDecryptedInstance().getJsonMapConfigNoCache(CONFIG_NAME), null);
                return instance;
            }
        }
        return new PooledChainConfig(configName);
    }

    private void setConfigData() {
        Object object = mappedConfig.get(MAX_POOL_SIZE);
        if (object != null)
            maxPoolSize = Config.loadIntegerValue(MAX_POOL_SIZE, object);
        object = mappedConfig.get(CORE_POOL_SIZE);
        if (object != null)
            corePoolSize = Config.loadIntegerValue(CORE_POOL_SIZE, object);
        object = mappedConfig.get(MAX_CHAIN_SIZE);
        if (object != null)
            maxChainSize = Config.loadIntegerValue(MAX_CHAIN_SIZE, object);
        object = mappedConfig.get(KEEP_ALIVE_TIME);
        if (object != null) {
            if (object instanceof Integer) {
                keepAliveTime = (Integer) object;
            } else if (object instanceof Long) {
                keepAliveTime = (Long) object;
            } else if (object instanceof String) {
                keepAliveTime = Long.parseLong((String) object);
            }
        }
        object = mappedConfig.get(FORCE_SYNCHRONOUS_EXECUTION);
        if (object != null)
            forceSynchronousExecution = Config.loadBooleanValue(FORCE_SYNCHRONOUS_EXECUTION, object);
        object = mappedConfig.get(EXIT_ON_MIDDLEWARE_INSTANCE_CREATION_FAILURE);
        if (object != null)
            exitOnMiddlewareInstanceCreationFailure = Config
                    .loadBooleanValue(EXIT_ON_MIDDLEWARE_INSTANCE_CREATION_FAILURE, object);
    }

    public int getCorePoolSize() {
        return corePoolSize;
    }

    public void setCorePoolSize(int corePoolSize) {
        this.corePoolSize = corePoolSize;
    }

    public int getMaxPoolSize() {
        return maxPoolSize;
    }

    public void setMaxPoolSize(int maxPoolSize) {
        this.maxPoolSize = maxPoolSize;
    }

    public long getKeepAliveTime() {
        return keepAliveTime;
    }

    public void setKeepAliveTime(long keepAliveTime) {
        this.keepAliveTime = keepAliveTime;
    }

    public int getMaxChainSize() {
        return maxChainSize;
    }

    public void setMaxChainSize(int maxChainSize) {
        this.maxChainSize = maxChainSize;
    }

    public boolean isForceSynchronousExecution() {
        return forceSynchronousExecution;
    }

    public void setForceSynchronousExecution(boolean forceSynchronousExecution) {
        this.forceSynchronousExecution = forceSynchronousExecution;
    }

    public boolean isExitOnMiddlewareInstanceCreationFailure() {
        return exitOnMiddlewareInstanceCreationFailure;
    }

    public void setExitOnMiddlewareInstanceCreationFailure(boolean exitOnMiddlewareInstanceCreationFailure) {
        this.exitOnMiddlewareInstanceCreationFailure = exitOnMiddlewareInstanceCreationFailure;
    }

    public Map<String, Object> getMappedConfig() {
        return mappedConfig;
    }
}
