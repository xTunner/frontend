process = () =>
  if @JasmineReporter.finished
    results = @JasmineReporter.results()
    results = (v for k,v of results)
    json = JSON.stringify(results)

    # attach it to the DOM, so the it can be retrieved by webdriver
    element = window.document.createElement("div")
    text = window.document.createTextNode(json)
    element.appendChild text
    element.id = "jasmine-results"
    window.document.body.appendChild element
  else
    setTimeout process, 100

try
  jasmine.getEnv().execute()
finally
  process()