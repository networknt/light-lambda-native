package com.networknt.aws.lambda.handler.middleware.specification;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.networknt.aws.lambda.handler.MiddlewareHandler;
import com.networknt.aws.lambda.handler.middleware.LightLambdaExchange;
import com.networknt.config.Config;
import com.networknt.oas.model.Operation;
import com.networknt.oas.model.Path;
import com.networknt.openapi.*;
import com.networknt.service.SingletonServiceFactory;
import com.networknt.status.Status;
import com.networknt.utility.Constants;
import com.networknt.utility.ModuleRegistry;
import org.apache.commons.lang3.NotImplementedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static com.networknt.aws.lambda.handler.middleware.audit.AuditMiddleware.AUDIT_ATTACHMENT_KEY;

public class OpenApiMiddleware implements MiddlewareHandler {

    public static final String OPENAPI_CONFIG_NAME = "openapi-validator";
    private static final Logger LOG = LoggerFactory.getLogger(OpenApiMiddleware.class);
    private static final String STATUS_METHOD_NOT_ALLOWED = "ERR10008";
    private static final String CONFIG_NAME = "openapi";
    static ValidatorConfig CONFIG = ValidatorConfig.load();
    private static final String SPEC_INJECT = "openapi-inject";

    public static OpenApiHelper helper;

    public OpenApiMiddleware() {
        if (LOG.isInfoEnabled()) LOG.info("OpenApiMiddleware is constructed");
        Map<String, Object> inject = Config.getInstance().getJsonMapConfig(SPEC_INJECT);
        Map<String, Object> openapi = Config.getInstance().getJsonMapConfigNoCache(CONFIG_NAME);
        validateSpec(openapi, inject, "openapi.yaml");
        OpenApiHelper.merge(openapi, inject);
        try {
            helper = new OpenApiHelper(Config.getInstance().getMapper().writeValueAsString(openapi));
        } catch (JsonProcessingException e) {
            LOG.error("merge specification failed");
            throw new RuntimeException("merge specification failed");
        }
    }

    @Override
    public Status execute(LightLambdaExchange exchange) throws InterruptedException {

        LOG.debug("OpenAPI Specification Time - Start: {}", System.currentTimeMillis());

        if (LOG.isDebugEnabled())
            LOG.debug("OpenApiMiddleware.executeMiddleware starts.");

        final NormalisedPath requestPath = new ApiNormalisedPath(exchange.getRequest().getPath(), helper.basePath);
        final Optional<NormalisedPath> maybeApiPath = helper.findMatchingApiPath(requestPath);

        final NormalisedPath openApiPathString = maybeApiPath.get();
        final Path path = helper.openApi3.getPath(openApiPathString.original());

        final String httpMethod = exchange.getRequest().getHttpMethod().toLowerCase();
        final Operation operation = path.getOperation(httpMethod);

        if (operation == null) {
            return new Status(STATUS_METHOD_NOT_ALLOWED, httpMethod, openApiPathString.normalised());
        }

        // This handler can identify the openApiOperation and endpoint only. Other info will be added by JwtVerifyHandler.
        final OpenApiOperation openApiOperation = new OpenApiOperation(openApiPathString, path, httpMethod, operation);

        String endpoint = openApiPathString.normalised() + "@" + httpMethod.toLowerCase();
        Map<String, Object> auditInfo = (exchange.getRequestAttachment(AUDIT_ATTACHMENT_KEY) != null) ? (Map<String, Object>) exchange.getRequestAttachment(AUDIT_ATTACHMENT_KEY) : new HashMap<>();
        auditInfo.put(Constants.ENDPOINT_STRING, endpoint);
        auditInfo.put(Constants.OPENAPI_OPERATION_STRING, openApiOperation);
        exchange.addRequestAttachment(AUDIT_ATTACHMENT_KEY, auditInfo);

        if (LOG.isDebugEnabled())
            LOG.debug("OpenApiMiddleware.executeMiddleware ends.");

        LOG.debug("OpenAPI Specification Time - Finish: {}", System.currentTimeMillis());

        return successMiddlewareStatus();
    }

    /**
     * Validates the injectMap and openapiMap.Throws an exception if not valid.
     *
     * @param openapiMap - openapiSpec
     * @param openapiInjectMap - inject map
     * @param specName - name of the openapiSpec
     */
    private void validateSpec(Map<String, Object> openapiMap, Map<String, Object> openapiInjectMap, String specName) {
        InjectableSpecValidator validator = SingletonServiceFactory.getBean(InjectableSpecValidator.class);
        if (validator == null) {
            validator = new DefaultInjectableSpecValidator();
        }

        if (!validator.isValid(openapiMap, openapiInjectMap)) {
            LOG.error("the original spec {} and injected spec has error, please check the validator {}", specName, validator.getClass().getName());
            throw new RuntimeException("inject spec error for " + specName);
        }
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
                OPENAPI_CONFIG_NAME,
                OpenApiMiddleware.class.getName(),
                Config.getNoneDecryptedInstance().getJsonMapConfigNoCache(OPENAPI_CONFIG_NAME),
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
