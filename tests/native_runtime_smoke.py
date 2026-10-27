#!/usr/bin/env python3
"""Exercise the native Runtime against a loopback-only synthetic Lambda Runtime API."""
import argparse
import http.server
import json
import os
from pathlib import Path
import queue
import subprocess
import tempfile
import threading
import zipfile


def invoke(binary, status_catalog, handler, expected_endpoint, event, *, config_catalog):
    received = queue.Queue()

    class RuntimeApi(http.server.BaseHTTPRequestHandler):
        def log_message(self, *args):
            pass

        def do_GET(self):
            assert self.path == "/2018-06-01/runtime/invocation/next", self.path
            payload = json.dumps(event).encode()
            self.send_response(200)
            self.send_header("lambda-runtime-aws-request-id", "native-smoke")
            self.send_header("Content-Length", str(len(payload)))
            self.end_headers()
            self.wfile.write(payload)

        def do_POST(self):
            payload = self.rfile.read(int(self.headers["Content-Length"]))
            received.put((self.path, json.loads(payload)))
            self.send_response(202)
            self.send_header("Content-Length", "0")
            self.end_headers()

    with tempfile.TemporaryDirectory(prefix="native-runtime-smoke-") as directory:
        config = Path(directory)
        (config / "config.yml").write_bytes(config_catalog)
        (config / "handler.yml").write_text("""enabled: true
handlers:
  - com.networknt.aws.lambda.handler.middleware.specification.OpenApiMiddleware@spec
  - com.networknt.aws.lambda.handler.middleware.validator.ValidatorMiddleware@validator
  - com.networknt.aws.lambda.handler.health.HealthCheckHandler@health
chains: {}
paths:
  - path: /health
    method: get
    exec: [spec, validator, health]
defaultHandlers: [spec, validator, health]
""")
        (config / "health.yml").write_text("enabled: true\ndownstreamEnabled: false\n")
        (config / "lambda-app.yml").write_text("lambdaAppId: native-smoke\nencodeBase64Request: false\nencodeBase64Response: false\n")
        (config / "values.yml").write_text("{}\n")
        (config / "service.yml").write_text("singletons: []\n")
        (config / "status.yml").write_bytes(status_catalog)
        # Controlled fixture settings exercise validation; this is not default-config qualification.
        (config / "openapi-inject.yml").write_text("{}\n")
        (config / "openapi-handler.yml").write_text("enabled: true\nmultipleSpec: false\n")
        (config / "openapi-validator.yml").write_text("enabled: true\nskipPathPrefixes: []\n")
        (config / "openapi.yaml").write_text("""openapi: 3.0.3
info:
  title: Native validation smoke
  version: '1'
servers:
  - url: /
paths:
  /health:
    get:
      parameters:
        - name: count
          in: query
          required: true
          schema:
            $ref: '#/components/schemas/Count'
        - name: x-count
          in: header
          required: true
          schema:
            $ref: '#/components/schemas/Count'
      responses:
        '200':
          description: OK
  /body:
    post:
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/Payload'
      responses:
        '200':
          description: OK
components:
  schemas:
    Count:
      type: integer
    Payload:
      type: object
      required: [value]
      properties:
        value:
          type: integer
""")
        server = http.server.ThreadingHTTPServer(("127.0.0.1", 0), RuntimeApi)
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        env = os.environ.copy()
        env.update(AWS_LAMBDA_RUNTIME_API=f"127.0.0.1:{server.server_port}", _HANDLER=handler)
        with (config / "runtime.log").open("w+") as log:
            process = subprocess.Popen([str(binary), f"-Dlight-4j-config-dir={config}"],
                                       env=env, stdout=log, stderr=subprocess.STDOUT)
            try:
                endpoint, payload = received.get(timeout=45)
                assert endpoint.endswith("/native-smoke/" + expected_endpoint), endpoint
                return payload
            except Exception:
                log.seek(0)
                diagnostic = log.read()
                binary.with_name("native-runtime-smoke-failure.log").write_text(diagnostic)
                log.seek(0)
                raise AssertionError("Native Runtime smoke failed:\n" + log.read()[-12000:])
            finally:
                process.terminate()
                try:
                    process.wait(timeout=5)
                except subprocess.TimeoutExpired:
                    process.kill()
                    process.wait(timeout=5)
                server.shutdown()
                server.server_close()
                thread.join(timeout=5)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("binary", type=Path)
    parser.add_argument("--application-jar", type=Path,
                        help="Shaded runtime JAR providing framework config and status catalog (default: target/lambda-native-*.jar)")
    args = parser.parse_args()
    binary = args.binary.resolve(strict=True)
    jars = [args.application_jar] if args.application_jar else [
        jar for jar in (Path(__file__).resolve().parents[1] / "target").glob("lambda-native-*.jar")
        if not jar.name.endswith(("-sources.jar", "-javadoc.jar"))]
    if len(jars) != 1:
        parser.error("Expected one shaded runtime JAR; specify --application-jar explicitly")
    with zipfile.ZipFile(jars[0]) as application_jar:
        status_catalog = application_jar.read("config/status.yml")
        config_catalog = application_jar.read("config/config.yml")
    event = {"path": "/health", "httpMethod": "GET", "body": None,
             "isBase64Encoded": False, "headers": {"x-count": "7"}, "multiValueHeaders": {},
             "queryStringParameters": {"count": "7"}, "futureEventField": {"nested": True},
             "requestContext": {"requestId": "native-smoke", "identity": {"sourceIp": "127.0.0.1"}}}
    handler = "com.networknt.aws.lambda.app.LambdaApp"
    response = invoke(binary, status_catalog, handler, "response", event, config_catalog=config_catalog)
    assert response["statusCode"] == 200, response
    assert response["body"] == "OK", response
    assert response["isBase64Encoded"] is False, response
    body_event = event | {"path": "/body", "httpMethod": "POST", "body": '{"value":7}',
                          "headers": {"content-type": "application/json"}, "queryStringParameters": {}}
    body_response = invoke(binary, status_catalog, handler, "response", body_event, config_catalog=config_catalog)
    assert body_response["statusCode"] == 200, body_response
    assert body_response["body"] == "OK", body_response
    cases = [
        ("query", event | {"queryStringParameters": {"count": "invalid"}}, 400, "ERR11004"),
        ("header", event | {"headers": {"x-count": "invalid"}}, 400, "ERR11004"),
        ("body", body_event | {"body": '{"value":"invalid"}'}, 400, "ERR11004"),
        ("unknown path", event | {"path": "/missing"}, 404, "ERR10007"),
        ("method mismatch", event | {"httpMethod": "DELETE"}, 405, "ERR10008"),
    ]
    for name, invalid_event, status, code in cases:
        invalid = invoke(binary, status_catalog, handler, "response", invalid_event, config_catalog=config_catalog)
        assert invalid["statusCode"] == status, (name, invalid)
        assert json.loads(invalid["body"])["code"] == code, (name, invalid)
    error = invoke(binary, status_catalog, "native.smoke.MissingHandler", "error", event, config_catalog=config_catalog)
    assert "native.smoke.MissingHandler" in error["message"], error
    print("Native Runtime smoke passed: API Gateway binding, health response and builder error serialization")
    print("Native Runtime validation passed: unknown event fields and referenced query-schema rejection")
    print("Native Runtime validation passed: body and header schemas, unknown path and method mismatch")


if __name__ == "__main__":
    main()
