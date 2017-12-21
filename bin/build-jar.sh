#!/usr/bin/env bash

echo "!! Updating version string in src/main/resources ..."
source bin/_utils.sh
V=`get_version`
VF=src/main/resources/george-version.txt
echo $V > $VF
cat $VF

echo "!! Building ..."
# Ensure any old compiled classed are removed first.
lein clean
lein uberjar