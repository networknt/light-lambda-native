package com.networknt.aws.lambda.handler.middleware.sanitizer;

import com.networknt.aws.lambda.handler.MiddlewareHandler;
import com.networknt.aws.lambda.LightLambdaExchange;
import com.networknt.config.Config;
import com.networknt.config.JsonMapper;
import com.networknt.sanitizer.SanitizerConfig;
import com.networknt.status.Status;
import com.networknt.utility.ModuleRegistry;
import org.owasp.encoder.EncoderWrapper;
import org.owasp.encoder.Encoders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

public class SanitizerMiddleware implements MiddlewareHandler {
    private static final Logger LOG = LoggerFactory.getLogger(SanitizerMiddleware.class);
    private static SanitizerConfig CONFIG;
    EncoderWrapper bodyEncoder;
    EncoderWrapper headerEncoder;

    public SanitizerMiddleware() {
        CONFIG = SanitizerConfig.load();
        bodyEncoder = new EncoderWrapper(Encoders.forName(CONFIG.getBodyEncoder()), CONFIG.getBodyAttributesToIgnore(), CONFIG.getBodyAttributesToEncode());
        headerEncoder = new EncoderWrapper(Encoders.forName(CONFIG.getHeaderEncoder()), CONFIG.getHeaderAttributesToIgnore(), CONFIG.getHeaderAttributesToEncode());
        if (LOG.isInfoEnabled()) LOG.info("SanitizerMiddleware is constructed");
    }

    /**
     * Constructor with configuration for testing purpose only
     * @param cfg SanitizerConfig
     */
    public SanitizerMiddleware(SanitizerConfig cfg) {
        CONFIG = cfg;
        bodyEncoder = new EncoderWrapper(Encoders.forName(CONFIG.getBodyEncoder()), CONFIG.getBodyAttributesToIgnore(), CONFIG.getBodyAttributesToEncode());
        headerEncoder = new EncoderWrapper(Encoders.forName(CONFIG.getHeaderEncoder()), CONFIG.getHeaderAttributesToIgnore(), CONFIG.getHeaderAttributesToEncode());
        LOG.info("SanitizerMiddleware is constructed");
    }

    @Override
    public Status execute(LightLambdaExchange exchange) throws InterruptedException {
        if (LOG.isDebugEnabled()) LOG.trace("SanitizerMiddleware.execute starts.");
        String method = exchange.getRequest().getHttpMethod();
        if (CONFIG.isHeaderEnabled()) {
            Map<String, String> headerMap = exchange.getRequest().getHeaders();
            if (headerMap != null) {
                for (Map.Entry<String, String> entry: headerMap.entrySet()) {
                    // if ignore list exists, it will take the precedence.
                    if(CONFIG.getHeaderAttributesToIgnore() != null && CONFIG.getHeaderAttributesToIgnore().stream().anyMatch(entry.getKey()::equalsIgnoreCase)) {
                        if(LOG.isTraceEnabled()) LOG.trace("Ignore header " + entry.getKey() + " as it is in the ignore list.");
                        continue;
                    }

                    if(CONFIG.getHeaderAttributesToEncode() != null) {
                        if(CONFIG.getHeaderAttributesToEncode().stream().anyMatch(entry.getKey()::equalsIgnoreCase)) {
                            if(LOG.isTraceEnabled()) LOG.trace("Encode header " + entry.getKey() + " as it is not in the ignore list and it is in the encode list.");
                            entry.setValue(headerEncoder.applyEncoding(entry.getValue()));
                        }
                    } else {
                        // no attributes to encode, encode everything except the ignore list.
                        if(LOG.isTraceEnabled()) LOG.trace("Encode header " + entry.getKey() + " as it is not in the ignore list and the encode list is null.");
                        entry.setValue(headerEncoder.applyEncoding(entry.getValue()));
                    }
                }
            }
        }

        if (CONFIG.isBodyEnabled() && ("POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method) || "PATCH".equalsIgnoreCase(method))) {
            String body = exchange.getRequest().getBody();
            if (!body.isEmpty()) {
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
        return CONFIG.isEnabled();
    }

    @Override
    public void register() {
        ModuleRegistry.registerModule(
                SanitizerConfig.CONFIG_NAME,
                SanitizerMiddleware.class.getName(),
                Config.getNoneDecryptedInstance().getJsonMapConfigNoCache(SanitizerConfig.CONFIG_NAME),
                null
        );
    }

    @Override
    public void reload() {

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
