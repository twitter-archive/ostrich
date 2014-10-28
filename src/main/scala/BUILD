scala_library(name='scala',
  dependencies=[
    'scala-json',
    'util/util-core',
    'util/util-eval',
    'util/util-logging',
    'util/util-jvm'
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
)
