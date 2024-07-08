package com.networknt.aws.lambda.handler.middleware.audit;

import com.networknt.audit.AuditConfig;
import com.networknt.aws.lambda.handler.MiddlewareHandler;
import com.networknt.aws.lambda.LightLambdaExchange;
import com.networknt.aws.lambda.app.LambdaAppConfig;
import com.networknt.aws.lambda.utility.HeaderKey;
import com.networknt.config.Config;
import com.networknt.config.JsonMapper;
import com.networknt.mask.Mask;
import com.networknt.status.Status;
import com.networknt.utility.MapUtil;
import com.networknt.utility.ModuleRegistry;
import com.networknt.utility.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * This middleware is responsible for auditing the request and response. it will wire in the end of the response chain and output the
 * information collected in the audit attachment in the exchange to an audit log file. There are several middleware handlers will be
 * responsible to update the attachment in the exchange.
 *
 */
public class AuditMiddleware implements MiddlewareHandler {
    private static final Logger LOG = LoggerFactory.getLogger(AuditMiddleware.class);
    private static AuditConfig CONFIG;
    public static final LightLambdaExchange.Attachable<AuditMiddleware> AUDIT_ATTACHMENT_KEY = LightLambdaExchange.Attachable.createAttachable(AuditMiddleware.class);
    static final String STATUS_CODE = "statusCode";
    static final String RESPONSE_TIME = "responseTime";
    static final String TIMESTAMP = "timestamp";
    static final String MASK_KEY = "audit";
    static final String REQUEST_BODY_KEY = "requestBody";
    static final String RESPONSE_BODY_KEY = "responseBody";
    static final String QUERY_PARAMETERS_KEY = "queryParameters";
    static final String PATH_PARAMETERS_KEY = "pathParameters";
    static final String REQUEST_COOKIES_KEY = "requestCookies";
    static final String SERVICE_ID_KEY = "serviceId";
    static final String INVALID_CONFIG_VALUE_CODE = "ERR10060";

    private String serviceId;

    private DateTimeFormatter DATE_TIME_FORMATTER;

    public AuditMiddleware() {
        if (LOG.isInfoEnabled()) LOG.info("AuditMiddleware is constructed.");
        CONFIG = AuditConfig.load();
        // get the serviceId from the proxy config
        LambdaAppConfig proxyConfig = (LambdaAppConfig) Config.getInstance().getJsonObjectConfig(LambdaAppConfig.CONFIG_NAME, LambdaAppConfig.class);
        serviceId = proxyConfig.getLambdaAppId();
        String timestampFormat = CONFIG.getTimestampFormat();
        if (!StringUtils.isBlank(timestampFormat)) {
            try {
                DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern(timestampFormat)
                        .withZone(ZoneId.systemDefault());
            } catch (IllegalArgumentException e) {
                LOG.error(new Status(INVALID_CONFIG_VALUE_CODE, timestampFormat, "timestampFormat", "audit.yml").toString());
            }
        }
    }

    @Override
    public Status execute(LightLambdaExchange exchange) {
        if(LOG.isDebugEnabled()) LOG.debug("AuditMiddleware.execute starts.");
        // as there is no way to write a separate audit log file in Lambda, we will skip this handler.
        Map<String, Object> auditInfo = (Map<String, Object>)exchange.getAttachment(AUDIT_ATTACHMENT_KEY);
        Map<String, Object> auditMap = new LinkedHashMap<>();
        final long start = System.currentTimeMillis();
        // add audit timestamp
        auditMap.put(TIMESTAMP, DATE_TIME_FORMATTER == null ? System.currentTimeMillis() : DATE_TIME_FORMATTER.format(Instant.now()));

        // dump audit info fields according to config
        boolean needAuditData = auditInfo != null && CONFIG.hasAuditList();
        if (needAuditData) {
            auditFields(auditInfo, auditMap);
        }

        // dump request header, request body, path parameters, query parameters and request cookies according to config
        auditRequest(exchange, auditMap, CONFIG);

        // dump serviceId from server.yml
        if (CONFIG.hasAuditList() && CONFIG.getAuditList().contains(SERVICE_ID_KEY)) {
            auditServiceId(auditMap);
        }
        if(CONFIG.isStatusCode() || CONFIG.isResponseTime()) {
            exchange.addResponseCompleteListener(finalExchange -> {
                if (CONFIG.isStatusCode()) {
                    auditMap.put(STATUS_CODE, finalExchange.getStatusCode());
                }
                if (CONFIG.isResponseTime()) {
                    auditMap.put(RESPONSE_TIME, System.currentTimeMillis() - start);
                }

                Map<String, Object> auditInfo1 = (Map<String, Object>)exchange.getAttachment(AUDIT_ATTACHMENT_KEY);

                if (auditInfo1 != null && CONFIG.getAuditList() != null) {
                    for (String name : CONFIG.getAuditList()) {
                        Object object = auditInfo1.get(name);
                        if(object != null) {
                            auditMap.put(name, object);
                        }
                    }
                }
                // audit the response body.
                if(CONFIG.getAuditList() != null && CONFIG.getAuditList().contains(RESPONSE_BODY_KEY)) {
                    auditResponseBody(exchange, auditMap);
                }

                if (CONFIG.isAuditOnError()) {
                    if (finalExchange.getStatusCode() >= 400)
                        logAuditMsg(JsonMapper.toJson(auditMap));
                } else {
                    logAuditMsg(JsonMapper.toJson(auditMap));
                }
            });
        } else {
            logAuditMsg(JsonMapper.toJson(auditMap));
        }
        if(LOG.isDebugEnabled()) LOG.debug("AuditMiddleware.execute ends.");
        return successMiddlewareStatus();
    }

    private void logAuditMsg(String auditMsg) {
        CONFIG.getAuditFunc().accept(auditMsg);
    }

    private void auditFields(Map<String, Object> auditInfo, Map<String, Object> auditMap) {
        for (String name : CONFIG.getAuditList()) {
            Object value = auditInfo.get(name);
            boolean needApplyMask = CONFIG.isMask() && value instanceof String;
            auditMap.put(name, needApplyMask ? Mask.maskRegex((String) value, MASK_KEY, name) : value);
        }
    }

    private void auditRequest(LightLambdaExchange exchange, Map<String, Object> auditMap, AuditConfig config) {
        if (config.hasHeaderList()) {
            auditHeader(exchange, auditMap);
        }
        if (!config.hasAuditList()) {
            return;
        }
        for (String key : config.getAuditList()) {
            switch (key) {
                case REQUEST_BODY_KEY:
                    auditRequestBody(exchange, auditMap);
                    break;
                case QUERY_PARAMETERS_KEY:
                    auditQueryParameters(exchange, auditMap);
                    break;
                case PATH_PARAMETERS_KEY:
                    auditPathParameters(exchange, auditMap);
                    break;
            }
        }
    }

    private void auditHeader(LightLambdaExchange exchange, Map<String, Object> auditMap) {
        for (String name : CONFIG.getHeaderList()) {
            Optional<String> optionalValue = MapUtil.getValueIgnoreCase(exchange.getRequest().getHeaders(), name);
            if(optionalValue.isEmpty()) {
                if(LOG.isTraceEnabled()) LOG.trace("header name = {} header value is null", name);
                continue;
            }
            if(LOG.isTraceEnabled()) LOG.trace("header name = {} header value = {}", name, optionalValue.get());
            auditMap.put(name, CONFIG.isMask() ? Mask.maskRegex(optionalValue.get(), "requestHeader", name) : optionalValue.get());
        }
    }

    // Audit request body automatically if body handler enabled
    private void auditRequestBody(LightLambdaExchange exchange, Map<String, Object> auditMap) {
        String requestBodyString = exchange.getRequest().getBody();
        // Mask requestBody json string if mask enabled
        if (requestBodyString != null && !requestBodyString.isEmpty()) {
            Optional<String> optionalContentType = MapUtil.getValueIgnoreCase(exchange.getRequest().getHeaders(), HeaderKey.CONTENT_TYPE);
            if(optionalContentType.isPresent()) {
                String contentType = optionalContentType.get();
                if(contentType.startsWith("application/json")) {
                    if(CONFIG.isMask()) requestBodyString = Mask.maskJson(requestBodyString, REQUEST_BODY_KEY);
                } else if(contentType.startsWith("text") || contentType.startsWith("application/xml")) {
                    if(CONFIG.isMask()) requestBodyString = Mask.maskString(requestBodyString, REQUEST_BODY_KEY);
                } else {
                    LOG.error("Incorrect request content type " + contentType);
                }
            }
            if(requestBodyString.length() > CONFIG.getRequestBodyMaxSize()) {
                requestBodyString = requestBodyString.substring(0, CONFIG.getRequestBodyMaxSize());
            }
            auditMap.put(REQUEST_BODY_KEY, requestBodyString);
        }
    }

    // Audit response body
    private void auditResponseBody(LightLambdaExchange exchange, Map<String, Object> auditMap) {
        String responseBodyString = exchange.getResponse().getBody();
        // mask the response body json string if mask is enabled.
        if(responseBodyString != null && !responseBodyString.isEmpty()) {
            Optional<String> optionalContentType = MapUtil.getValueIgnoreCase(exchange.getResponse().getHeaders(), HeaderKey.CONTENT_TYPE);
            if(optionalContentType.isPresent()) {
                String contentType = optionalContentType.orElse(null);
                if(contentType.startsWith("application/json")) {
                    if(CONFIG.isMask()) responseBodyString =Mask.maskJson(responseBodyString, RESPONSE_BODY_KEY);
                } else if(contentType.startsWith("text") || contentType.startsWith("application/xml")) {
                    if(CONFIG.isMask()) responseBodyString = Mask.maskString(responseBodyString, RESPONSE_BODY_KEY);
                } else {
                    LOG.error("Incorrect response content type " + contentType);
                }
            }
            if(responseBodyString.length() > CONFIG.getResponseBodyMaxSize()) {
                responseBodyString = responseBodyString.substring(0, CONFIG.getResponseBodyMaxSize());
            }
            auditMap.put(RESPONSE_BODY_KEY, responseBodyString);
        }
    }

    // Audit query parameters
    private void auditQueryParameters(LightLambdaExchange exchange, Map<String, Object> auditMap) {
        Map<String, String> res = new HashMap<>();
        Map<String, String> queryParameters = exchange.getRequest().getQueryStringParameters();
        if (queryParameters != null && !queryParameters.isEmpty()) {
            for (String query : queryParameters.keySet()) {
                String value = queryParameters.get(query);
                String mask = CONFIG.isMask() ? Mask.maskRegex(value, QUERY_PARAMETERS_KEY, query) : value;
                res.put(query, mask);
            }
            auditMap.put(QUERY_PARAMETERS_KEY, res.toString());
        }
    }

    private void auditPathParameters(LightLambdaExchange exchange, Map<String, Object> auditMap) {
        Map<String, String> res = new HashMap<>();
        Map<String, String> pathParameters = exchange.getRequest().getPathParameters();
        if (pathParameters != null && !pathParameters.isEmpty()) {
            for (String name : pathParameters.keySet()) {
                String value = pathParameters.get(name);
                String mask = CONFIG.isMask() ? Mask.maskRegex(value, PATH_PARAMETERS_KEY, name) : value;
                res.put(name, mask);
            }
            auditMap.put(PATH_PARAMETERS_KEY, res.toString());
        }
    }

    private void auditServiceId(Map<String, Object> auditMap) {
        if (!StringUtils.isBlank(serviceId)) {
            auditMap.put(SERVICE_ID_KEY, serviceId);
        }
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
                AuditConfig.CONFIG_NAME,
                AuditMiddleware.class.getName(),
                Config.getNoneDecryptedInstance().getJsonMapConfigNoCache(AuditConfig.CONFIG_NAME),
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
