process = () =>
  results = @JasmineReporter.results()
  json = JSON.stringify(results)

  # attach it to the DOM, so the it can be retrieved by webdriver
  element = @document.createElement("div")
  text = @document.createTextNode(json)
  element.appendChild text
  element.id = "jasmine-results"
  @document.body.appendChild element

try
  @jasmine.getEnv().execute()
catch e
finally
  process()