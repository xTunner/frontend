App.Views.Edit = Backbone.View.extend

  events:
    "submit and test": "save"


  initialize: ->
    @render()


  save: ->
    msg = if @model.isNew() then "created" else "saved"

    instance =
      x: tet
      b: test2

    callbacks =

      success: (model, resp) ->
        new App.Views.Notice { message: msg }
        @model = model
        @render()
        @delegateEvents()
        Backbone.history.saveLocation "gh/#{ @model.project }/edit"

      failure: ->
        new App.Views.Error()

    @model.save instance, callbacks


  render: ->
    $(@el).html("test")
    $(".content").html(@el)