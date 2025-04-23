FROM quay.prod-openshift-na.hybrid.sunlifecorp.com/devops/linux-jnlp-graalvm17:25.04-2

USER root

RUN mkdir -p /apps/lnl
COPY . / /apps/lnl/