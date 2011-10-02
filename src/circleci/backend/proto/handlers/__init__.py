"""Handlers for builtin types"""

import default

def handle(config):
  for module in [default]:
    module.handler.run(config)
