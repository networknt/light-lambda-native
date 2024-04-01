package com.networknt.aws.lambda.handler.chain;

public class PooledChainConfig {
    private int maxPoolSize;
    private int corePoolSize;
    private int maxChainSize;
    private long keepAliveTime;
    private boolean forceSynchronousExecution;
    private boolean exitOnMiddlewareInstanceCreationFailure;

    public int getCorePoolSize() {
        return corePoolSize;
    }

    public int getMaxPoolSize() {
        return maxPoolSize;
    }

    public long getKeepAliveTime() {
        return keepAliveTime;
    }

    public int getMaxChainSize() {
        return maxChainSize;
    }

    public boolean isForceSynchronousExecution() {
        return forceSynchronousExecution;
    }

    public boolean isExitOnMiddlewareInstanceCreationFailure() {
        return exitOnMiddlewareInstanceCreationFailure;
    }
}
