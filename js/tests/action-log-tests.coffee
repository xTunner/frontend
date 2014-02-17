j = jasmine.getEnv()

j.describe "maybe_retrieve_output", ->
  j.it "should run if the action log is expanded", ->
    $.mockjax
      url: "/api/v1/project/test-org/test-project/1/output/*"
      responseText: [{type: "out", message: "This is some stdout\nhi"}]

    build = new CI.inner.Build({vcs_url: "https://github.com/test-org/test-project", build_num: 1})
    log = new CI.inner.ActionLog({has_output: true, step: 0, index: 1, minimize: true}, build)

    runs =>
      log.toggle_minimize()

    waitsFor =>
      log.retrieving_output() == false

    runs =>
      expect(log.final_out()).toEqual(["<span class='brblue'>This is some stdout\n</span>"])

    $.mockjaxClear()
