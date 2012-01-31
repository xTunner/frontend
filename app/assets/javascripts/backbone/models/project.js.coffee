App.Models.Project = Backbone.Model.extend
  url: ->
    project = @get 'project'
    "/gh/#{project}"

  urlRoot: "/"

  idAttribute: "_id"

  github_url: ->
    "https://github.com/#{@get 'project'}"

  build_url: ->
    @url() + '/build'

  is_inferred: ->
    full_spec = @get "setup"
    full_spec += @get "dependencies"
    full_spec += @get "compile"
    full_spec += @get "test"
    full_spec += @get "extra"
    "" == full_spec