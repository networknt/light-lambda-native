"""Script boundary tests; Maven and Docker are stubbed, ZIP packaging is real."""
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest
from zipfile import ZipFile


ROOT = Path(__file__).resolve().parents[1]


class NativeImageScriptTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.bin = self.root / "bin"
        self.bin.mkdir()
        for name in ("native-image-linux.sh", "bootstrap"):
            shutil.copy(ROOT / name, self.root / name)
        self.stub("mvn", '''#!/usr/bin/env bash
set -eu
if [[ "$1" == clean ]]; then
    [[ "${FAIL_STAGE:-}" != maven ]] || exit 21
    mkdir -p target
    printf jar > target/customer-runtime-9.9.jar
else
    for arg in "$@"; do
        case "$arg" in
            -Dexpression=*) expression=${arg#*=} ;;
            -Doutput=*) output=${arg#*=} ;;
        esac
    done
    case "$expression" in
        project.build.finalName) printf customer-runtime-9.9 > "$output" ;;
        maven.compiler.target) printf 25 > "$output" ;;
        *) exit 22 ;;
    esac
fi
''')
        self.stub("docker", '''#!/usr/bin/env bash
set -eu
printf '%s\\n' "$@" > docker-args
[[ "${FAIL_STAGE:-}" != docker ]] || exit 23
[[ "${FAIL_STAGE:-}" != missing ]] || exit 0
printf '#!/bin/sh\\nexit 0\\n' > target/custom-runtime/lambda-native
chmod +x target/custom-runtime/lambda-native
''')
        self.env = dict(os.environ, PATH=f"{self.bin}:{os.environ['PATH']}")
        self.env.pop("GRAALVM_IMAGE", None)

    def stub(self, name, source):
        path = self.bin / name
        path.write_text(source)
        path.chmod(0o755)

    def run_script(self, failure=""):
        return subprocess.run(
            ["bash", str(self.root / "native-image-linux.sh")],
            cwd="/tmp", env=dict(self.env, FAIL_STAGE=failure),
            capture_output=True, text=True,
        )

    def test_packages_pom_named_jar_with_https_and_java_25(self):
        result = self.run_script()
        self.assertEqual(result.returncode, 0, result.stderr)
        args = (self.root / "docker-args").read_text().splitlines()
        self.assertIn("ghcr.io/graalvm/native-image-community:25", args)
        self.assertIn("/workspace/target/customer-runtime-9.9.jar", args)
        self.assertIn("--enable-url-protocols=http,https", args)
        with ZipFile(self.root / "lambda-native-custom-runtime.zip") as archive:
            self.assertEqual(set(archive.namelist()), {"lambda-native", "bootstrap"})
            for info in archive.infolist():
                self.assertTrue((info.external_attr >> 16) & 0o111)
        result = subprocess.run(
            ["sh", "bootstrap"], cwd=self.root / "target/custom-runtime",
            capture_output=True, text=True,
        )
        self.assertEqual(result.returncode, 0, result.stderr)

    def test_image_override(self):
        self.env["GRAALVM_IMAGE"] = "example/pinned-graalvm:25"
        self.assertEqual(self.run_script().returncode, 0)
        self.assertIn("example/pinned-graalvm:25", (self.root / "docker-args").read_text())

    def test_failures_never_publish_bootstrap_only_zip(self):
        for stage in ("maven", "docker", "missing"):
            with self.subTest(stage=stage):
                shutil.rmtree(self.root / "target", ignore_errors=True)
                result = self.run_script(stage)
                self.assertNotEqual(result.returncode, 0)
                self.assertFalse((self.root / "lambda-native-custom-runtime.zip").exists())
                self.assertNotIn("Created ", result.stdout)

    def test_zip_failure_preserves_previous_package(self):
        self.stub("zip", "#!/bin/sh\nexit 24\n")
        previous = self.root / "lambda-native-custom-runtime.zip"
        previous.write_bytes(b"previous package")
        result = self.run_script()
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual(previous.read_bytes(), b"previous package")
        self.assertNotIn("Created ", result.stdout)


if __name__ == "__main__":
    unittest.main()
