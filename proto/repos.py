import os
import string
import tempfile

from calls import exe

class Repo(object):
  def __init__(self, backend, url):
    self.backend = backend
    self.url = url
    self._store = os.path.join(
        tempfile.gettempdir(),
        "repos_cache",
        self._convert_to_filename(url))

  def _convert_to_filename(self, url):
    valid_chars = "-_.%s%s" % (string.ascii_letters, string.digits)
    return "".join([c if c in valid_chars else '_' for c in url])

  def _is_cloned(self):
    return os.is_dir(self._store)

  def clone(self, target):
    exit = exe("git clone -- %s %s " % (self.url, self._store))
    if exit != 0:
      exe("git pull origin master", self._store)

    if os.path.exists(target):
      os.unlink(target)

    os.symlink(self._store, target)


