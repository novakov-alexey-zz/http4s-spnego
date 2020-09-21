#!/bin/sh
tag=$1

mill all test-server.{compile,assembly}
docker build -t test-server . -f test-server/Dockerfile
docker tag test-server alexeyn/test-server:$tag
docker push alexeyn/test-server:$tag