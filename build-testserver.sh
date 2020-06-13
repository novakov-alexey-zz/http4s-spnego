#!/bin/sh
mill test-server.compile test-server.packageIt
docker build -t test-server . -f test-server/Dockerfile