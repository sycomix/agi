#!/bin/bash

######################################################################
# Use this 'variables.sh' if you are running the system locally, 
# or if you are are deploying Docker containters from this local environment.
######################################################################


# ----------------------------------------
# AGI Home 
# ---------------------------------------
export AGI_HOME=~/Development/ProjectAGI/AGIEF/agi
#export AGI_HOME=/home/dave/workspace/agi.io/agi

# RUN Home
# export AGI_RUN_HOME=~/Development/ProjectAGI/AGIEF/runFolders/runImage 
export AGI_RUN_HOME=~/Development/ProjectAGI/AGIEF/experiment-definitions/mnist-gng-v1
#export AGI_RUN_HOME=/home/dave/workspace/agi.io/agi/resources/run-empty

# DATA Home
export AGI_DATA_HOME=~/Development/ProjectAGI/AGIEF/datasets/MNIST


# Database
export DB_PORT=5432
# export DB_HOST=localhost		# IMPORTANT!!!  DO NOT DEFINE THIS VARIABLE.   It will be defined by scripts at runtime, and we don't want it to get defined by sourcing this file

# ----------------------------------------
# Dependencies
# ---------------------------------------

# MAVEN
export MAVEN_BIN=/usr/local/bin/mvn
# export MAVEN_BIN=/home/dave/workspace/maven/apache-maven-3.3.3/bin/mvn

# Java
export JAVA_HOME=`/usr/libexec/java_home -v 1.8`
#export JAVA_HOME=/home/dave/workspace/agi.io/java/jdk1.8.0_60


# ----------------------------------------
# Set Path
# ----------------------------------------
export PATH=${JAVA_HOME}/bin:${PATH}
