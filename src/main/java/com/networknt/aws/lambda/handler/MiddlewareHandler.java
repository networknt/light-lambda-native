package com.networknt.aws.lambda.handler;

public interface MiddlewareHandler extends LambdaHandler {

    /**
     * Indicate if this middleware handler will continue on failure or not.
     * @return boolean true if continue on failure
     */
    boolean isContinueOnFailure();

    /**
     * Indicate if this middleware handler is audited or not.
     * @return boolean true if audited
     */
    boolean isAudited();

    void getCachedConfigurations();



}
