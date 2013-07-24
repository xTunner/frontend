# Update this observable every second so that we can get updating durations
# and intervals
window.updator = ko.observable(0)

setUpdate = () ->
  console.log window.updator.getSubscriptionsCount()
  window.updator(window.updator() + 1)
  setTimeout setUpdate, 1000

setUpdate()
