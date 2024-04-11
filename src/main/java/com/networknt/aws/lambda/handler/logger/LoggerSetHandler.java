package com.networknt.aws.lambda.handler.logger;

import ch.qos.logback.classic.Level;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.networknt.aws.lambda.handler.LambdaHandler;
import com.networknt.aws.lambda.LightLambdaExchange;
import com.networknt.config.Config;
import com.networknt.config.JsonMapper;
import com.networknt.logging.model.LoggerConfig;
import com.networknt.status.Status;
import com.networknt.utility.ModuleRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

public class LoggerSetHandler implements LambdaHandler {
    static final Logger logger = LoggerFactory.getLogger(LoggerGetHandler.class);
    static final String HANDLER_IS_DISABLED = "ERR10065";
    static final String REQUEST_BODY_MISSING = "ERR10059";

    static LoggerConfig config;

    public LoggerSetHandler() {
        if(logger.isInfoEnabled()) logger.info("LoggerSetHandler is constructed");
        config = LoggerConfig.load();
    }

    @Override
    public Status execute(LightLambdaExchange exchange) {
        if (logger.isTraceEnabled()) logger.trace("LoggerSetHandler.handleRequest starts.");
        Map<String, String> headers = Map.of("Content-Type", "application/json");
        if (config.isEnabled()) {
            // get the body from the request event
            String body = exchange.getRequest().getBody();
            if(body.isEmpty()) {
                return new Status(REQUEST_BODY_MISSING);
            } else {
                // parse the body to get the loggers
                List<Map<String, Object>> loggers = JsonMapper.string2List(body);
                for (Map<String, Object> map: loggers) {
                    String name = (String)map.get("name");
                    Level level = Level.valueOf((String)map.get("level"));
                    ch.qos.logback.classic.Logger logger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(name);
                    if(level != logger.getLevel()) logger.setLevel(level);
                }
                var res = new APIGatewayProxyResponseEvent()
                        .withStatusCode(200)
                        .withHeaders(headers)
                        .withBody(JsonMapper.toJson(loggers));
                exchange.setInitialResponse(res);
            }
        } else {
            return new Status(HANDLER_IS_DISABLED, "LoggerSetHandler");
        }
        if (logger.isTraceEnabled()) logger.trace("LoggerSetHandler.handleRequest ends.");
        return this.successMiddlewareStatus();
    }

    @Override
    public boolean isEnabled() {
        return config.isEnabled();
    }

    @Override
    public void register() {
        ModuleRegistry.registerModule(
                LoggerConfig.CONFIG_NAME,
                LoggerSetHandler.class.getName(),
                Config.getNoneDecryptedInstance().getJsonMapConfigNoCache(LoggerConfig.CONFIG_NAME),
                null);
    }

    @Override
    public void reload() {

    }

    @Override
    public boolean isAsynchronous() {
        return false;
    }


}
