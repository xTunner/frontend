import errno
import os


def exe(cmd, dir=None):
  try:
    if dir:
      cwd = os.getcwd()
      _mkdirs(dir)
      os.chdir(dir)
  finally:
    if dir:
      os.chdir(cwd)


def _mkdirs(dir):
  """Make a direcory and all it's parents, don't fail if it already exists"""
  try:
      os.makedirs(dir)
  except OSError as exc: # Python >2.5
      if exc.errno == errno.EEXIST:
          pass
      else: raise


