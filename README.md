# [sbt-whitesource][] [![travis-badge][]][travis]

[sbt-whitesource]: https://github.com/lightbend/sbt-whitesource
[travis]:       https://travis-ci.org/lightbend/sbt-whitesource
[travis-badge]: https://travis-ci.org/lightbend/sbt-whitesource.svg?branch=master

`sbt-whitesource` is an [sbt][] plugin to keep your WhiteSource project up to date. In WhiteSource terms it is
an [external update agent][whitesource/agents] for sbt.

[sbt]: http://www.scala-sbt.org/
[whitesource/agents]: https://github.com/whitesource/agents

## Setup

1. Add the sbt plugin to `project/plugins.sbt`, like so:

```scala
addSbtPlugin("com.lightbend" % "sbt-whitesource" % "0.1.11")
```

[ws-Integrate]: https://saas.whitesourcesoftware.com/Wss/WSS.html#!adminOrganization_integration

2. Set the Organization API Key (from WhiteSource's [Integrate][ws-Integrate] page) by appending to `credentials` in `~/.sbt/0.13/credentials.sbt`, like so:

```scala
credentials += Credentials(realm = "whitesource", host = "whitesourcesoftware.com",
  userName = "", passwd = "********" /* Organization API Key */)
```

Note: on Jenkins you can inject that file, while on Travis CI you'd have to use its [Environment Variables](https://docs.travis-ci.com/user/environment-variables/) feature.

3. In your `build.sbt` set the product name and the aggregate project name and token, also available from WhiteSource's [Integrate][ws-Integrate] page:

```scala
whitesourceProduct in ThisBuild               := "Lightbend Reactive Platform"
whitesourceAggregateProjectName in ThisBuild  := "akka-2.5"
whitesourceAggregateProjectToken in ThisBuild := "1234abc-******"
```

## Usage

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

## Maintenance

This project is active, it's not supported by [Lightbend's subscription](https://www.lightbend.com/subscription), and it's maintained by the [Tooling Team](https://github.com/orgs/lightbend/teams/tooling-team) at Lightbend.
