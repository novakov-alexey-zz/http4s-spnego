RELEASE_VERSION_BUMP=true sbt 'project http4s-spnego' compile test 'release with-defaults'
RELEASE_PUBLISH=true sbt 'release with-defaults'