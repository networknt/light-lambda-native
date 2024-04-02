package com.networknt.aws.lambda.handler.middleware.validator;

import com.networknt.aws.lambda.handler.MiddlewareHandler;
import com.networknt.aws.lambda.handler.middleware.LightLambdaExchange;
import com.networknt.aws.lambda.handler.middleware.specification.OpenApiMiddleware;
import com.networknt.aws.lambda.utility.HeaderKey;
import com.networknt.aws.lambda.utility.HeaderValue;
import com.networknt.config.Config;
import com.networknt.openapi.ApiNormalisedPath;
import com.networknt.openapi.NormalisedPath;
import com.networknt.openapi.OpenApiOperation;
import com.networknt.openapi.ValidatorConfig;
import com.networknt.status.Status;
import com.networknt.utility.Constants;
import com.networknt.utility.ModuleRegistry;
import org.apache.commons.lang3.NotImplementedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import static com.networknt.aws.lambda.handler.middleware.audit.AuditMiddleware.AUDIT_ATTACHMENT_KEY;

public class ValidatorMiddleware implements MiddlewareHandler {

    private static final Logger LOG = LoggerFactory.getLogger(ValidatorMiddleware.class);
    private static final String OPENAPI_NAME = "openapi.yaml";
    static final String CONTENT_TYPE_MISMATCH = "ERR10015";
    static final String STATUS_MISSING_OPENAPI_OPERATION = "ERR10012";

    RequestValidator requestValidator;

    public static ValidatorConfig CONFIG;

    public ValidatorMiddleware() {
        if (LOG.isInfoEnabled()) LOG.info("ValidatorMiddleware is constructed");
        CONFIG = ValidatorConfig.load();
        final SchemaValidator schemaValidator = new SchemaValidator(OpenApiMiddleware.helper.openApi3);
        this.requestValidator = new RequestValidator(schemaValidator);
    }

    @Override
    public Status execute(final LightLambdaExchange exchange) throws InterruptedException {

        if (!CONFIG.isEnabled())
            return disabledMiddlewareStatus();

        LOG.debug("ValidatorMiddleware.execute starts at {}.", System.currentTimeMillis());
        String reqPath = exchange.getRequest().getPath();
        // if request path is in the skipPathPrefixes in the config, call the next handler directly to skip the validation.
        if (CONFIG.getSkipPathPrefixes() != null && CONFIG.getSkipPathPrefixes().stream().anyMatch(s -> reqPath.startsWith(s))) {
            if (LOG.isDebugEnabled()) LOG.debug("ValidatorMiddleware.execute ends with skipped path " + reqPath);
            return successMiddlewareStatus();
        }
        final NormalisedPath requestPath = new ApiNormalisedPath(reqPath, OpenApiMiddleware.getBasePath(reqPath));
        OpenApiOperation openApiOperation = null;
        Map<String, Object> auditInfo = (Map<String, Object>)exchange.getRequestAttachment(AUDIT_ATTACHMENT_KEY);
        if(auditInfo != null) {
            openApiOperation = (OpenApiOperation)auditInfo.get(Constants.OPENAPI_OPERATION_STRING);
        }
        if(openApiOperation == null) {
            if (LOG.isDebugEnabled()) LOG.debug("ValidatorMiddleware.execute ends with an error.");
            return new Status(STATUS_MISSING_OPENAPI_OPERATION);
        }
        Status status = requestValidator.validateRequest(requestPath, exchange, openApiOperation);
        if(status != null) {
            if (LOG.isDebugEnabled()) LOG.debug("ValidatorHandler.handleRequest ends with an error.");
            return status;
        }
        return successMiddlewareStatus();
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

    }

    @Override
    public boolean isContinueOnFailure() {
        return false;
    }

    @Override
    public boolean isAudited() {
        return false;
    }

    @Override
    public boolean isAsynchronous() {
        return false;
    }

}
