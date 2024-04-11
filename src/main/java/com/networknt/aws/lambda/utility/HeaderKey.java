package com.networknt.aws.lambda.utility;

public class HeaderKey {

    /* unique header keys used by light-4j */
    public static final String TRACEABILITY = "X-Traceability-Id";
    public static final String CORRELATION = "X-Correlation-Id";
    public static final String AUTHORIZATION = "Authorization";
    public static final String SCOPE_TOKEN = "X-Scope-Token";
    /* common header keys */
    public static final String CONTENT_TYPE = "Content-Type";
    public static final String WWW_AUTHENTICATE = "WWW-Authenticate";
    /* Amazon header keys */
    public static final String PARAMETER_SECRET_TOKEN = "X-Aws-Parameters-Secrets-Token";
    public static final String AMZ_TARGET = "X-Amz-Target";


}
