CI.outer.Changelog = class Changelog extends CI.outer.Page
  constructor: ->
    super
    @name = "changelog"
    @entries = ko.observableArray([])

  render: (cx) =>
    @fetchContent()
    super cx

  fetchContent: () =>
    $.ajax
      url: "/changelog.rss"
      type: "GET"
      dataType: "xml"
      success: (results) =>
        @setEntries(results)

  setEntries: (document) =>
    list = for i in $(document).find("item")
      elem = $(i)
      title: elem.find('title').text()
      description: elem.find('description').text()
      link: elem.find('link').text()
      author: elem.find('author').text()
      pubDate: elem.find('pubDate').text()
      guid: elem.find('guid').text()
      categories:
        for c in elem.find('category')
          $(c).text()
    @entries(list)