handler = Handler({
  repo: {
    backend: required,

    url: def url(spec, parent, val):
      print "Pulling %s to %s" % (url, dirname)
      dirname = os.path.basename(parent['url'])
      repo = repos.Repo(parent['backend'], val)
      repo.clone(dirname)
      
  code: {
    subdir: def subdir(spec, subdir):
      os.chdir(subdir)
    }
  }
})
