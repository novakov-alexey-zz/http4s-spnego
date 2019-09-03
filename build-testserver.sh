#!/bin/sh
sbt 'project test-server' clean compile stage
docker build -t test-server test-server/