j = @jasmine.getEnv()

j.describe "circle test suite", ->
  j.it 'should increment a variable', ->
    @expect(1).toEqual 1
