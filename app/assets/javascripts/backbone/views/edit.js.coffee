App.Views.EditProject = Backbone.View.extend

  events:
    "submit form.spec_form": "save_specs"
    "submit form.hook_form": "save_hooks"
    "click #reset": "reset_specs"


  initialize: ->
    @options or= {}


  set_page: (page) ->
    # handle both with and without a hash
    if page[0] == '#'
      page = page.substring 1

    @options.page = page
    App.router.navigate page
    @render()


  save: (event, btn, redirect, keys) ->
    event.preventDefault()
    btn.button 'loading'

    # Only push a subset of the model to the server: we don't want to save specs
    # for hooks and vice-versa, and splitting it up into multiple models is too
    # hard.
    keys.push "project"
    keys.push "_id"

    m = @model.clone()
    for k of m.attributes
      m.unset k, {silent: true} if k not in keys

    m.save {},
      success: ->
        btn.button 'reset'
        window.location = redirect
      error: ->
        btn.button 'reset'
        alert "Error in saving project. Please try again. If it persists, please contact Circle."


  save_specs: (e) ->
    @save e, $(e.target.save_specs), "#settings",
     ["setup", "dependencies", "compile", "test", "extra"]

  save_hooks: (e) ->
    @save e, $(e.target.save_hooks), "#hooks",
      ["hipchat_room", "hipchat_api_token"]

  el: '#el'

  reset_specs: (e) ->
    @model.set
      "setup": ""
      "compile": ""
      "test": ""
      "extra": ""
      "dependencies": ""

    @save_specs e
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
