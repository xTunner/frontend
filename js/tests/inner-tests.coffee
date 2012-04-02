j = jasmine.getEnv()

j.describe "trailingSlash", ->
  j.it 'should remove trailing slashes', ->
    @expect(stripTrailingSlash('/gh/a/b/')).toEqual '/gh/a/b'

  j.it 'shouldnt remove non-trailing slashes', ->
    @expect(stripTrailingSlash('/gh/a/b/edit')).toEqual '/gh/a/b/edit'

  j.it 'shouldnt delete root variable', ->
    @expect(stripTrailingSlash('/')).toEqual '/'
