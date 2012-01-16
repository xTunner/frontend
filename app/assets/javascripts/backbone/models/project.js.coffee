App.Models.Project = Backbone.Model.extend
  url: ->
    project = @get 'project'
    "/gh/#{project}/"

  urlRoot: "/"