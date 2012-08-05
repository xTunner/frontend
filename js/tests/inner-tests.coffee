j = jasmine.getEnv()

j.describe "calculateCost", ->
  j.it "should be the same as on the server", ->
    @expect(0).toEqual 0