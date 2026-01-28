package com.networknt.aws.lambda.handler.middleware.transformer;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.networknt.aws.lambda.LightLambdaExchange;
import com.networknt.aws.lambda.utility.HeaderKey;
import com.networknt.aws.lambda.utility.HeaderValue;
import com.networknt.config.JsonMapper;
import com.networknt.reqtrans.RequestTransformerConfig;
import com.networknt.rule.*;
import com.networknt.status.Status;
import com.networknt.utility.ConfigUtils;
import com.networknt.utility.MapUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.networknt.aws.lambda.handler.middleware.audit.AuditMiddleware.AUDIT_ATTACHMENT_KEY;

public class RequestTransformerMiddleware extends AbstractTransformerMiddleware {
    private static final Logger LOG = LoggerFactory.getLogger(RequestTransformerMiddleware.class);
    static final String REQUEST_TRANSFORM = "req-tra";

    private static RequestTransformerConfig CONFIG;
    public RequestTransformerMiddleware() {
        super();
        CONFIG = RequestTransformerConfig.load();
        LOG.info("RequestTransformerMiddleware is constructed");
    }

    /**
     * Constructor with configuration for testing purpose only
     * @param cfg RequestTransformerConfig
     */
    public RequestTransformerMiddleware(RequestTransformerConfig cfg) {
        super();
        CONFIG = cfg;
        LOG.info("RequestTransformerMiddleware is constructed");
    }

    @Override
    public Status execute(LightLambdaExchange exchange) {
        if(LOG.isDebugEnabled()) LOG.trace("RequestTransformerMiddleware.execute starts.");
        String requestPath = exchange.getRequest().getPath();
        if (CONFIG.getAppliedPathPrefixes() != null && CONFIG.getAppliedPathPrefixes().stream().anyMatch(s -> requestPath.startsWith(s))) {
            String method = exchange.getRequest().getHttpMethod();
            Map<String, Object> auditInfo = (Map<String, Object>)exchange.getAttachment(AUDIT_ATTACHMENT_KEY);
            // checked the RuleLoaderStartupHook to ensure it is loaded. If not, return an error to the caller.
            if(endpointRulesMap == null) {
                LOG.error("endpointRules is null");
            }
            // need to get the rule/rules to execute from the RuleLoaderStartupHook. First, get the endpoint.
            String endpoint, serviceEntry = null;
            // Grab ServiceEntry from config
            endpoint = ConfigUtils.toInternalKey(method.toLowerCase(), exchange.getRequest().getPath());
            if(LOG.isDebugEnabled()) LOG.debug("request endpoint: " + endpoint);
            serviceEntry = ConfigUtils.findServiceEntry(method.toLowerCase(), exchange.getRequest().getPath(), endpointRulesMap);
            if(LOG.isDebugEnabled()) LOG.debug("request serviceEntry: " + serviceEntry);

            // get the rules (maybe multiple) based on the endpoint.
            Map<String, List> endpointRules = (Map<String, List>)endpointRulesMap.get(serviceEntry);
            if(endpointRules == null) {
                if(LOG.isDebugEnabled()) LOG.debug("endpointRules iS NULL");
            } else {
                if(LOG.isDebugEnabled()) LOG.debug("endpointRules: " + endpointRules.get(REQUEST_TRANSFORM).size());
            }
            if(endpointRules != null) {
                List<String> requestTransformRules = endpointRules.get(REQUEST_TRANSFORM);
                if(requestTransformRules != null) {
                    boolean finalResult = true;
                    // call the rule engine to transform the request metadata or body. The input contains all the request elements
                    Map<String, Object> objMap = new HashMap<>();
                    objMap.put("auditInfo", auditInfo);
                    objMap.put("requestHeaders", exchange.getRequest().getHeaders());
                    objMap.put("queryParameters", exchange.getRequest().getQueryStringParameters());
                    objMap.put("pathParameters", exchange.getRequest().getPathParameters());
                    objMap.put("method", method);
                    objMap.put("requestPath", exchange.getRequest().getPath());
                    if ((method.equalsIgnoreCase("post") || method.equalsIgnoreCase("put") || method.equalsIgnoreCase("patch")) && !exchange.isRequestComplete()) {
                        // This object contains the reference to the request data buffer. Any modification done to this will be reflected in the request. We only want to transform the request body if
                        // the body is json or xml which is text based. If it is binary, we will not touch it. We first try to get the injected attachment from the RequestBodyInterceptor. However, if
                        // the RequestBodyInterceptor is not configured run first, we need to get the buffer from the exchange directly.
                        String bodyString = exchange.getRequest().getBody();
                        if(bodyString != null) {
                            objMap.put("requestBody", bodyString);
                        }
                    }
                    Map<String, Object> result = null;
                    // iterate the rules and execute them in sequence. Break only if one rule is successful.
                    if(LOG.isDebugEnabled()) LOG.debug("requestTransformRules list count: " + requestTransformRules.size());
                    for(String ruleId: requestTransformRules) {
                        if(LOG.isDebugEnabled()) LOG.debug("ruleId found: {}", ruleId);
                        try {
                            result = ruleEngine.executeRule(ruleId, objMap);
                            boolean res = (Boolean) result.get(RuleConstants.RESULT);
                            if (LOG.isDebugEnabled() && res) LOG.debug("ruleID result is true");
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
                        for(Map.Entry<String, Object> entry: result.entrySet()) {
                            if(LOG.isTraceEnabled()) LOG.trace("key = " + entry.getKey() + " value = " + entry.getValue());
                            // you can only update the response headers and response body in the transformation.
                            switch(entry.getKey()) {
                                case "requestPath":
                                    String reqPath = (String)result.get("requestPath");
                                    exchange.getRequest().setPath(reqPath);
                                    if(LOG.isTraceEnabled()) LOG.trace("requestPath is changed to " + reqPath);
                                    break;
                                case "queryString":
                                    // we have pass the queryParameters to the rule engine, the plugin developer should use that
                                    // to add or remove entries, and then convert the map to a json string.
                                    String queryString = (String)result.get("queryString");
                                    if(LOG.isTraceEnabled()) LOG.trace("queryString = " + queryString);
                                    if(queryString != null) {
                                        Map<String, Object> queryParameters = JsonMapper.string2Map(queryString);
                                        exchange.getRequest().setQueryStringParameters(convertMapValueToString(queryParameters));
                                    }
                                    break;
                                case "requestHeaders":
                                    // if requestHeaders object is null, ignore it.
                                    Map<String, Object> requestHeaders = (Map<String, Object>)result.get("requestHeaders");
                                    if(requestHeaders != null && requestHeaders.size() > 0) {
                                        // manipulate the request headers.
                                        List<String> removeList = (List)requestHeaders.get("remove");
                                        if(removeList != null) {
                                            removeList.forEach(s -> {
                                                if(LOG.isTraceEnabled()) LOG.trace("removing request header: " + s);
                                                MapUtil.delValueIgnoreCase(exchange.getRequest().getHeaders(), s);
                                            });
                                        }
                                        Map<String, Object> updateMap = (Map)requestHeaders.get("update");
                                        if(updateMap != null) {
                                            updateMap.forEach((k, v) -> {
                                                if(LOG.isTraceEnabled()) LOG.trace("updating request header: " + k + " value: " + v);
                                                exchange.getRequest().getHeaders().put(k, (String)v);
                                            });
                                        }
                                    }
                                    break;
                                case "requestBody":
                                    String requestBody = (String)result.get("requestBody");
                                    if(LOG.isTraceEnabled()) LOG.trace("requestBody = " + requestBody);
                                    exchange.getRequest().setBody(requestBody);
                                    break;
                                case "responseBody":
                                    String responseBody = (String)result.get("responseBody");
                                    if(LOG.isTraceEnabled()) LOG.trace("responseBody = " + responseBody);
                                    // Send the response body and return immediately just like the admin endpoSint.
                                    var responseEvent = new APIGatewayProxyResponseEvent();
                                    var headers = new HashMap<String, String>();
                                    headers.put(HeaderKey.CONTENT_TYPE, HeaderValue.APPLICATION_JSON);
                                    responseEvent.setHeaders(headers);
                                    responseEvent.setStatusCode(Objects.requireNonNullElse((Integer)result.get("statusCode"), 200));
                                    responseEvent.setBody(responseBody);
                                    exchange.setInitialResponse(responseEvent);
                                    break;
                                case "validationError":
                                    // If the rule engine returns any validationError entry, the message should be able to
                                    // converted back to Status
                                    String errorMessage = (String)result.get("errorMessage");
                                    if(LOG.isTraceEnabled()) LOG.trace("Entry key validationError with errorMessage {} ", errorMessage);
                                    // we are expecting the errorMessage can be converted to Status object.
                                    return JsonMapper.fromJson(errorMessage, Status.class);
                            }
                        }
                    }
                }
            }
        }
        if(LOG.isDebugEnabled()) LOG.trace("RequestTransformerMiddleware.execute ends.");
        return successMiddlewareStatus();
    }

    @Override
    public boolean isEnabled() {
        return CONFIG.isEnabled();
    }

}
