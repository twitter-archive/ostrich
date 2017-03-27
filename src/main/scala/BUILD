scala_library(
  dependencies=[
    '3rdparty/jvm/com/fasterxml/jackson/core:jackson-annotations',
    '3rdparty/jvm/com/fasterxml/jackson/core:jackson-core',
    '3rdparty/jvm/com/fasterxml/jackson/core:jackson-databind',
    '3rdparty/jvm/com/fasterxml/jackson/module:jackson-module-scala',
    'ostrich/src/main/resources',
    'util/util-core/src/main/scala:scala',
    'util/util-eval',
    'util/util-jvm',
    'util/util-logging',
    'util/util-registry/src/main/scala:scala',
  ],
  sources=rglobs('*.scala'),
  provides = scala_artifact(
    org = 'com.twitter',
    name = 'ostrich',
    repo = artifactory,
  ),
  tags = {
    # Prefer TwitterServer and finagle-stats.
    'deprecated',
  },
)
