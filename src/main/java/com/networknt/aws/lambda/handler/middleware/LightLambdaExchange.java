package com.networknt.aws.lambda.handler.middleware;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.networknt.aws.lambda.handler.MiddlewareHandler;
import com.networknt.aws.lambda.handler.chain.PooledChainLinkExecutor;
import com.networknt.aws.lambda.handler.chain.Chain;
import com.networknt.status.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;


/**
 * Shared object among middleware threads containing information on the request/response event.
 */
public final class LightLambdaExchange {

    private static final Logger LOG = LoggerFactory.getLogger(LightLambdaExchange.class);

    // TODO change these request, response members to a more generic type to handle other more edge cases (i.e. lambda stream request/response)
    private APIGatewayProxyRequestEvent request;
    private InputStream streamRequest;
    private APIGatewayProxyResponseEvent response;
    private OutputStream streamResponse;
    private final Context context;
    private final Map<Attachable<?>, Object> requestAttachments = Collections.synchronizedMap(new HashMap<>());
    private final Map<Attachable<?>, Object> responseAttachments = Collections.synchronizedMap(new HashMap<>());
    private final PooledChainLinkExecutor executor;

    // Initial state
    private static final int INITIAL_STATE = 0;

    // Request chain ready for execution
    private static final int FLAG_STARTING_REQUEST_READY = 1 << 1;

    // Request chain execution complete
    private static final int FLAG_REQUEST_DONE = 1 << 2;

    // Request chain execution complete but had an exception occur
    private static final int FLAG_REQUEST_HAS_FAILURE = 1 << 3;

    // Received response from backend lambda and we are preparing to execute the response chain
    private static final int FLAG_STARTING_RESPONSE_READY = 1 << 4;

    // Response chain execution complete
    private static final int FLAG_RESPONSE_DONE = 1 << 5;

    // Response chain execution complete but had an exception occur
    private static final int FLAG_RESPONSE_HAS_FAILURE = 1 << 6;
    private int state = INITIAL_STATE;
    private int statusCode = 200;
    private final Chain chain;

    public LightLambdaExchange(Context context, Chain chain) {
        this.context = context;
        this.chain = chain;
        this.executor = new PooledChainLinkExecutor();
    }

    public void executeChain() {

        if (stateHasAllFlags(FLAG_STARTING_REQUEST_READY)) {
            this.executor.executeChain(this, this.chain);
            this.state &= ~FLAG_STARTING_REQUEST_READY;

            if (stateHasAllFlagsClear(FLAG_REQUEST_DONE)) {
                for (var res : this.executor.getChainResults()) {

                    if (this.hasErrorCode(res)) {
                        this.state |= FLAG_REQUEST_HAS_FAILURE;
                        this.statusCode = res.getStatusCode();
                        break;
                    }
                }
            }

            this.state |= FLAG_REQUEST_DONE;

        }
    }

//    public void executeResponseChain() {
//        if (stateHasAllFlags(FLAG_STARTING_RESPONSE_READY)) {
//            this.executor.executeChain(this, this.responseChain);
//            this.state &= ~FLAG_STARTING_RESPONSE_READY;
//
//            if (stateHasAllFlagsClear(FLAG_RESPONSE_DONE)) {
//
//                for (var res : this.executor.getChainResults()) {
//
//                    if (this.hasErrorCode(res)) {
//                        this.state |= FLAG_RESPONSE_HAS_FAILURE;
//                        this.statusCode = res.getStatusCode();
//                        break;
//                    }
//                }
//                this.state |= FLAG_RESPONSE_DONE;
//            }
//
//        }
//
//    }

    private boolean hasErrorCode(final Status status) {

        // TODO - change this to something more reliable than a string check
        return status.getCode().startsWith("ERR");
    }

    /**
     * Sets the response object of the exchange.
     *
     * @param response -
     */
    public void setResponse(APIGatewayProxyResponseEvent response) {

        if (stateHasAnyFlags(FLAG_STARTING_RESPONSE_READY | FLAG_RESPONSE_DONE | FLAG_RESPONSE_HAS_FAILURE))
            return;

        this.response = response;
        this.statusCode = response.getStatusCode();
        this.state |= FLAG_STARTING_RESPONSE_READY;
    }

    /**
     * Sets the request object of the exchange.
     *
     * @param request -
     */
    public void setRequest(APIGatewayProxyRequestEvent request) {

        if (stateHasAnyFlags(FLAG_STARTING_REQUEST_READY | FLAG_REQUEST_DONE | FLAG_REQUEST_HAS_FAILURE))
            return;

        this.request = request;
        this.state |= FLAG_STARTING_REQUEST_READY;
    }

    /**
     * Returns the response object or an exception object if there was a failure.
     *
     * @return - return formatted response event.
     */
    public APIGatewayProxyResponseEvent getResponse() {
        if(this.response != null) {
            return this.response;
        } else {
            if (stateHasAnyFlags(FLAG_REQUEST_HAS_FAILURE))
                return ExceptionUtil.convert(this.executor.getChainResults());

            if (stateHasAnyFlags(FLAG_RESPONSE_HAS_FAILURE))
                return ExceptionUtil.convert(this.executor.getChainResults());
        }
        return null;
    }

    public Context getContext() {
        return context;
    }
    public APIGatewayProxyRequestEvent getRequest() {
        return request;
    }

    /**
     * Adds a request attachment to the exchange.
     *
     * @param key - Attachable key.
     * @param o - object value.
     * @param <T> - Middleware key type.
     */
    public <T extends MiddlewareHandler> void addRequestAttachment(final Attachable<T> key, final Object o) {
        this.requestAttachments.put(key, o);
    }

    /**
     * Adds a response attachment to the exchange.
     *
     * @param key - Attachable key.
     * @param o - object value.
     * @param <T> - Middleware key type.
     */
    public <T extends MiddlewareHandler> void addResponseAttachment(final Attachable<T> key, final Object o) {
        this.responseAttachments.put(key, o);
    }

    /**
     * Get request attachment object for given key.
     *
     * @param attachable - middleware key
     * @return - returns the object for the provided key. Can return null if it does not exist.
     */
    public Object getRequestAttachment(final Attachable<?> attachable) {
        return this.requestAttachments.get(attachable);
    }

    /**
     * Get response attachment object for given key.
     *
     * @param attachable - middleware key
     * @return - returns the object for the provided. Can return null if it does not exist.
     */
    public Object getResponseAttachment(final Attachable<?> attachable) {
        return this.responseAttachments.get(attachable);
    }

    /**
     * Check to see if the exchange has any error state at all.
     *
     * @return - returns true if the exchange has a failure state.
     */
    public boolean hasFailedState() {
        return stateHasAnyFlags(FLAG_REQUEST_HAS_FAILURE | FLAG_RESPONSE_HAS_FAILURE);
    }

    /**
     * Checks to see if the exchange is in the 'request in progress' state.
     * The exchange is in the request state when the request chain is ready and has not finished executing.
     *
     * @return - returns true if the exchange is handing the request.
     */
    public boolean isRequestInProgress() {
        return this.stateHasAllFlags(FLAG_STARTING_REQUEST_READY)
                && this.stateHasAllFlagsClear(FLAG_REQUEST_DONE);
    }

    /**
     * Checks to see if the exchange is in the response in progress state.
     * The exchange is in the response state when the request chain is complete, and the response chain is ready and has not finished executing.
     *
     * @return - return true if the exchange is handling the response.
     */
    public boolean isResponseInProgress() {
        return this.stateHasAllFlags(FLAG_REQUEST_DONE | FLAG_STARTING_RESPONSE_READY)
                && this.stateHasAllFlagsClear(FLAG_RESPONSE_DONE);
    }

    /**
     * Checks to see if the exchange is in the response in progress state.
     * @return int state
     */
    public int getState() {
        return state;
    }

    /**
     * Checks to see if any of the provided flags is true.
     *
     * @param flags - flags to check.
     * @return - returns true if any flags are true.
     */
    private boolean stateHasAnyFlags(final int flags) {
        return (this.state & flags) != 0;
    }

    /**
     * Checks to see if any of the provided flags are not set.
     *
     * @param flags - flags to check.
     * @return - returns true if any of the provided flags are false.
     */
    private boolean stateHasAnyFlagsClear(final int flags) {
        return (this.state & flags) != flags;
    }

    /**
     * Checks to see if all provided flags are set.
     *
     * @param flags - flags to check.
     * @return - returns true if all provided flags are true.
     */
    private boolean stateHasAllFlags(final int flags) {
        return (this.state & flags) == flags;
    }

    /**
     * Checks to see if all provided flags are not set.
     *
     * @param flags - flags to check.
     * @return - returns true if all flags are false.
     */
    private boolean stateHasAllFlagsClear(final int flags) {
        return (this.state & flags) == 0;
    }

    @Override
    public String toString() {
        return "LightLambdaExchange{" +
                "request=" + request +
                ", response=" + response +
                ", context=" + context +
                ", requestAttachments=" + requestAttachments +
                ", responseAttachments=" + responseAttachments +
                ", executor=" + executor +
//                ", requestChain=" + requestChain +
//                ", responseChain=" + responseChain +
                ", state=" + state +
                ", statusCode=" + statusCode +
                '}';
    }

    /**
     * Attachment key class to attach data to the exchange.
     *
     * @param <T> -
     */
    public static class Attachable<T extends MiddlewareHandler> {
        private final Class<T> key;

        private Attachable(Class<T> key) {
            this.key = key;
        }

        public Class<T> getKey() {
            return key;
        }

        /**
         * Creates a new attachable key.
         *
         * @param middleware - class to create a key for.
         * @return - returns new attachable instance.
         * @param <T> - given class has to implement the MiddlewareHandler interface.
         */
        public static <T extends MiddlewareHandler> Attachable<T> createMiddlewareAttachable(final Class<T> middleware) {
            return new Attachable<>(middleware);
        }
    }


}
