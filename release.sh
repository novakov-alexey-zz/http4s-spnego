RELEASE_VERSION_BUMP=true sbt compile +test 'release cross with-defaults'
RELEASE_PUBLISH=true sbt 'release cross with-defaults'