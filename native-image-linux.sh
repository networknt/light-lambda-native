#!/usr/bin/env bash
set -euo pipefail

cd -- "$(dirname -- "${BASH_SOURCE[0]}")"

# Build the shaded JAR, then obtain its name and Java level from Maven.
mvn clean install
metadata_dir=$(mktemp -d)
trap 'rm -rf -- "$metadata_dir"' EXIT
mvn -q help:evaluate -Dexpression=project.build.finalName \
    -Doutput="$metadata_dir/final-name"
mvn -q help:evaluate -Dexpression=maven.compiler.target \
    -Doutput="$metadata_dir/java-version"
jar_name=$(cat "$metadata_dir/final-name")
java_version=$(cat "$metadata_dir/java-version")
[[ "$java_version" =~ ^[0-9]+$ ]] || { echo "Invalid Maven compiler target: $java_version" >&2; exit 1; }
test -s "target/$jar_name.jar"

# Override with a pinned GraalVM image compatible with the POM's Java level.
graalvm_image=${GRAALVM_IMAGE:-ghcr.io/graalvm/native-image-community:$java_version}
mkdir -p target/custom-runtime
docker run --rm --user "$(id -u):$(id -g)" \
    -v "$PWD:/workspace" -w /workspace --entrypoint native-image \
    "$graalvm_image" \
    --enable-url-protocols=http,https \
    --no-fallback \
    --verbose \
    --initialize-at-build-time=org.slf4j,ch.qos.logback \
    --initialize-at-run-time=io.netty \
    -H:ReflectionConfigurationFiles=/workspace/reflect.json \
    -H:ResourceConfigurationFiles=/workspace/resource-config.json \
    -H:+ReportExceptionStackTraces \
    -jar "/workspace/target/$jar_name.jar" \
    -o /workspace/target/custom-runtime/lambda-native

# Never package a bootstrap-only archive after a failed native build.
test -s target/custom-runtime/lambda-native
test -x target/custom-runtime/lambda-native
cp bootstrap target/custom-runtime/bootstrap
chmod +x target/custom-runtime/bootstrap
(
    cd target/custom-runtime
    zip -X ../lambda-native-custom-runtime.zip lambda-native bootstrap
)
mv target/lambda-native-custom-runtime.zip lambda-native-custom-runtime.zip
echo "Created $PWD/lambda-native-custom-runtime.zip"
