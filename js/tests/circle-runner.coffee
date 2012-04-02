process = () =>
  if @JasmineReporter.finished
    results = @JasmineReporter.results()
    json = JSON.stringify(results)

    # attach it to the DOM, so the it can be retrieved by webdriver
    element = @document.createElement("div")
    text = @document.createTextNode(json)
    element.appendChild text
    element.id = "jasmine-results"
    @document.body.appendChild element
  else
    setTimeout process, 100

try
  @jasmine.getEnv().execute()
finally
  process()