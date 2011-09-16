#!/usr/bin/env python

import sys
import pprint
import os

import repos
import yaml
import handlers

# Read the yaml file
# Download the code
# switch to the root directory

filename = sys.argv[1]
print "Reading from " + filename + " :"

config = yaml.load(file(filename))
print pprint.pformat(config)

handlers.handle(config)
