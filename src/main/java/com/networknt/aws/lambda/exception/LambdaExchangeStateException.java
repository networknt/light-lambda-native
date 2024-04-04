package com.networknt.aws.lambda.exception;

public class LambdaExchangeStateException extends RuntimeException {
    public LambdaExchangeStateException(String message) {
        super(message);
    }

    public static LambdaExchangeStateException invalidStateException(int state, int wrongState) {
        var stateString = Integer.toBinaryString(state);
        var wrongStateString = Integer.toBinaryString(wrongState);
        return new LambdaExchangeStateException("Attempted to execute an operation while in the wrong state. Current state '" + stateString + "' has flag state '" + wrongStateString + "'.");
    }

    public static LambdaExchangeStateException missingStateException(int state, int missing) {
        var stateString = Integer.toBinaryString(state);
        var missingStateString = Integer.toBinaryString(missing);
        return new LambdaExchangeStateException("Attempted to execute an operation while missing a flag. Current state '" + stateString + "' is missing flag state '" + missingStateString + "'.");
    }
}
