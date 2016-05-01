#!/bin/bash

variables_file=${VARIABLES_FILE:-"variables.sh"}
echo "Using variables file = \"$variables_file\""
source $(dirname $0)/../$variables_file


echo "------- Build and install libs -------"
./build-libs.sh

echo "------- Build the core and experimental framework -------"
cd $AGI_HOME/code/core
$MAVEN_BIN clean package