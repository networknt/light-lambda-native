package com.networknt.aws.lambda.handler.middleware.validator;

import com.networknt.aws.lambda.handler.MiddlewareHandler;
import com.networknt.aws.lambda.LightLambdaExchange;
import com.networknt.aws.lambda.handler.middleware.specification.OpenApiMiddleware;
import com.networknt.aws.lambda.utility.HeaderKey;
import com.networknt.aws.lambda.utility.HeaderValue;
import com.networknt.aws.lambda.validator.RequestValidator;
import com.networknt.aws.lambda.validator.SchemaValidator;
import com.networknt.config.Config;
import com.networknt.openapi.ApiNormalisedPath;
import com.networknt.openapi.NormalisedPath;
import com.networknt.openapi.OpenApiOperation;
import com.networknt.openapi.ValidatorConfig;
import com.networknt.status.Status;
import com.networknt.utility.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

import static com.networknt.aws.lambda.handler.middleware.audit.AuditMiddleware.AUDIT_ATTACHMENT_KEY;

public class ValidatorMiddleware implements MiddlewareHandler {

    private static final Logger LOG = LoggerFactory.getLogger(ValidatorMiddleware.class);
    static final String STATUS_MISSING_OPENAPI_OPERATION = "ERR10012";

    RequestValidator requestValidator;

    public static ValidatorConfig CONFIG;

    public ValidatorMiddleware() {
        if (LOG.isInfoEnabled()) LOG.info("ValidatorMiddleware is constructed");
        CONFIG = ValidatorConfig.load();
        final SchemaValidator schemaValidator = new SchemaValidator(OpenApiMiddleware.helper.openApi3);
        this.requestValidator = new RequestValidator(schemaValidator, CONFIG);
    }

    @Override
    public Status execute(final LightLambdaExchange exchange) {
        if(LOG.isTraceEnabled()) LOG.trace("ValidatorMiddleware.execute starts.");
        if (!CONFIG.isEnabled()) return disabledMiddlewareStatus();
        LOG.debug("ValidatorMiddleware.execute starts at {}.", System.currentTimeMillis());
        String reqPath = exchange.getRequest().getPath();
        // if request path is in the skipPathPrefixes in the config, call the next handler directly to skip the validation.
        if (CONFIG.getSkipPathPrefixes() != null && CONFIG.getSkipPathPrefixes().stream().anyMatch(s -> reqPath.startsWith(s))) {
            if (LOG.isDebugEnabled()) LOG.debug("ValidatorMiddleware.execute ends with skipped path " + reqPath);
            return successMiddlewareStatus();
        }
        final NormalisedPath requestPath = new ApiNormalisedPath(reqPath, OpenApiMiddleware.getBasePath(reqPath));
        OpenApiOperation openApiOperation = null;
        Map<String, Object> auditInfo = (Map<String, Object>)exchange.getAttachment(AUDIT_ATTACHMENT_KEY);
        if(auditInfo != null) {
            openApiOperation = (OpenApiOperation)auditInfo.get(Constants.OPENAPI_OPERATION_STRING);
        }
        if(openApiOperation == null) {
            if (LOG.isDebugEnabled()) LOG.debug("ValidatorMiddleware.execute ends with an error.");
            return new Status(STATUS_MISSING_OPENAPI_OPERATION);
        }
        Status status = requestValidator.validateRequest(requestPath, exchange.getRequest(), openApiOperation);
        if(status != null) {
            if (LOG.isDebugEnabled()) LOG.debug("ValidatorHandler.handleRequest ends with an error.");
            return status;
        }
        return successMiddlewareStatus();
    }

    private boolean isApplicationJsonContentType(Map<String, String> headers) {
        return headers.containsKey(HeaderKey.CONTENT_TYPE)
                && headers.get(HeaderKey.CONTENT_TYPE).equals(HeaderValue.APPLICATION_JSON);
    }

    @Override
    public boolean isEnabled() {
        return CONFIG.isEnabled();
    }

}
