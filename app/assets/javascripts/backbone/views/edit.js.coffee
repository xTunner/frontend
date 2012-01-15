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
#        Backbone.history.navigate "gh/#{ @model.project }/edit", true

      failure: ->
        new App.Views.Error()

    @model.save instance, callbacks


  el: '#el'


  render: ->
    context = {}
    partials = {}
    html = HoganTemplates["backbone/templates/projects/spec"].render(context, partials)
    $(@el).html(html)
    @
