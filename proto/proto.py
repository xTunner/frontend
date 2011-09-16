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

spec = yaml.load(file(filename))
print pprint.pformat(spec)

handlers.handle()
