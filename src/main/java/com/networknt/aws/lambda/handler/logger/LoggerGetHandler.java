package com.networknt.aws.lambda.handler.logger;

import ch.qos.logback.classic.LoggerContext;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.networknt.aws.lambda.handler.LambdaHandler;
import com.networknt.aws.lambda.handler.middleware.LightLambdaExchange;
import com.networknt.config.Config;
import com.networknt.config.JsonMapper;
import com.networknt.logging.model.LoggerConfig;
import com.networknt.logging.model.LoggerInfo;
import com.networknt.status.Status;
import com.networknt.utility.ModuleRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;


public class LoggerGetHandler implements LambdaHandler {
    static final Logger logger = LoggerFactory.getLogger(LoggerGetHandler.class);
    public static final String HANDLER_IS_DISABLED = "ERR10065";
    static LoggerConfig config;

    public LoggerGetHandler() {
        if(logger.isInfoEnabled()) logger.info("LoggerGetHandler is constructed");
        config = LoggerConfig.load();
    }

    @Override
    public void register() {
        ModuleRegistry.registerModule(
                LoggerConfig.CONFIG_NAME,
                LoggerGetHandler.class.getName(),
                Config.getNoneDecryptedInstance().getJsonMapConfigNoCache(LoggerConfig.CONFIG_NAME),
                null);
    }

    @Override
    public boolean isEnabled() {
        return config.isEnabled();
    }

    @Override
    public void reload() {

    }

    @Override
    public boolean isAsynchronous() {
        return false;
    }

    @Override
    public Status execute(LightLambdaExchange exchange) throws InterruptedException {
        if (logger.isTraceEnabled()) logger.trace("LoggerGetHandler.handleRequest starts.");
        Map<String, String> headers = Map.of("Content-Type", "application/json");
        if (config.isEnabled()) {
            List<LoggerInfo> loggersList = new ArrayList<LoggerInfo>();
            LoggerContext lc = (LoggerContext) LoggerFactory.getILoggerFactory();
            for (ch.qos.logback.classic.Logger log : lc.getLoggerList()) {
                if (log.getLevel() != null) {
                    LoggerInfo loggerInfo = new LoggerInfo();
                    loggerInfo.setName(log.getName());
                    loggerInfo.setLevel(log.getLevel().toString());
                    loggersList.add(loggerInfo);
                }
            }
            var res = new APIGatewayProxyResponseEvent()
                    .withStatusCode(200)
                    .withHeaders(headers)
                    .withBody(JsonMapper.toJson((loggersList)));
            exchange.setResponse(res);
        } else {
            return new Status(HANDLER_IS_DISABLED, "LoggerGetHandler");
        }
        if (logger.isTraceEnabled()) logger.trace("LoggerGetHandler.handleRequest ends.");
        return this.successMiddlewareStatus();
    }
}
