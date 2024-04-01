package com.networknt.aws.lambda.handler.middleware.validator;

import com.mservicetech.openapi.common.RequestEntity;
import com.mservicetech.openapi.validation.OpenApiValidator;
import com.networknt.aws.lambda.handler.MiddlewareHandler;
import com.networknt.aws.lambda.handler.middleware.LightLambdaExchange;
import com.networknt.aws.lambda.utility.HeaderKey;
import com.networknt.aws.lambda.utility.HeaderValue;
import com.networknt.config.Config;
import com.networknt.openapi.ValidatorConfig;
import com.networknt.status.Status;
import com.networknt.utility.ModuleRegistry;
import org.apache.commons.lang3.NotImplementedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

public class ValidatorMiddleware implements MiddlewareHandler {

    private static final Logger LOG = LoggerFactory.getLogger(ValidatorMiddleware.class);
    private static final String CONFIG_NAME = "lambda-validator";
    private static final String OPENAPI_NAME = "openapi.yaml";
    private static final String CONTENT_TYPE_MISMATCH = "ERR10015";
    private static OpenApiValidator OPENAPI_VALIDATOR;

    private static ValidatorConfig CONFIG;

    public ValidatorMiddleware() {
        if (LOG.isInfoEnabled()) LOG.info("ValidatorMiddleware is constructed");
        CONFIG = ValidatorConfig.load();
    }

    @Override
    public Status execute(final LightLambdaExchange exchange) throws InterruptedException {

        if (!CONFIG.isEnabled())
            return disabledMiddlewareStatus();

        LOG.debug("ValidatorHandler.executeMiddleware starts.");
        LOG.debug("Validation Time - Start: {}", System.currentTimeMillis());

        if (this.shouldValidateRequestBody(exchange)) {

            if (exchange.getRequest().getBody() != null) {
                var requestEntity = new RequestEntity();
                requestEntity.setRequestBody(exchange.getRequest().getBody());
                requestEntity.setHeaderParameters(exchange.getRequest().getHeaders());
                requestEntity.setContentType(HeaderValue.APPLICATION_JSON);
                var status = ValidatorMiddleware.getValidatorInstance().validateRequestPath(exchange.getRequest().getPath(), exchange.getRequest().getHttpMethod(), requestEntity);

                if (status != null)
                    return new Status(status.getCode());

                else return successMiddlewareStatus();

            } else return new Status(CONTENT_TYPE_MISMATCH);

        } else if (this.shouldValidateResponseBody(exchange)) {

            if (exchange.getResponse().getBody() != null) {

                // TODO - response body validation
                return successMiddlewareStatus();

            } else return new Status(CONTENT_TYPE_MISMATCH);
        }

        LOG.debug("Validation Time - Finish: {}", System.currentTimeMillis());
        LOG.debug("ValidatorHandler.executeMiddleware ends.");

        return successMiddlewareStatus();
    }

    private static OpenApiValidator getValidatorInstance() {
        if (OPENAPI_VALIDATOR == null) {
            InputStream in  = Config.getInstance().getInputStreamFromFile(OPENAPI_NAME);
            if (in != null) {
                OPENAPI_VALIDATOR = new OpenApiValidator(in);

                try {
                    in.close();

                } catch (IOException e) {
                    LOG.error("Failed to close stream on openapi.yaml.");
                    throw new RuntimeException(e);
                }

            } else OPENAPI_VALIDATOR = new OpenApiValidator();

        }
        return OPENAPI_VALIDATOR;
    }

    private boolean shouldValidateRequestBody(final LightLambdaExchange exchange) {
        return exchange.isRequestInProgress()
                && this.isApplicationJsonContentType(exchange.getRequest().getHeaders())
                && !CONFIG.isSkipBodyValidation();
    }

    private boolean shouldValidateResponseBody(final LightLambdaExchange exchange) {
        return exchange.isResponseInProgress()
                && this.isApplicationJsonContentType(exchange.getResponse().getHeaders())
                && CONFIG.isValidateResponse();
    }

    private boolean isApplicationJsonContentType(Map<String, String> headers) {
        return headers.containsKey(HeaderKey.CONTENT_TYPE)
                && headers.get(HeaderKey.CONTENT_TYPE).equals(HeaderValue.APPLICATION_JSON);
    }

    @Override
    public void getCachedConfigurations() {
        throw new NotImplementedException();
    }

    @Override
    public boolean isEnabled() {
        return CONFIG.isEnabled();
    }

    @Override
    public void register() {
        ModuleRegistry.registerModule(
                ValidatorConfig.CONFIG_NAME,
                ValidatorMiddleware.class.getName(),
                Config.getNoneDecryptedInstance().getJsonMapConfigNoCache(ValidatorConfig.CONFIG_NAME),
                null
        );
    }

    @Override
    public void reload() {
        throw new NotImplementedException();
    }

    @Override
    public boolean isContinueOnFailure() {
        throw new NotImplementedException();
    }

    @Override
    public boolean isAudited() {
        throw new NotImplementedException();
    }

    @Override
    public boolean isAsynchronous() {
        return false;
    }

}
