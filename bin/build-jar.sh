#!/usr/bin/env bash

export LEIN_SNAPSHOTS_IN_RELEASE=true
source bin/_utils.sh

echo "!! Updating version string in src/main/resources ..."
V=`get_version`
VF=src/main/resources/george-version.txt
echo -n $V > $VF
cat $VF
echo

echo "!! Building ..."
# Ensure any old compiled classed are removed first.
lein clean
lein uberjar