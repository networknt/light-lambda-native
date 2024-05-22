package com.networknt.aws.lambda.handler.middleware.router;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.networknt.aws.lambda.LightLambdaExchange;
import com.networknt.aws.lambda.handler.Handler;
import com.networknt.aws.lambda.handler.LambdaHandler;
import com.networknt.aws.lambda.handler.MiddlewareHandler;
import com.networknt.aws.lambda.handler.middleware.metrics.AbstractMetricsMiddleware;
import com.networknt.aws.lambda.utility.MapUtil;
import com.networknt.cluster.Cluster;
import com.networknt.config.Config;
import com.networknt.config.JsonMapper;
import com.networknt.http.client.HttpClientRequest;
import com.networknt.http.client.HttpMethod;
import com.networknt.metrics.MetricsConfig;
import com.networknt.monad.Failure;
import com.networknt.monad.Result;
import com.networknt.router.RouterConfig;
import com.networknt.service.SingletonServiceFactory;
import com.networknt.status.Status;
import com.networknt.utility.ModuleRegistry;
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
 * This middleware is responsible for routing the incoming request to the external microservices.
 *
 */
public class LambdaRouterMiddleware implements MiddlewareHandler {
    private static LambdaClient client;
    private static final Logger LOG = LoggerFactory.getLogger(LambdaRouterMiddleware.class);
    private static AbstractMetricsMiddleware metricsMiddleware;
    private static final Cluster cluster = SingletonServiceFactory.getBean(Cluster.class);
    public static final String FAILED_TO_INVOKE_SERVICE = "ERR10089";
    public static final String EXCHANGE_HAS_FAILED_STATE = "ERR10087";
    public static final RouterConfig CONFIG = RouterConfig.load();
    private static final String protocol = CONFIG.isHttpsEnabled() ? "https" : "http";
    static final Map<String, PathTemplateMatcher<String>> methodToMatcherMap = new HashMap<>();

    public LambdaRouterMiddleware() {
        if(CONFIG.isMetricsInjection()) lookupMetricsMiddleware();
        if (LOG.isInfoEnabled()) LOG.info("LambdaRouterMiddleware is constructed");
    }

    @Override
    public Status execute(LightLambdaExchange exchange) {
        if(LOG.isTraceEnabled()) LOG.trace("LambdaRouterMiddleware.execute starts.");
        // check if the Function-Name is in the header. If it is, we will continue. Otherwise, return immediately.
        Optional<String> serviceIdOptional = MapUtil.getValueIgnoreCase(exchange.getRequest().getHeaders(), SERVICE_ID);
        if(serviceIdOptional.isEmpty()) {
            LOG.error("service_id is not in the header. Skip LambdaRouterMiddleware.");
            return this.successMiddlewareStatus();
        } else {
            if (!exchange.hasFailedState()) {
                /* invoke http service */
                String serviceId = serviceIdOptional.get();
                var path = exchange.getRequest().getPath();
                var method = exchange.getRequest().getHttpMethod().toLowerCase();
                LOG.debug("Request path: {} -- Request method: {} -- Start time: {}", path, method, System.currentTimeMillis());
                // lookup the host from the serviceId
                String host = cluster.serviceToUrl(protocol, serviceId, null, null);
                if (host == null) {
                    LOG.error("No host is found serviceId: {}", serviceId);
                    return new Status(FAILED_TO_INVOKE_SERVICE, serviceId);
                }
                if(LOG.isTraceEnabled()) LOG.trace("Discovered host {} for ServiceId {}", host, serviceId);
                // call the downstream service based on the request methods.
                long startTime = System.nanoTime();
                if("get".equalsIgnoreCase(method) || "delete".equalsIgnoreCase(method)) {
                    HttpClientRequest request = new HttpClientRequest();
                    try {
                        HttpRequest.Builder builder = request.initBuilder(host + path, HttpMethod.valueOf(exchange.getRequest().getHttpMethod()));
                        exchange.getRequest().getHeaders().forEach(builder::header);
                        builder.timeout(Duration.ofMillis(CONFIG.getMaxRequestTime()));
                        request.addCcToken(builder, path, null, null);
                        HttpResponse<String> response = (HttpResponse<String>) request.send(builder, HttpResponse.BodyHandlers.ofString());
                        APIGatewayProxyResponseEvent res = new APIGatewayProxyResponseEvent()
                                .withStatusCode(response.statusCode())
                                .withHeaders(convertJdkHeaderToMap(response.headers().map()))
                                .withBody(response.body());
                        if(CONFIG.isMetricsInjection()) {
                            if(metricsMiddleware == null) lookupMetricsMiddleware();
                            if(metricsMiddleware != null) {
                                if (LOG.isTraceEnabled()) LOG.trace("Inject metrics for {}", CONFIG.getMetricsName());
                                metricsMiddleware.injectMetrics(exchange, startTime, CONFIG.getMetricsName(), null);
                            }
                        }
                        if(LOG.isTraceEnabled()) LOG.trace("Response: {}", JsonMapper.toJson(res));
                        exchange.setInitialResponse(res);
                    } catch (Exception e) {
                        LOG.error("Exception:", e);
                        return new Status(FAILED_TO_INVOKE_SERVICE, host + path);
                    }
                } else if("post".equalsIgnoreCase(method) || "put".equalsIgnoreCase(method) || "patch".equalsIgnoreCase(method)) {
                    HttpClientRequest request = new HttpClientRequest();
                    try {
                        HttpRequest.Builder builder = request.initBuilder(host + path, HttpMethod.valueOf(exchange.getRequest().getHttpMethod()), Optional.of(exchange.getRequest().getBody()));
                        exchange.getRequest().getHeaders().forEach(builder::header);
                        builder.timeout(Duration.ofMillis(CONFIG.getMaxRequestTime()));
                        request.addCcToken(builder, path, null, null);
                        HttpResponse<String> response = (HttpResponse<String>) request.send(builder, HttpResponse.BodyHandlers.ofString());
                        APIGatewayProxyResponseEvent res = new APIGatewayProxyResponseEvent()
                                .withStatusCode(response.statusCode())
                                .withHeaders(convertJdkHeaderToMap(response.headers().map()))
                                .withBody(response.body());
                        if(CONFIG.isMetricsInjection()) {
                            if(metricsMiddleware == null) lookupMetricsMiddleware();
                            if(metricsMiddleware != null) {
                                if (LOG.isTraceEnabled()) LOG.trace("Inject metrics for {}", CONFIG.getMetricsName());
                                metricsMiddleware.injectMetrics(exchange, startTime, CONFIG.getMetricsName(), null);
                            }
                        }
                        if(LOG.isTraceEnabled()) LOG.trace("Response: {}", JsonMapper.toJson(res));
                        exchange.setInitialResponse(res);
                    } catch (Exception e) {
                        LOG.error("Exception:", e);
                        return new Status(FAILED_TO_INVOKE_SERVICE, host + path);
                    }
                } else {
                    LOG.error("Unsupported HTTP method: {}", method);
                    return new Status(FAILED_TO_INVOKE_SERVICE, serviceId);
                }
                if(LOG.isTraceEnabled()) LOG.trace("LambdaRouterMiddleware.execute ends.");
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

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public void register() {
        ModuleRegistry.registerModule(
                RouterConfig.CONFIG_NAME,
                LambdaRouterMiddleware.class.getName(),
                Config.getNoneDecryptedInstance().getJsonMapConfigNoCache(RouterConfig.CONFIG_NAME),
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

    @Override
    public void getCachedConfigurations() {

    }

    private void lookupMetricsMiddleware() {
        // get the metrics middleware instance from the chain.
        Map<String, LambdaHandler> handlers = Handler.getHandlers();
        metricsMiddleware = (AbstractMetricsMiddleware) handlers.get(MetricsConfig.CONFIG_NAME);
        if(metricsMiddleware == null) {
            LOG.error("An instance of MetricsMiddleware is not configured in the handler.yml file.");
        }
    }

}
