#!/bin/sh
RELEASE_VERSION_BUMP=true sbt -mem 2048 compile +test 'release cross with-defaults'
RELEASE_PUBLISH=true sbt 'release cross with-defaults'