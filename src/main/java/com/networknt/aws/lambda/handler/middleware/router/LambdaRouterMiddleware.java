package com.networknt.aws.lambda.handler.middleware.router;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.networknt.aws.lambda.LightLambdaExchange;
import com.networknt.aws.lambda.handler.Handler;
import com.networknt.aws.lambda.handler.LambdaHandler;
import com.networknt.aws.lambda.handler.MiddlewareHandler;
import com.networknt.aws.lambda.handler.middleware.metrics.AbstractMetricsMiddleware;
import com.networknt.utility.MapUtil;
import com.networknt.cluster.Cluster;
import com.networknt.config.Config;
import com.networknt.config.JsonMapper;
import com.networknt.handler.config.UrlRewriteRule;
import com.networknt.http.client.HttpClientRequest;
import com.networknt.http.client.HttpMethod;
import com.networknt.metrics.MetricsConfig;
import com.networknt.router.RouterConfig;
import com.networknt.service.SingletonServiceFactory;
import com.networknt.status.Status;
import com.networknt.utility.PathTemplateMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.lambda.LambdaClient;

import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.networknt.aws.lambda.utility.HeaderKey.SERVICE_ID;

/**
 * This middleware is responsible for routing the incoming request to the
 * external microservices.
 *
 */
public class LambdaRouterMiddleware implements MiddlewareHandler {
    private static final Logger LOG = LoggerFactory.getLogger(LambdaRouterMiddleware.class);
    private static AbstractMetricsMiddleware metricsMiddleware;
    private static final Cluster cluster = SingletonServiceFactory.getBean(Cluster.class);
    public static final String FAILED_TO_INVOKE_SERVICE = "ERR10089";
    public static final String EXCHANGE_HAS_FAILED_STATE = "ERR10087";

    private volatile LambdaClient client;
    private volatile RouterConfig config;
    private String protocol;
    static final Map<String, PathTemplateMatcher<String>> methodToMatcherMap = new HashMap<>();

    public LambdaRouterMiddleware() {
        this.config = RouterConfig.load();
        this.protocol = config.isHttpsEnabled() ? "https" : "http";
        if (config.isMetricsInjection())
            lookupMetricsMiddleware();
        if (LOG.isInfoEnabled())
            LOG.info("LambdaRouterMiddleware is constructed");
    }

    @Override
    public Status execute(LightLambdaExchange exchange) {
        if (LOG.isTraceEnabled())
            LOG.trace("LambdaRouterMiddleware.execute starts.");
        RouterConfig newConfig = RouterConfig.load();
        if(newConfig != config) {
            synchronized (this) {
                newConfig = RouterConfig.load();
                if(newConfig != config) {
                    this.config = newConfig;
                    this.protocol = config.isHttpsEnabled() ? "https" : "http";
                    if (config.isMetricsInjection())
                        lookupMetricsMiddleware();
                    if(LOG.isInfoEnabled()) LOG.info("RouterConfig is reloaded.");
                }
            }
        }

        // check if the Function-Name is in the header. If it is, we will continue.
        // Otherwise, return immediately.
        Optional<String> serviceIdOptional = MapUtil.delValueIgnoreCase(exchange.getRequest().getHeaders(), SERVICE_ID);
        if (serviceIdOptional.isEmpty()) {
            LOG.error("service_id is not in the header. Skip LambdaRouterMiddleware.");
            return this.successMiddlewareStatus();
        } else {
            if (!exchange.hasFailedState()) {
                // get the finalized request to trigger the state change for the request
                // complete.
                APIGatewayProxyRequestEvent requestEvent = exchange.getFinalizedRequest(false);
                /* invoke http service */
                String serviceId = serviceIdOptional.get();
                var originalPath = requestEvent.getPath();
                var targetPath = originalPath;
                var method = requestEvent.getHttpMethod().toLowerCase();
                LOG.debug("Request path: {} -- Request method: {} -- Start time: {}", originalPath, method,
                        System.currentTimeMillis());
                // lookup the host from the serviceId
                String host = cluster.serviceToUrl(protocol, serviceId, null, null);
                if (host == null) {
                    LOG.error("No host is found serviceId: {}", serviceId);
                    return new Status(FAILED_TO_INVOKE_SERVICE, serviceId);
                }
                // we have the path now, let's apply the url rewrite if there is any. This is
                // useful when using the api gateway to add the stage.
                List<UrlRewriteRule> urlRewriteRules = config.getUrlRewriteRules();

                if (urlRewriteRules != null && !urlRewriteRules.isEmpty()) {
                    // apply the url rewrite rules to the path.
                    targetPath = createRouterRequestPath(urlRewriteRules, originalPath);
                    if (LOG.isTraceEnabled())
                        LOG.trace("Rewritten original path {} to targetPath {}", originalPath, targetPath);
                }
                if (LOG.isTraceEnabled())
                    LOG.trace("Discovered host {} for ServiceId {}", host, serviceId);
                // call the downstream service based on the request methods.
                long startTime = System.nanoTime();
                if ("get".equalsIgnoreCase(method) || "delete".equalsIgnoreCase(method)) {
                    HttpClientRequest request = new HttpClientRequest();
                    try {
                        HttpRequest.Builder builder = request.initBuilder(host + targetPath,
                                HttpMethod.valueOf(requestEvent.getHttpMethod()));
                        requestEvent.getHeaders().forEach(builder::header);
                        builder.timeout(Duration.ofMillis(config.getMaxRequestTime()));
                        HttpResponse<String> response = (HttpResponse<String>) request.send(builder,
                                HttpResponse.BodyHandlers.ofString());
                        APIGatewayProxyResponseEvent res = new APIGatewayProxyResponseEvent()
                                .withStatusCode(response.statusCode())
                                .withHeaders(convertJdkHeaderToMap(response.headers().map()))
                                .withBody(response.body());
                        if (config.isMetricsInjection()) {
                            if (metricsMiddleware == null)
                                lookupMetricsMiddleware();
                            if (metricsMiddleware != null) {
                                if (LOG.isTraceEnabled())
                                    LOG.trace("Inject metrics for {}", config.getMetricsName());
                                metricsMiddleware.injectMetrics(exchange, startTime, config.getMetricsName(), null);
                            }
                        }
                        if (LOG.isTraceEnabled())
                            LOG.trace("Response: {}", JsonMapper.toJson(res));
                        exchange.setInitialResponse(res);
                    } catch (Exception e) {
                        LOG.error("Exception:", e);
                        return new Status(FAILED_TO_INVOKE_SERVICE, host + targetPath);
                    }
                } else if ("post".equalsIgnoreCase(method) || "put".equalsIgnoreCase(method)
                        || "patch".equalsIgnoreCase(method)) {
                    HttpClientRequest request = new HttpClientRequest();
                    try {
                        HttpRequest.Builder builder = request.initBuilder(host + targetPath,
                                HttpMethod.valueOf(requestEvent.getHttpMethod()), Optional.of(requestEvent.getBody()));
                        requestEvent.getHeaders().forEach(builder::header);
                        builder.timeout(Duration.ofMillis(config.getMaxRequestTime()));
                        HttpResponse<String> response = (HttpResponse<String>) request.send(builder,
                                HttpResponse.BodyHandlers.ofString());
                        APIGatewayProxyResponseEvent res = new APIGatewayProxyResponseEvent()
                                .withStatusCode(response.statusCode())
                                .withHeaders(convertJdkHeaderToMap(response.headers().map()))
                                .withBody(response.body());
                        if (config.isMetricsInjection()) {
                            if (metricsMiddleware == null)
                                lookupMetricsMiddleware();
                            if (metricsMiddleware != null) {
                                if (LOG.isTraceEnabled())
                                    LOG.trace("Inject metrics for {}", config.getMetricsName());
                                metricsMiddleware.injectMetrics(exchange, startTime, config.getMetricsName(), null);
                            }
                        }
                        if (LOG.isTraceEnabled())
                            LOG.trace("Response: {}", JsonMapper.toJson(res));
                        exchange.setInitialResponse(res);
                    } catch (Exception e) {
                        LOG.error("Exception:", e);
                        return new Status(FAILED_TO_INVOKE_SERVICE, host + targetPath);
                    }
                } else {
                    LOG.error("Unsupported HTTP method: {}", method);
                    return new Status(FAILED_TO_INVOKE_SERVICE, serviceId);
                }
                if (LOG.isTraceEnabled())
                    LOG.trace("LambdaRouterMiddleware.execute ends.");
                return this.successMiddlewareStatus();
            } else {
                LOG.error("Exchange has failed state {}", exchange.getState());
                return new Status(EXCHANGE_HAS_FAILED_STATE, exchange.getState());
            }
        }
    }

    private Map<String, String> convertJdkHeaderToMap(final Map<String, List<String>> headers) {
        Map<String, String> map = new HashMap<>();
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            String headerName = entry.getKey();
            String headerValue = entry.getValue().isEmpty() ? null : entry.getValue().get(0);
            map.put(headerName, headerValue);
        }
        return map;
    }

    /**
     * Builds a complete request path string for our router request.
     *
     * @param urlRewriteRules - the list of url rewrite rules
     * @param requestPath     - the original request path
     * @return - targetRequestPath the target request path string
     */
    public String createRouterRequestPath(List<UrlRewriteRule> urlRewriteRules, String requestPath) {
        var uriBuilder = new StringBuilder();
        rewriteUrl(urlRewriteRules, uriBuilder, requestPath);
        return uriBuilder.toString();
    }

    /**
     * Rewrites the router request url based on defined rules.
     *
     * @param uriBuilder  - new URI
     * @param requestPath - target URI
     */
    private void rewriteUrl(List<UrlRewriteRule> urlRewriteRules, StringBuilder uriBuilder, String requestPath) {
        /*
         * Rewrites the url. Uses original if there are no rules matches/no rules
         * defined.
         */
        if (urlRewriteRules != null && !urlRewriteRules.isEmpty()) {
            var matched = false;
            for (var rule : urlRewriteRules) {
                var matcher = rule.getPattern().matcher(requestPath);
                if (matcher.matches()) {
                    matched = true;
                    uriBuilder.append(matcher.replaceAll(rule.getReplace()));
                    break;
                }
            }
            if (!matched)
                uriBuilder.append(requestPath);
        } else
            uriBuilder.append(requestPath);
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    private void lookupMetricsMiddleware() {
        // get the metrics middleware instance from the chain.
        Map<String, LambdaHandler> handlers = Handler.getHandlers();
        metricsMiddleware = (AbstractMetricsMiddleware) handlers.get(MetricsConfig.CONFIG_NAME);
        if (metricsMiddleware == null) {
            LOG.error("An instance of MetricsMiddleware is not configured in the handler.yml file.");
        }
    }

}
