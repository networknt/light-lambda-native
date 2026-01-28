package com.networknt.aws.lambda.handler.logger;

import ch.qos.logback.classic.LoggerContext;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.networknt.aws.lambda.handler.LambdaHandler;
import com.networknt.aws.lambda.LightLambdaExchange;
import com.networknt.config.Config;
import com.networknt.config.JsonMapper;
import com.networknt.logging.model.LoggerConfig;
import com.networknt.logging.model.LoggerInfo;
import com.networknt.status.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;


public class LoggerGetHandler implements LambdaHandler {
    static final Logger logger = LoggerFactory.getLogger(LoggerGetHandler.class);
    public static final String HANDLER_IS_DISABLED = "ERR10065";

    public LoggerGetHandler() {
        if(logger.isInfoEnabled()) logger.info("LoggerGetHandler is constructed");
    }

    @Override
    public boolean isEnabled() {
        return LoggerConfig.load().isEnabled();
    }

    @Override
    public boolean isAsynchronous() {
        return false;
    }

    @Override
    public Status execute(LightLambdaExchange exchange) {
        if (logger.isTraceEnabled()) logger.trace("LoggerGetHandler.handleRequest starts.");
        LoggerConfig config = LoggerConfig.load();
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
            exchange.setInitialResponse(res);
        } else {
            return new Status(HANDLER_IS_DISABLED, "LoggerGetHandler");
        }
        if (logger.isTraceEnabled()) logger.trace("LoggerGetHandler.handleRequest ends.");
        return this.successMiddlewareStatus();
    }
}
