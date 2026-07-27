package com.networknt.aws.lambda.handler.middleware.sanitizer;

import com.networknt.aws.lambda.handler.MiddlewareHandler;
import com.networknt.aws.lambda.LightLambdaExchange;
import com.networknt.aws.lambda.utility.HeaderKey;
import com.networknt.config.JsonMapper;
import com.networknt.sanitizer.SanitizerConfig;
import com.networknt.status.Status;
import com.networknt.utility.MapUtil;
import org.owasp.encoder.EncoderWrapper;
import org.owasp.encoder.Encoders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public class SanitizerMiddleware implements MiddlewareHandler {
    private static final Logger LOG = LoggerFactory.getLogger(SanitizerMiddleware.class);
    static final String CONTENT_TYPE_MISMATCH = "ERR10015";
    static final String GENERIC_EXCEPTION = "ERR10014";
    private final SanitizerConfig config;
    private final EncoderWrapper bodyEncoder;
    private final EncoderWrapper headerEncoder;

    public SanitizerMiddleware() {
        config = SanitizerConfig.load();
        bodyEncoder = new EncoderWrapper(Encoders.forName(config.getBodyEncoder()), config.getBodyAttributesToIgnore(), config.getBodyAttributesToEncode());
        headerEncoder = new EncoderWrapper(Encoders.forName(config.getHeaderEncoder()), config.getHeaderAttributesToIgnore(), config.getHeaderAttributesToEncode());
        LOG.info("SanitizerMiddleware is constructed");
    }

    /**
     * Constructor with configuration for testing purpose only
     * @param configName String
     */
    public SanitizerMiddleware(String configName) {
        config = SanitizerConfig.load(configName);
        bodyEncoder = new EncoderWrapper(Encoders.forName(config.getBodyEncoder()), config.getBodyAttributesToIgnore(), config.getBodyAttributesToEncode());
        headerEncoder = new EncoderWrapper(Encoders.forName(config.getHeaderEncoder()), config.getHeaderAttributesToIgnore(), config.getHeaderAttributesToEncode());
        LOG.info("SanitizerMiddleware is constructed");
    }

    @Override
    public Status execute(LightLambdaExchange exchange) {
        LOG.trace("SanitizerMiddleware.execute starts.");
        String method = exchange.getRequest().getHttpMethod();
        if (config.isHeaderEnabled()) {
            this.sanitizeHeaders(exchange);
        }

        if (config.isBodyEnabled() && ("POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method) || "PATCH".equalsIgnoreCase(method))) {
            String body = exchange.getRequest().getBody();
            if (body != null && !body.isEmpty()) {
                body = body.trim();
                // If the payload starts with '{', assume to be an object.
                if (body.startsWith("{")) {
                    var status = this.sanitizeObjectBody(body, exchange);
                    if (status.isPresent()) {
                        return status.get();
                    }
                // If the payload starts with '[', assume to be an array.
                } else if (body.startsWith("[")) {
                    var status = this.sanitizeArrayBody(body, exchange);
                    if (status.isPresent()) {
                        return status.get();
                    }
                } else {
                    LOG.debug("Skip sanitization as the body is not in JSON format");
                }
            }
        }
        LOG.trace("SanitizerMiddleware.execute ends.");
        return successMiddlewareStatus();
    }

    /**
     * Applies header encoding to the request headers based on the configured ignore/encode lists.
     *
     * @param exchange the current exchange
     */
    private void sanitizeHeaders(LightLambdaExchange exchange) {
        Map<String, String> headerMap = exchange.getRequest().getHeaders();
        if (headerMap == null) {
            return;
        }
        for (Map.Entry<String, String> entry : headerMap.entrySet()) {
            // if ignore list exists, it will take the precedence.
            if (config.getHeaderAttributesToIgnore() != null && config.getHeaderAttributesToIgnore().stream().anyMatch(entry.getKey()::equalsIgnoreCase)) {
                LOG.trace("Ignore header {} as it is in the ignore list.", entry.getKey());
                continue;
            }

            if (config.getHeaderAttributesToEncode() != null) {
                if (config.getHeaderAttributesToEncode().stream().anyMatch(entry.getKey()::equalsIgnoreCase)) {
                    LOG.trace("Encode header {} as it is not in the ignore list and it is in the encode list.", entry.getKey());
                    entry.setValue(headerEncoder.applyEncoding(entry.getValue()));
                }
            } else {
                // no attributes to encode, encode everything except the ignore list.
                LOG.trace("Encode header {} as it is not in the ignore list and the encode list is null.", entry.getKey());
                entry.setValue(headerEncoder.applyEncoding(entry.getValue()));
            }
        }
    }

    /**
     * Sanitizes a JSON object body.
     * If the body fails to parse we return CONTENT_TYPE_MISMATCH; if sanitizing/serializing
     * fails we return GENERIC_EXCEPTION. No status is returned if sanitization succeeds.
     *
     * @param body - The body from the lambda event.
     * @param exchange - The full lambda event context.
     * @return - Returns a status if there was an issue with parsing or sanitization.
     */
    private Optional<Status> sanitizeObjectBody(String body, LightLambdaExchange exchange) {
        Map<String, Object> bodyMap;
        try {
            bodyMap = JsonMapper.string2Map(body);
        } catch (Exception e) {
            LOG.warn("Unable to parse the request body as a JSON object: {}", e.getClass().getName());
            return Optional.of(new Status(CONTENT_TYPE_MISMATCH, getContentType(exchange)));
        }
        try {
            bodyEncoder.encodeNode(bodyMap);
            exchange.getRequest().setBody(JsonMapper.toJson(bodyMap));
        } catch (Exception e) {
            LOG.error("Exception while encoding the request body", e);
            return Optional.of(new Status(GENERIC_EXCEPTION));
        }
        return Optional.empty();
    }

    /**
     * Sanitizes a JSON array body.
     * If the body fails to parse we return CONTENT_TYPE_MISMATCH; if sanitizing/serializing
     * fails we return GENERIC_EXCEPTION. No status is returned if sanitization succeeds.
     *
     * @param body - The body from the lambda event.
     * @param exchange - The full lambda event context.
     * @return - Returns a status if there was an issue with parsing or sanitization.
     */
    private Optional<Status> sanitizeArrayBody(String body, LightLambdaExchange exchange) {
        List<Map<String, Object>> bodyList;
        try {
            bodyList = JsonMapper.string2List(body);
        } catch (Exception e) {
            LOG.warn("Unable to parse the request body as a JSON array: {}", e.getClass().getName());
            return Optional.of(new Status(CONTENT_TYPE_MISMATCH, getContentType(exchange)));
        }
        try {
            bodyEncoder.encodeList(bodyList);
            exchange.getRequest().setBody(JsonMapper.toJson(bodyList));
        } catch (Exception e) {
            LOG.error("Exception while encoding the request body", e);
            return Optional.of(new Status(GENERIC_EXCEPTION));
        }
        return Optional.empty();
    }

    /**
     * Looks up the Content-Type header value from the request, ignoring case.
     *
     * @param exchange the current exchange
     * @return the Content-Type header value, or "unknown" if not present
     */
    private static String getContentType(LightLambdaExchange exchange) {
        return MapUtil.getValueIgnoreCase(exchange.getRequest().getHeaders(), HeaderKey.CONTENT_TYPE)
                .orElse("unknown");
    }

    @Override
    public boolean isEnabled() {
        return config.isEnabled();
    }
}
