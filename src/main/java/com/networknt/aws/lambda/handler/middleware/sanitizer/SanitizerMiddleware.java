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
    private volatile String configName = SanitizerConfig.CONFIG_NAME;
    private volatile SanitizerConfig config;
    private volatile EncoderWrapper bodyEncoder;
    private volatile EncoderWrapper headerEncoder;

    public SanitizerMiddleware() {
        config = SanitizerConfig.load(configName);
        bodyEncoder = new EncoderWrapper(Encoders.forName(config.getBodyEncoder()), config.getBodyAttributesToIgnore(), config.getBodyAttributesToEncode());
        headerEncoder = new EncoderWrapper(Encoders.forName(config.getHeaderEncoder()), config.getHeaderAttributesToIgnore(), config.getHeaderAttributesToEncode());
        if (LOG.isInfoEnabled()) LOG.info("SanitizerMiddleware is constructed");
    }

    /**
     * Constructor with configuration for testing purpose only
     * @param configName String
     */
    public SanitizerMiddleware(String configName) {
        this.configName = configName;
        config = SanitizerConfig.load(configName);
        bodyEncoder = new EncoderWrapper(Encoders.forName(config.getBodyEncoder()), config.getBodyAttributesToIgnore(), config.getBodyAttributesToEncode());
        headerEncoder = new EncoderWrapper(Encoders.forName(config.getHeaderEncoder()), config.getHeaderAttributesToIgnore(), config.getHeaderAttributesToEncode());
        LOG.info("SanitizerMiddleware is constructed");
    }

    @Override
    public Status execute(LightLambdaExchange exchange) {
        if (LOG.isDebugEnabled()) LOG.trace("SanitizerMiddleware.execute starts.");
        String method = exchange.getRequest().getHttpMethod();
        SanitizerConfig newConfig = SanitizerConfig.load(configName);
        if(config != newConfig) {
            synchronized (this) {
                if (config != newConfig) {
                    config = newConfig;
                    bodyEncoder = new EncoderWrapper(Encoders.forName(config.getBodyEncoder()), config.getBodyAttributesToIgnore(), config.getBodyAttributesToEncode());
                    headerEncoder = new EncoderWrapper(Encoders.forName(config.getHeaderEncoder()), config.getHeaderAttributesToIgnore(), config.getHeaderAttributesToEncode());
                    LOG.info("SanitizerConfig is reloaded.");
                }
            }
        }
        if (config.isHeaderEnabled()) {
            Map<String, String> headerMap = exchange.getRequest().getHeaders();
            if (headerMap != null) {
                for (Map.Entry<String, String> entry: headerMap.entrySet()) {
                    // if ignore list exists, it will take the precedence.
                    if(config.getHeaderAttributesToIgnore() != null && config.getHeaderAttributesToIgnore().stream().anyMatch(entry.getKey()::equalsIgnoreCase)) {
                        if(LOG.isTraceEnabled())
                            LOG.trace("Ignore header {} as it is in the ignore list.", entry.getKey());
                        continue;
                    }

                    if(config.getHeaderAttributesToEncode() != null) {
                        if(config.getHeaderAttributesToEncode().stream().anyMatch(entry.getKey()::equalsIgnoreCase)) {
                            if(LOG.isTraceEnabled())
                                LOG.trace("Encode header {} as it is not in the ignore list and it is in the encode list.", entry.getKey());
                            entry.setValue(headerEncoder.applyEncoding(entry.getValue()));
                        }
                    } else {
                        // no attributes to encode, encode everything except the ignore list.
                        if(LOG.isTraceEnabled())
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
                if (body.startsWith("{")) {
                    Map<String, Object> bodyMap = JsonMapper.string2Map(body);
                    bodyEncoder.encodeNode(bodyMap);
                    exchange.getRequest().setBody(JsonMapper.toJson(bodyMap));
                } else if (body.startsWith("[")) {
                    List bodyList = JsonMapper.string2List(body);
                    bodyEncoder.encodeList(bodyList);
                    exchange.getRequest().setBody(JsonMapper.toJson(bodyList));
                } else {
                    // Body is not in JSON format or form data, skip...
                    if(LOG.isDebugEnabled()) LOG.debug("Skip sanitization as the body is not in JSON format");
                }
            }
        }
        if (LOG.isDebugEnabled()) LOG.trace("SanitizerMiddleware.execute ends.");
        return successMiddlewareStatus();
    }

    @Override
    public boolean isEnabled() {
        return config.isEnabled();
    }

    @Override
    public boolean isAsynchronous() {
        return false;
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
    public void getCachedConfigurations() {

    }
}
