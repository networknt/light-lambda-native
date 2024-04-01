package com.networknt.aws.lambda.handler;

public interface MiddlewareHandler extends LambdaHandler {

    /**
     * Indicate if this middleware handler will continue on failure or not.
     */
    boolean isContinueOnFailure();

    /**
     * Indicate if this middleware handler is audited or not.
     */
    boolean isAudited();

    void getCachedConfigurations();



}
