package com.networknt.aws.lambda.handler.middleware.transformer;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.networknt.aws.lambda.LightLambdaExchange;
import com.networknt.config.Config;
import com.networknt.restrans.ResponseTransformerConfig;
import com.networknt.rule.RuleConstants;
import com.networknt.status.Status;
import com.networknt.utility.ConfigUtils;
import com.networknt.utility.MapUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.networknt.aws.lambda.handler.middleware.audit.AuditMiddleware.AUDIT_ATTACHMENT_KEY;

public class ResponseTransformerMiddleware extends AbstractTransformerMiddleware {
    private static ResponseTransformerConfig CONFIG;
    private static final Logger LOG = LoggerFactory.getLogger(ResponseTransformerMiddleware.class);
    private static final String RESPONSE_TRANSFORM = "res-tra";
    private static final String RESPONSE_HEADERS = "responseHeaders";
    private static final String REQUEST_HEADERS = "requestHeaders";
    private static final String RESPONSE_BODY = "responseBody";
    private static final String REMOVE = "remove";
    private static final String UPDATE = "update";
    private static final String QUERY_PARAMETERS = "queryParameters";
    private static final String PATH_PARAMETERS = "pathParameters";
    private static final String METHOD = "method";
    private static final String REQUEST_PATH = "requestPath";
    private static final String POST = "post";
    private static final String PUT = "put";
    private static final String PATCH = "patch";
    private static final String REQUEST_BODY = "requestBody";
    private static final String AUDIT_INFO = "auditInfo";
    private static final String STATUS_CODE = "statusCode";


    public ResponseTransformerMiddleware() {
        super();
        CONFIG = ResponseTransformerConfig.load();
        LOG.info("ResponseTransformerMiddleware is constructed");
    }

    /**
     * Constructor with configuration for testing purpose only
     * @param cfg ResponseTransformerConfig
     */
    public ResponseTransformerMiddleware(ResponseTransformerConfig cfg) {
        super();
        CONFIG = cfg;
        LOG.info("ResponseTransformerMiddleware is constructed");
    }


    @Override
    public Status execute(LightLambdaExchange exchange) {
        if (LOG.isDebugEnabled()) LOG.trace("ResponseTransformerMiddleware.execute starts.");
        APIGatewayProxyRequestEvent readOnlyRequest = exchange.getReadOnlyRequest();
        String requestPath = readOnlyRequest.getPath();
        if (CONFIG.getAppliedPathPrefixes() != null && CONFIG.getAppliedPathPrefixes().stream().anyMatch(requestPath::startsWith)) {
            String responseBody = exchange.getResponse().getBody();
            if (LOG.isTraceEnabled()) LOG.trace("original response body = " + responseBody);

            // call the rule engine to transform the response body and response headers. The input contains all the request
            // and response elements.
            String method = readOnlyRequest.getHttpMethod();
            Map<String, Object> auditInfo = (Map<String, Object>)exchange.getAttachment(AUDIT_ATTACHMENT_KEY);
            Map<String, Object> objMap = new HashMap<>();
            objMap.put(REQUEST_HEADERS,  readOnlyRequest.getHeaders());
            objMap.put(RESPONSE_HEADERS, readOnlyRequest.getHeaders());
            objMap.put(QUERY_PARAMETERS, readOnlyRequest.getQueryStringParameters());
            objMap.put(PATH_PARAMETERS,  readOnlyRequest.getPathParameters());
            objMap.put(METHOD, method);
            objMap.put(REQUEST_PATH, readOnlyRequest.getPath());
            if (method.toString().equalsIgnoreCase(POST)
                    || method.toString().equalsIgnoreCase(PUT)
                    || method.toString().equalsIgnoreCase(PATCH)) {
                String requestBody = readOnlyRequest.getBody();
                objMap.put(REQUEST_BODY, requestBody);
            }
            if (responseBody != null) {
                objMap.put(RESPONSE_BODY, responseBody);
            }
            objMap.put(AUDIT_INFO, auditInfo);
            objMap.put(STATUS_CODE, exchange.getStatusCode());

            // need to get the rule/rules to execute from the RuleLoaderStartupHook. First, get the endpoint.
            String endpoint, serviceEntry = null;
            if (auditInfo != null) {
                if (LOG.isDebugEnabled())
                    LOG.debug("auditInfo exists. Grab endpoint from it.");
                endpoint = (String) auditInfo.get("endpoint");
            } else {
                if (LOG.isDebugEnabled())
                    LOG.debug("auditInfo is NULL. Grab endpoint from exchange.");
                endpoint = exchange.getRequest().getPath() + "@" + method.toLowerCase();
            }

            // checked the RuleLoaderStartupHook to ensure it is loaded. If not, return an error to the caller.
            if (endpointRulesMap == null) {
                LOG.error("endpointRules is null");
            }

            // Grab ServiceEntry from config
            endpoint = ConfigUtils.toInternalKey(method.toLowerCase(), readOnlyRequest.getPath());
            if(LOG.isDebugEnabled()) LOG.debug("request endpoint: " + endpoint);
            serviceEntry = ConfigUtils.findServiceEntry(method.toLowerCase(), readOnlyRequest.getPath(), endpointRulesMap);
            if(LOG.isDebugEnabled()) LOG.debug("request serviceEntry: " + serviceEntry);

            // get the rules (maybe multiple) based on the endpoint.
            Map<String, List> endpointRules = (Map<String, List>) endpointRulesMap.get(serviceEntry);
            if (endpointRules == null) {
                if (LOG.isDebugEnabled())
                    LOG.debug("endpointRules iS NULL");
                // if there is no endpoint rules, we will not do any transformation.
                return successMiddlewareStatus();
            } else {
                if (LOG.isDebugEnabled())
                    LOG.debug("endpointRules: {}", endpointRules.get(RESPONSE_TRANSFORM).size());
            }

            boolean finalResult = true;
            List<String> responseTransformRules = endpointRules.get(RESPONSE_TRANSFORM);
            Map<String, Object> result = null;
            // iterate the rules and execute them in sequence. Break only if one rule is successful.
            for(String ruleId: responseTransformRules) {
                try {
                    result = ruleEngine.executeRule(ruleId, objMap);
                    boolean res = (Boolean) result.get(RuleConstants.RESULT);
                    if (!res) {
                        finalResult = false;
                        break;
                    }
                } catch (Exception e) {
                    LOG.error("Exception:", e);
                    finalResult = false;
                    break;
                }
            }
            if(finalResult) {
                for (Map.Entry<String, Object> entry : result.entrySet()) {
                    if (LOG.isTraceEnabled()) LOG.trace("key = {} value = {}", entry.getKey(), entry.getValue());

                    // you can only update the response headers and response body in the transformation.
                    switch (entry.getKey()) {
                        case RESPONSE_HEADERS:
                            // if responseHeaders object is null, ignore it.
                            Map<String, Object> responseHeaders = (Map) result.get(RESPONSE_HEADERS);
                            if (responseHeaders != null) {
                                // manipulate the response headers.
                                List<String> removeList = (List) responseHeaders.get(REMOVE);
                                if (removeList != null) {
                                    removeList.forEach(s -> MapUtil.delValueIgnoreCase(exchange.getResponse().getHeaders(), s));
                                }
                                Map<String, Object> updateMap = (Map) responseHeaders.get(UPDATE);
                                if (updateMap != null) {
                                    updateMap.forEach((k, v) -> exchange.getResponse().getHeaders().put(k, (String) v));
                                }
                            }
                            break;
                        case RESPONSE_BODY:
                            responseBody = (String) result.get(RESPONSE_BODY);
                            if (responseBody != null) {
                                exchange.getResponse().setBody(responseBody);
                            }
                            break;
                    }
                }
            }
        }
        if (LOG.isDebugEnabled()) LOG.trace("ResponseTransformerInterceptor.handleRequest ends.");
        return successMiddlewareStatus();
    }

    private Map<String, Object> createExchangeInfoMap(LightLambdaExchange exchange, String method, String responseBody, Map<String, Object> auditInfo) {
        Map<String, Object> objMap = new HashMap<>();
        objMap.put(REQUEST_HEADERS,  exchange.getRequest().getHeaders());
        objMap.put(RESPONSE_HEADERS, exchange.getResponse().getHeaders());
        objMap.put(QUERY_PARAMETERS, exchange.getRequest().getQueryStringParameters());
        objMap.put(PATH_PARAMETERS,  exchange.getRequest().getPathParameters());
        objMap.put(METHOD, method);
        objMap.put(REQUEST_PATH, exchange.getRequest().getPath());
        if (method.toString().equalsIgnoreCase(POST)
                || method.toString().equalsIgnoreCase(PUT)
                || method.toString().equalsIgnoreCase(PATCH)) {
            String requestBody = exchange.getRequest().getBody();
            objMap.put(REQUEST_BODY, requestBody);
        }
        if (responseBody != null) {
            objMap.put(RESPONSE_BODY, responseBody);
        }
        objMap.put(AUDIT_INFO, auditInfo);
        objMap.put(STATUS_CODE, exchange.getStatusCode());
        return objMap;
    }

    @Override
    public boolean isEnabled() {
        return CONFIG.isEnabled();
    }

    /**
     * All response chain middleware handler should override this method and return true.
     * @return boolean to indicate if the middleware handler is a response middleware handler.
     */
    @Override
    public boolean isResponseMiddleware() {
        return true;
    }
}
