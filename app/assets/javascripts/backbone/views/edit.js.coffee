App.Views.EditProject = Backbone.View.extend

  events:
    "submit form": "save"
    "click a": "click_link"

  initialize: ->
    @options or= {}

  click_link: (e) ->
    e.preventDefault()
    @set_page $(e.target).attr('href')

  set_page: (page) ->
    # handle both with and without a hash
    if page[0] == '#'
      page = page.substring 1

    @options.page = page
    target = (if page then "##{page}" else "")
    App.router.navigate target
    @render()


  save: (e) =>
    e.preventDefault()
    @model.save @model
    # no need to rerender I think


  el: '#el'


  render: ->
    html = JST["backbone/templates/projects/edit"] @model
    $(@el).html(html)

    page = @options.page or "settings"
    nested = JST["backbone/templates/projects/#{page}"] @model
    $(@el).find("#el-content").html(nested)



    @delegateEvents()
    Backbone.ModelBinding.bind(@)
    @
