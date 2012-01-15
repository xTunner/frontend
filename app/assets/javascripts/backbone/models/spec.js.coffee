App.Models.Spec = Backbone.Model.extend
  url: ->
    project = @get 'project'
    "/gh/#{project}/edit"

  urlRoot: "/"