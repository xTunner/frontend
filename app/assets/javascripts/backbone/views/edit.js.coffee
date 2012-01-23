App.Views.EditProject = Backbone.View.extend

  events:
    "submit form": "save"
    "click #reset": "reset"


  initialize: ->
    @options or= {}


  set_page: (page) ->
    # handle both with and without a hash
    if page[0] == '#'
      page = page.substring 1

    @options.page = page
    App.router.navigate page
    @render()


  save: (e) ->
    e.preventDefault()

    btn = $(e.target.commit)
    btn.button 'loading'

    @model.save @model,
      success: ->
        btn.button 'reset'
        window.location = "#settings"
      failure: ->
        btn.button 'failure!'
        alert "Error in saving project. Please try again. If it persists, please contact help."


  el: '#el'

  reset: (e) ->
    @model.set
      "setup": ""
      "compile": ""
      "test": ""
      "extra": ""
      "dependencies": ""

    @save e
    @render()


  render: ->
    html = JST["backbone/templates/projects/edit"] @model
    $(@el).html(html)

    page = @options.page or "settings"
    nested = JST["backbone/templates/projects/#{page}"] @model
    $(@el).find("#el-content").html(nested)

    @delegateEvents()
    Backbone.ModelBinding.bind(@)
    @
