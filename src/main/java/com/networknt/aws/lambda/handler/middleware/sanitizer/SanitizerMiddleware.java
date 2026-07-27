package com.networknt.aws.lambda.handler.middleware.sanitizer;

import com.networknt.aws.lambda.handler.MiddlewareHandler;
import com.networknt.aws.lambda.LightLambdaExchange;
import com.networknt.config.Config;
import com.networknt.config.JsonMapper;
import com.networknt.sanitizer.SanitizerConfig;
import com.networknt.status.Status;
import org.owasp.encoder.EncoderWrapper;
import org.owasp.encoder.Encoders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

public class SanitizerMiddleware implements MiddlewareHandler {
    private static final Logger LOG = LoggerFactory.getLogger(SanitizerMiddleware.class);
    static final String CONTENT_TYPE_MISMATCH = "ERR10015";
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
            Map<String, String> headerMap = exchange.getRequest().getHeaders();
            if (headerMap != null) {
                for (Map.Entry<String, String> entry: headerMap.entrySet()) {
                    // if ignore list exists, it will take the precedence.
                    if(config.getHeaderAttributesToIgnore() != null && config.getHeaderAttributesToIgnore().stream().anyMatch(entry.getKey()::equalsIgnoreCase)) {

                        LOG.trace("Ignore header {} as it is in the ignore list.", entry.getKey());
                        continue;
                    }

                    if(config.getHeaderAttributesToEncode() != null) {
                        if(config.getHeaderAttributesToEncode().stream().anyMatch(entry.getKey()::equalsIgnoreCase)) {

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
        }

        if (config.isBodyEnabled() && ("POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method) || "PATCH".equalsIgnoreCase(method))) {
            String body = exchange.getRequest().getBody();
            if (body != null && !body.isEmpty()) {
                body = body.trim();
                String contentType = getContentType(exchange.getRequest().getHeaders());
                try {
                    if (body.startsWith("{")) {
                        Map<String, Object> bodyMap = JsonMapper.string2Map(body);
                        if (bodyMap == null) {
                            // the body is not a valid JSON object, JsonMapper.string2Map returned null.
                            LOG.error("Invalid JSON body received; unable to parse as a JSON object.");
                            return new Status(CONTENT_TYPE_MISMATCH, contentType);
                        }
                        bodyEncoder.encodeNode(bodyMap);
                        exchange.getRequest().setBody(JsonMapper.toJson(bodyMap));
                    } else if (body.startsWith("[")) {
                        List bodyList = JsonMapper.string2List(body);
                        if (bodyList == null) {
                            // the body is not a valid JSON array, JsonMapper.string2List returned null.
                            LOG.error("Invalid JSON body received; unable to parse as a JSON array.");
                            return new Status(CONTENT_TYPE_MISMATCH, contentType);
                        }
                        bodyEncoder.encodeList(bodyList);
                        exchange.getRequest().setBody(JsonMapper.toJson(bodyList));
                    } else {
                        // Body is not in JSON format or form data, skip...
                        LOG.debug("Skip sanitization as the body is not in JSON format");
                    }
                } catch (Exception e) {
                    // catch any exception thrown while parsing/encoding the body (e.g. malformed JSON)
                    // so that it doesn't bubble up as an unhandled NullPointerException/RuntimeException
                    // resulting in a 500 response.
                    LOG.error("Exception while sanitizing the request body: {}", e.getMessage(), e);
                    return new Status(CONTENT_TYPE_MISMATCH, contentType);
                }
            }
        }
        LOG.trace("SanitizerMiddleware.execute ends.");
        return successMiddlewareStatus();
    }

    /**
     * Looks up the Content-Type header value from the given headers map, ignoring case.
     *
     * @param headers the request headers, may be null
     * @return the Content-Type header value, or "unknown" if not present
     */
    private static String getContentType(Map<String, String> headers) {
        if (headers != null) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                if ("Content-Type".equalsIgnoreCase(entry.getKey())) {
                    return entry.getValue();
                }
            }
        }
        return "unknown";
    }

    @Override
    public boolean isEnabled() {
        return config.isEnabled();
    }
}
