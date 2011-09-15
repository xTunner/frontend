#!/usr/bin/env python

import sys
import pprint
import os

import yaml
import repos

# Read the yaml file
# Download the code
# switch to the root directory

filename = sys.argv[1]
print "Reading from " + filename + " :"

spec = yaml.load(file(filename))
print pprint.pformat(spec)

url = spec['repo']['url']
backend = spec['repo']['backend']
dirname = os.path.basename(url)

print "Pulling %s to %s" % (url, dirname)
repo = repos.Repo(backend, url)
repo.clone(dirname)

print "Switching to " + dirname
os.chdir(dirname)

# subdir
subdir = spec['code']['subdir']
if subdir:
  os.chdir(subdir)

# just do autotools for now
autotools.run(spec)

