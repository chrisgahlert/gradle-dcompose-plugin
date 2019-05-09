FROM registry:2.7.1

COPY testreg.* /certs/
ENV REGISTRY_HTTP_TLS_CERTIFICATE /certs/testreg.crt
ENV REGISTRY_HTTP_TLS_KEY /certs/testreg.key

COPY htpasswd /auth/
ENV REGISTRY_AUTH htpasswd
ENV REGISTRY_AUTH_HTPASSWD_REALM Registry Realm
ENV REGISTRY_AUTH_HTPASSWD_PATH /auth/htpasswd