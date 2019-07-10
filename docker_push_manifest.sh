#!/bin/bash
TAG=''
case $1 in

  'dublin')
    TAG='1.4.0'
    ;;
  *)
    TAG='latest' ;;
esac
./docker_manifest.sh policy-xacml-pdp $TAG
HOST_ARCH='amd64'
if [ "$(uname -m)" == 'aarch64' ]
then
    HOST_ARCH='arm64'
fi
MT_RELEASE='v0.9.0'
wget https://github.com/estesp/manifest-tool/releases/download/${MT_RELEASE}/manifest-tool-linux-${HOST_ARCH} -O ./manifest-tool
chmod u+x manifest-tool
./manifest-tool push from-spec policy-xacml-pdp_$TAG.yaml
