App.Views.EditProject = Backbone.View.extend

  events:
    "submit form": "save"


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
    Backbone.ModelBinding.bind(@)
    @
