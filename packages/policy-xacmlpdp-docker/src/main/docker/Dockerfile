FROM onapmulti/policy-common-alpine:1.4.0

LABEL maintainer="Policy Team"

ARG POLICY_LOGS=/var/log/onap/policy/pdpx

ENV POLICY_LOGS=$POLICY_LOGS

ENV POLICY_HOME=$POLICY_HOME/pdpx
RUN apk add --no-cache mariadb-client
RUN mkdir -p $POLICY_HOME $POLICY_LOGS $POLICY_HOME/etc/ssl $POLICY_HOME/bin $POLICY_HOME/apps && \
    chown -R policy:policy $POLICY_HOME $POLICY_LOGS && mkdir /packages

COPY /maven/* /packages
RUN tar xvfz /packages/policy-xacmlpdp.tar.gz --directory $POLICY_HOME && \
    rm /packages/policy-xacmlpdp.tar.gz

WORKDIR $POLICY_HOME
COPY policy-pdpx.sh  bin/.
RUN chown -R policy:policy * && chmod 755 bin/*.sh && chmod 755 mysql/bin/*.sh

USER policy
WORKDIR $POLICY_HOME/bin
ENTRYPOINT [ "bash", "./policy-pdpx.sh" ]
