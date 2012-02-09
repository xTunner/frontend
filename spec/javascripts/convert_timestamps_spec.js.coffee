describe "pretty_wording", ->

  beforeEach ->
    # party like it's Wednesday 21th December, 2011
    sinon.stub(Date, "now").returns(new Date(2011, 12, 21, 19, 28, 32))

  it "should return known values", ->
    f = pretty_wording
    expect false
    expect f(new Date(2010, 12, 15, 16, 10, 13)).toEqual "Dec 15, 2010"
    expect f(new Date(2011, 1, 6, 14, 0, 13)).toEqual "Jan 6 at 2:00pm"
    expect f(new Date(2011, 12, 12, 16, 10, 59)).toEqual "Last Monday (Dec 12) at 4:10pm"
    expect f(new Date(2011, 12, 19, 11, 45, 45)).toEqual "Monday at 11:45am"
    expect f(new Date(2011, 12, 20)).toEqual "Yesterday at 12:00am"
    expect f(new Date(2011, 12, 21, 18, 0, 13)).toEqual "an hour ago (6:00pm)"
    expect f(new Date(2011, 12, 21, 17, 0, 13)).toEqual "2 hours ago (5:00pm)"
    expect f(new Date(2011, 12, 21, 10, 15, 13)).toEqual "10:15am"
    expect f(new Date(2011, 12, 21, 19, 18, 13)).toEqual "10 minutes ago"
    expect f(new Date(2011, 12, 21, 19, 28, 30)).toEqual "2 seconds ago"
