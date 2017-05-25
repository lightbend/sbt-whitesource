# [sbt-whitesource][]

[sbt-whitesource]: https://github.com/typesafehub/sbt-whitesource

`sbt-whitesource` is an [sbt][] plugin to keep your WhiteSource project up to date. In WhiteSource terms it is
an [external update agent][whitesource/agents] for sbt.

[sbt]: http://www.scala-sbt.org/
[whitesource/agents]: https://github.com/whitesource/agents

## Setup

First, ensure you have permission to read from Lightbend's private Bintray repositories.

Then add this to `project/plugins.sbt` (or `project/whitesource.sbt` if you prefer one file per plugin):

    resolvers += Resolver.bintrayIvyRepo("typesafe", "internal-ivy-releases")

    addSbtPlugin("com.lightbend" % "sbt-whitesource" % "0.1.0")

Next append to `credentials` in `~/.sbt/0.13/credentials.sbt`:

```scala
credentials += Credentials(realm = "whitesource", host = "whitesourcesoftware.com",
  userName = "", passwd = "********" /* Organization API Key */)
```

## usage

Run `whitesourceUpdate` task to upload your projects' info to WhiteSource.

### Details

This plugin is a port of [whitesource-maven-plugin][] to sbt, providing very similar options and features.

[whitesource-maven-plugin]: https://github.com/whitesource/maven-plugin

## Licence

Copyright 2017 Lightbend, Inc.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
