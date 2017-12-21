#!/usr/bin/env bash

case "$TRAVIS_SBT_VERSION" in
  0.13.x) SWITCH_SBT_VERSION=""        ;;
     1.x) SWITCH_SBT_VERSION="^^1.0.0" ;;
       *) echo >&2 "Aborting: Unknown TRAVIS_SBT_VERSION: $TRAVIS_SBT_VERSION"; exit 1; ;;
esac

PUBLISH=publishLocal
if [[ "$TRAVIS_SECURE_ENV_VARS" == "true" ]]; then
  if [[ "$TRAVIS_TAG" != "" ]]; then
    PUBLISH=publish
  fi
  if [[ "$TRAVIS_BRANCH" == "master" && "$TRAVIS_PULL_REQUEST" == "false" ]]; then
    PUBLISH=publish
  fi
fi

sbt "$SWITCH_SBT_VERSION" test mimaReportBinaryIssues "$PUBLISH"
