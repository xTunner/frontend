j = jasmine.getEnv()

j.describe "status_style", =>
  j.beforeEach =>
    @build = new CI.inner.Build({vcs_url: "https://github.com/test-org/test-project", build_num: 1})

    make_action = () =>
      new CI.inner.ActionLog({has_output: false, status: "running"}, @build)

    @actions = (make_action() for dont_care in _.range(4))
    @container = new CI.inner.Container("Test", 0, @actions, @build)


  j.it "should be running if all actions have been successful but the build has not finished", =>
    for action in @actions
      action.status("success")

    @build.stop_time(null)
    @expect(@container.status_style()).toEqual({running: true})


  j.it "should be successful if all actions have been successful and the build is finished", =>
    for action in @actions
      action.status("success")

    @build.stop_time("2014-01-01T12:00")
    @expect(@container.status_style()).toEqual({success: true})


  j.it "should be failed if an action has failed, whether the build is running or not", =>
    for action in @actions
      action.status("running")
    @actions[0].status("failed")

    @expect(@container.status_style()).toEqual({failed: true})

    @build.stop_time("2014-01-01T12:00")
    @expect(@container.status_style()).toEqual({failed: true})


  j.it "should be canceled if any action has been canceled, whether the build is running or not", =>
    for action in @actions
      action.status("running")
    @actions[0].status("canceled")

    @expect(@container.status_style()).toEqual({canceled: true})

    @build.stop_time("2014-01-01T12:00")
    @expect(@container.status_style()).toEqual({canceled: true})


  j.it "should assume running if there are no actions in the container", =>
    container = new CI.inner.Container("Test", 0, [], @build)
    @expect(container.status_style()).toEqual({running: true})
