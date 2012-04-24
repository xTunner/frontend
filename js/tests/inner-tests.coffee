j = jasmine.getEnv()

j.describe "trailingSlash", ->
  j.it 'should remove trailing slashes', ->
    @expect(stripTrailingSlash('/gh/a/b/')).toEqual '/gh/a/b'

  j.it 'shouldnt remove non-trailing slashes', ->
    @expect(stripTrailingSlash('/gh/a/b/edit')).toEqual '/gh/a/b/edit'

  j.it 'shouldnt delete root variable', ->
    @expect(stripTrailingSlash('/')).toEqual '/'

ps = (vals={}) ->
  vals.vcs_url ?= "https://github.com/a/b"
  vals.status ?= "available"
  vals.compile ?= null
  vals.setup ?= null
  vals.dependencies ?= null
  vals.test ?= null
  vals.extra ?= null
  vals.hipchat_api_token ?= null
  vals.hipchat_room ?= null
  new ProjectSettings(vals)

j.describe "has_settings", ->
  j.it 'should be false for all null', ->
    @expect(ps().has_settings()).toBeFalsy()

  j.it 'should be false for all empty', ->
    @expect(ps({setup: "", extra: ""}).has_settings()).toBeFalsy()

  j.it 'should be false for all empty', ->
    @expect(ps({setup: "", test: "", dependencies: "", extra: ""}).has_settings()).toBeFalsy()

  j.it 'should be true for any at all', ->
    @expect(ps({setup: "asd"}).has_settings()).toBeTruthy()