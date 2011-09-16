import os

from generic import Handler, required
import repos


def url(config, parent, val):
  url = parent['url']
  dirname = os.path.basename(url)
  print "Pulling %s to %s" % (url, dirname)
  repo = repos.Repo(parent['backend'], val)
  repo.clone(dirname)

def subdir(config, parent, val):
  os.chdir(val)


handler = Handler({
  'repo': {
    'backend': required,
    'url': url
  },
  'code': {
    'subdir': subdir
  }
})
