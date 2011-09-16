class Handler(object):
  def __init__(self, definition):
    self.definition = definition

  def run(self, config):
    self._run_recurse(self.definition, config, config)

  def _run_recurse(self, definition, config, fullconfig):
    for k in sorted(definition.keys()):

      # ignore required
      if k not in config:
        if definition[k] == required:
          raise Exception("%s is required but not defined" % k)
        else:
          return # TODO: may miss some nested required

      if definition[k] == required:
        pass # it's needed later, but we don't do anything
      elif isinstance(definition[k], dict):
        self._run_recurse(definition[k], config[k], fullconfig)
      elif callable(definition[k]):
        definition[k](fullconfig, config, config[k])
      else:
        print definition
        print k
        print definition[k]
        raise Exception("wtf")





required = object()
