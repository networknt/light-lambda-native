package com.networknt.aws.lambda.handler.chain;

import com.networknt.aws.lambda.handler.LambdaHandler;
import com.networknt.aws.lambda.LightLambdaExchange;
import com.networknt.aws.lambda.handler.middleware.MiddlewareRunnable;
import com.networknt.config.Config;
import com.networknt.status.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public class PooledChainLinkExecutor extends ThreadPoolExecutor {
    private static final Logger LOG = LoggerFactory.getLogger(PooledChainLinkExecutor.class);
    private static final String CONFIG_NAME = "pooled-chain-executor";
    private static final PooledChainConfig CONFIG = (PooledChainConfig) Config.getInstance().getJsonObjectConfig(CONFIG_NAME, PooledChainConfig.class);
    private final LinkedList<Status> chainResults = new LinkedList<>();
    private static final String MIDDLEWARE_THREAD_INTERRUPT = "ERR14003";
    private static final String MIDDLEWARE_UNHANDLED_EXCEPTION = "ERR14000";

    final Object lock = new Object();

    public PooledChainLinkExecutor() {
        super(CONFIG.getCorePoolSize(), CONFIG.getMaxPoolSize(), CONFIG.getKeepAliveTime(), TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>());
    }

    public void executeChain(final LightLambdaExchange exchange, final Chain chain) {

        if (!chain.isFinalized()) {
            LOG.error("Execution attempt on a chain that is not finalized! Call 'finalizeChain' before 'executeChain'");
            return;
        }

        for (var chainLinkGroup : chain.getGroupedChain()) {

            /* create workers & submit to queue */
            final var chainLinkWorkerGroup = this.createChainListWorkers(chainLinkGroup, exchange);
            final var chainLinkWorkerFutures = this.createChainLinkWorkerFutures(chainLinkWorkerGroup);

            /* await worker completion */
            this.awaitChainWorkerFutures(chainLinkWorkerFutures);

            if (this.isTerminating() || this.isTerminated() || this.isShutdown() || exchange.hasFailedState()) {
                break;
            }

            chainLinkWorkerGroup.clear();
            chainLinkWorkerFutures.clear();
        }

        //this.shutdown();
    }

    /**
     * Wait on future results.
     *
     * @param chainLinkWorkerFutures - list of futures from submitted tasks.
     */
    private void awaitChainWorkerFutures(final Collection<Future<?>> chainLinkWorkerFutures) {

        /* wait out the future results of the submitted tasks. */
        for (var chainLinkWorkerFuture : chainLinkWorkerFutures) {

            try {
                chainLinkWorkerFuture.get();

            } catch (ExecutionException e) {
                LOG.error(e.getMessage(), e);
                return;

            } catch (InterruptedException e) {
                LOG.error(e.getMessage(), e);
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Creates a collection of futures. One for each submitted task.
     *
     * @param chainLinkWorkerGroup - list of threads for submission.
     * @return - list of futures for submitted tasks.
     */
    private Collection<Future<?>> createChainLinkWorkerFutures(final ArrayList<ChainLinkWorker> chainLinkWorkerGroup) {

        final Collection<Future<?>> chainLinkWorkerFutures = new ArrayList<>();
        int linkNumber = 1;

        /* submit each link in the group into the pooled executor queue */
        for (var chainLinkWorker : chainLinkWorkerGroup) {
            LOG.debug("Submitting link '{}' for execution.", linkNumber++);

            if (!this.isShutdown() && !this.isTerminating() && !this.isTerminated())
                chainLinkWorkerFutures.add(this.submit(chainLinkWorker));
        }
        return chainLinkWorkerFutures;
    }

    /**
     * Creates the chain workers array to prepare for submission.
     *
     * @param chainLinkGroup - sub-group of main chain
     * @param exchange       - current exchange.
     * @return - List of thread workers for the tasks.
     */
    private ArrayList<ChainLinkWorker> createChainListWorkers(final ArrayList<LambdaHandler> chainLinkGroup, final LightLambdaExchange exchange) {
        final ArrayList<ChainLinkWorker> chainLinkWorkerGroup = new ArrayList<>();
        int linkNumber = 1;

        /* create a worker for each link in a group */
        for (var chainLink : chainLinkGroup) {
            LOG.debug("Creating thread for link '{}[{}]'.", chainLink.getClass().getName(), linkNumber++);

            if (chainLink.isEnabled()) {
                final var loggingContext = new ChainLinkWorker.AuditThreadContext(MDC.getCopyOfContextMap());
                final var runnable = new MiddlewareRunnable(chainLink, exchange, this.chainLinkCallback);
                final var worker = new ChainLinkWorker(runnable, loggingContext);
                chainLinkWorkerGroup.add(worker);

            } else
                LOG.debug("Middleware handler '{}' is disabled, no worker will be created.", chainLink.getClass().getName());

        }

        return chainLinkWorkerGroup;
    }

    public void abortExecution() {
        synchronized (lock) {
            this.shutdownNow();
        }
    }

    protected void addChainableResult(Status result) {
        this.chainResults.add(result);
    }

    private final ChainLinkCallback chainLinkCallback = new ChainLinkCallback() {

        @Override
        public void callback(final LightLambdaExchange exchange, Status status) {
            exchange.updateExchangeStatus(status);
            PooledChainLinkExecutor.this.addChainableResult(status);
        }

        /* handles any generic throwable that occurred during middleware execution. */
        @Override
        public void exceptionCallback(final LightLambdaExchange exchange, Throwable throwable) {

            if (throwable instanceof InterruptedException) {
                LOG.error("Interrupted thread and cancelled middleware execution", throwable);
                PooledChainLinkExecutor.this.addChainableResult(new Status(MIDDLEWARE_THREAD_INTERRUPT));

            } else {
                LOG.error("Middleware returned with unhandled exception.", throwable);
                PooledChainLinkExecutor.this.addChainableResult(new Status(MIDDLEWARE_UNHANDLED_EXCEPTION));
            }

            PooledChainLinkExecutor.this.abortExecution();

        }
    };

    public List<Status> getChainResults() {
        return chainResults;
    }

}
