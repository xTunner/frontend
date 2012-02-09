#= require sinon

describe "pretty_wording", ->

  beforeEach ->
    # party like it's Wednesday 21th December, 2011
    wednesday = new Date(2011, 11, 21, 19, 28, 32)
    clock = sinon.useFakeTimers(wednesday.getTime(), "Date")



  it "should return known values", ->
    f = pretty_wording
    m = moment
    e = expect

    e(f(m(new Date(2010, 11, 15, 16, 10, 13)))).toEqual "Dec 15, 2010"
    e(f(m(new Date(2011,  0,  6, 14,  0, 13)))).toEqual "Jan 6 at 2:00pm"
    e(f(m(new Date(2011, 11, 12, 16, 10, 59)))).toEqual "Dec 12 at 4:10pm"
    e(f(m(new Date(2011, 11, 19, 11, 45, 45)))).toEqual "Monday at 11:45am"
    e(f(m(new Date(2011, 11, 20)))).toEqual "Yesterday at 12:00am"
    e(f(m(new Date(2011, 11, 21, 18,  0, 13)))).toEqual "an hour ago (6:00pm)"
    e(f(m(new Date(2011, 11, 21, 17,  0, 13)))).toEqual "2 hours ago (5:00pm)"
    e(f(m(new Date(2011, 11, 21, 10, 15, 13)))).toEqual "10:15am"
    e(f(m(new Date(2011, 11, 21, 19, 18, 13)))).toEqual "10 minutes ago"
    e(f(m(new Date(2011, 11, 21, 19, 28, 30)))).toEqual "2 seconds ago"
