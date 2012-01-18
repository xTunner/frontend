App.Views.EditProject = Backbone.View.extend

  events:
    "submit form": "save"


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
      failure: ->
        btn.button 'failure!'


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
