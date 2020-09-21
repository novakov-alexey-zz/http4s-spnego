#!/usr/bin/env bash
namespace=""
if [ -z "$1" ]
then
      echo "Usage: <namespace>"
      exit 1
else
      namespace=$1
      echo "using $namespace as namespace"
fi

SERVER_HOSTNAME=testserver:8080
curl -v -k --negotiate -u : -b ~/cookiejar.txt -c ~/cookiejar.txt  -H "Origin: http://example.com" ${SERVER_HOSTNAME}