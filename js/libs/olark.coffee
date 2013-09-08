CI.olark =
  disable: ->
    try
      olark 'api.box.hide'
    catch error
      console.error 'Tried to hide olark, but it threw:', error
