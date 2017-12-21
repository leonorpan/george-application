#!/usr/bin/env bash

# Ensure any old compiled classed are removed first.

lein clean
lein run $@