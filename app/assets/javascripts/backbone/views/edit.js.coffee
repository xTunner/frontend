App.Views.EditProject = Backbone.View.extend

  events:
    "submit form": "save"
    "click a": "changePage"

  changePage: (e) ->
    e.preventDefault()
    href = "#{window.location.pathname}#{$(e.target).attr('href')}"
    App.router.navigate(href, true);


  initialize: ->
    @render()


  save: (e) ->
    e.preventDefault()
    @model.save @model
    # no need to rerender I think


  el: '#el'


  render: ->
    html = JST["backbone/templates/projects/edit"] @model
    $(@el).html(html)

    nested = JST["backbone/templates/projects/#{@options.page}"] @model
    $(@el).find("#el-content").html(nested)

    @delegateEvents()
    Backbone.ModelBinding.bind(@)
    @
