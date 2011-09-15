import os
import string

import vcs

import tempfile

class Repo(object):
  def __init__(self, backend, url):
    self.backend = backend
    self.url = url
    cachedir = self._convert_to_filename(url)
    self._store = os.path.join(tempfile.gettempdir(), "repos_cache", cachedir)

  def _convert_to_filename(self, url):
    valid_chars = "-_.%s%s" % (string.ascii_letters, string.digits)
    return "".join([c if c in valid_chars else '_' for c in url])

  def _mkdirs(self, dir):
    try:
        os.makedirs(dir)
    except OSError as exc: # Python >2.5
        if exc.errno == errno.EEXIST:
            pass
        else: raise

  def _is_cloned(self):
    return os.is_dir(self._store)

  def clone(self, target):
    print self._store
    self._mkdirs(self._store)
    vc = vcs.get_backend(self.backend)
    vc(repo_path=self._store, src_url=self.url, create=True, update_after_clone=True)
    vc(repo_path=target, src_url=self._store, create=True, update_after_clone=True)


