j = jasmine.getEnv()
t = TestTargets # combat coffeescript scope

testBilling = (c, p, $, e) ->
  @expect((new t.Billing()).calculateCost({price: $}, c, p)).toEqual e

j.describe "calculateCost", ->
  j.it "should be correct for log2", ->
    @expect(t.log2 2).toEqual 1
    @expect(t.log2 4).toEqual 2
    @expect(t.log2 8).toEqual 3
    @expect(t.log2 3).toEqual 1.5849625007211563

  j.it "should be the same as on the server", ->
    testBilling(1, 1, 19, 19)
    testBilling(1, 1, 49, 49)
    testBilling(1, 2, 149, 149)
    testBilling(0, 0, 19, 19)
    testBilling(0, 0, 49, 49)
    testBilling(0, 0, 149, 149)
    testBilling(2, 1, 19, 68)
    testBilling(4, 1, 49, 196)
    testBilling(4, 1, 149, 296)
    testBilling(4, 2, 149, 296)
    testBilling(4, 4, 149, 395)
    testBilling(4, 8, 149, 494)
    testBilling(4, 5, 149, 427)
