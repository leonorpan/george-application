#!/usr/bin/env bash

# Utility functions for potential use in other bash-scripts.
# Do:  source bin/_utils.sh  at top of any bash-files, then call them with back-quotes.
#  Ex.:  V=`get_version`


# Trips quotations marks (or any one character) from front and back of string.
function strip_quots() {
    cut -c 2- | rev | cut -c 2- | rev
}


# Extracts info from project.clj
function get_version () {
    echo `lein pprint :version` | strip_quots
}
function get_group () {
    echo `lein pprint :group` | strip_quots
}
function get_name () {
    echo `lein pprint :name` | strip_quots
}


#N=`get_name`
#echo "N: $N"
#V=`get_version`
#echo "V: $V"
#R=`dirname $0`
#echo $R
