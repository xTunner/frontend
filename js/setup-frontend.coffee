###
stefon doesn't like comments at the end of the previous file
###

old_onload = document.onload
window.onload = () ->
  console.log('document onload')
  frontend.core.setup_BANG_()
  if old_onload?
    old_onload()
