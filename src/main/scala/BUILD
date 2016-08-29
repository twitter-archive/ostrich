scala_library(name='scala',
  dependencies=[
    '3rdparty/jvm/com/fasterxml/jackson/core:jackson-annotations',
    '3rdparty/jvm/com/fasterxml/jackson/core:jackson-databind',
    '3rdparty/jvm/com/fasterxml/jackson/module:jackson-module-scala',
    'util/util-eval',
    'util/util-logging',
    'util/util-jvm',
    '3rdparty/jvm/com/fasterxml/jackson/core:jackson-core',
    'util/util-core/src/main/scala:scala',
    'util/util-registry/src/main/scala:scala',
  ],
  sources=rglobs('*.scala'),
  resources=[
    'ostrich/src/main/resources'
  ],
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
