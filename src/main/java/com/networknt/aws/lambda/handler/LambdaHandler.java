package com.networknt.aws.lambda.handler;

import com.networknt.aws.lambda.LightLambdaExchange;
import com.networknt.status.Status;

public interface LambdaHandler {

    String DISABLED_LAMBDA_HANDLER_RETURN = "ERR14001";
    String SUCCESS_LAMBDA_HANDLER_RETURN = "SUC14200";

    Status execute(final LightLambdaExchange exchange);

    /**
     *
     * Indicate if this handler is enabled or not.
     *
     * @return boolean true if enabled
     */
    boolean isEnabled();

    /**
     * Register this handler to the handler registration.
     */
    void register();

    /**
     * Reload config values in case the config values changed by config server.
     */
    void reload();

    /**
     * Indicate if this middleware handler is asynchronous or not.
     * @return boolean true if asynchronous
     */
    boolean isAsynchronous();

    default Status disabledMiddlewareStatus() {
        return new Status(409, DISABLED_LAMBDA_HANDLER_RETURN, "Middleware handler is disabled", "CONFLICT", "ERROR");
    }

    default Status successMiddlewareStatus() {
        return new Status(200, SUCCESS_LAMBDA_HANDLER_RETURN, "OK", "SUCCESS", "SUCCESS");
    }

    default boolean isResponseMiddleware() {
        return false;
    };

}
