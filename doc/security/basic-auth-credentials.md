# Basic Auth credential handling (A20 / A25)

[Issue #191](https://github.com/networknt/light-lambda-native/issues/191) covers
partner Checkmarx A25 rows 90–97 and A20 row 98.

## Re-evaluation

At master `b6d27a7d42ecdc94efe387acdd1cb4b3f47a7ce7` and 2.x
`785235d5324697958a573cfa6ad6e664615e19ab`, the reported A25 password/header
logging paths were already mitigated: authentication error messages and LDAP
dispatch traces omit submitted credentials. The scoped fix therefore addresses
A20, with regression coverage protecting A25. Denied-path diagnostics still
include the configured username and sanitized path; this is not a promise to
remove all identity data from logs.

## Ownership and cleanup

Local Basic authentication no longer creates an immutable decoded credential
pair or password String. A request-local closeable owns the decoded bytes and
password bytes and clears them after authentication/authorization, including
early returns and exceptions. The shared scheme check scans characters without creating a credential substring.
Temporary header/payload copies and configured
password comparison bytes are also cleared. Local comparison continues to use
`MessageDigest.isEqual`. Existing raw `username:password` compatibility, Base64
decoding, Unicode passwords, colons in passwords, status codes, reserved-user
rules and LDAP dispatch are retained for valid UTF-8 credentials.

The protected `handleLdapAuth(UserAuth, String)` extension and the LDAP library
still require a String. Only that branch constructs a password String; it cannot
be zeroed. No borrowed request header or configured password is modified.
The Lambda request, configuration, decoder/JVM internals and downstream LDAP
implementation may retain secrets. Clearing owned arrays reduces extra retention
and does not guarantee complete heap erasure or establish a remote exploit.
Protect request/access/audit logs, memory dumps, diagnostics and transport.

## Verification boundaries

Focused JVM tests exercise the existing Basic authentication contract, owned
buffer cleanup for successful/malformed/empty/exceptional scopes, and synthetic
local/LDAP outcomes at TRACE. Log checks inspect messages, argument arrays, MDC
and throwable causes/suppressed exceptions; returned status content is checked
for canaries as well. LDAP is a local test double. No live LDAP bind, AWS
deployment, native-image rebuild or partner rescan is implied by these tests.
Deployment requires rebuilding and distributing the native Lambda artifact.
