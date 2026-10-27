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


def invoke(binary, handler, expected_endpoint, count="7"):
    received = queue.Queue()
    event = {"path": "/health", "httpMethod": "GET", "body": None,
             "isBase64Encoded": False, "headers": {}, "multiValueHeaders": {},
             "queryStringParameters": {"count": count}, "futureEventField": {"nested": True},
             "requestContext": {"requestId": "native-smoke", "identity": {"sourceIp": "127.0.0.1"}}}

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
defaultHandlers: [health]
""")
        (config / "health.yml").write_text("enabled: true\ndownstreamEnabled: false\n")
        (config / "lambda-app.yml").write_text("lambdaAppId: native-smoke\nencodeBase64Request: false\nencodeBase64Response: false\n")
        (config / "values.yml").write_text("{}\n")
        (config / "service.yml").write_text("singletons: []\n")
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
      responses:
        '200':
          description: OK
components:
  schemas:
    Count:
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
    args = parser.parse_args()
    binary = args.binary.resolve(strict=True)
    response = invoke(binary, "com.networknt.aws.lambda.app.LambdaApp", "response")
    assert response["statusCode"] == 200, response
    assert response["body"] == "OK", response
    assert response["isBase64Encoded"] is False, response
    invalid = invoke(binary, "com.networknt.aws.lambda.app.LambdaApp", "response", count="invalid")
    assert invalid["statusCode"] == 400, invalid
    assert "ERR11004" in invalid["body"], invalid
    error = invoke(binary, "native.smoke.MissingHandler", "error")
    assert "native.smoke.MissingHandler" in error["message"], error
    print("Native Runtime smoke passed: API Gateway binding, health response and builder error serialization")
    print("Native Runtime validation passed: unknown event fields and referenced query-schema rejection")


if __name__ == "__main__":
    main()
