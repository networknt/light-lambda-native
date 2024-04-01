package com.networknt.aws.lambda.handler.chain;

import org.slf4j.MDC;

import java.util.Map;

public class ChainLinkWorker extends Thread {

    public ChainLinkWorker(Runnable runnable, AuditThreadContext context) {
        super(runnable);
        MDC.setContextMap(context.MDCContext);
    }

    @Override
    public synchronized void start() {
        super.start();
    }

    @Override
    public void run() {
        super.run();
    }

    public static class AuditThreadContext {
        final Map<String, String> MDCContext;
        public AuditThreadContext(Map<String, String> context) {
            this.MDCContext = context;
        }
    }
}
