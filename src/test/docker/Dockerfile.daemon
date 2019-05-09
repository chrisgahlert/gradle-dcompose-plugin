FROM docker:18.09.6-dind

COPY testreg.crt /etc/docker/certs.d/testreg:5000/ca.crt
COPY hub.crt /etc/docker/certs.d/hub:5000/ca.crt

CMD ["--registry-mirror=https://hub:5000"]
