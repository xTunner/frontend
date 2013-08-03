CI.inner.Plan = class Plan extends CI.inner.Obj
  observables: =>
    free_containers: 1
    max_containers: 1
    containers: null

  constructor: ->
    super

    @concurrency_options = ko.observableArray([1..20])

    @container_options = ko.observableArray([@free_containers()..@max_containers()])

    @allowsParallelism = @komp =>
      @max_parallelism > 1

    @projectsTitle = @komp =>
      "#{@projects} project" + (if @projects == 1 then "" else "s")

    @minParallelismDescription = @komp =>
      "#{@min_parallelism}x"

    @maxParallelismDescription = @komp =>
      "up to #{@max_parallelism}x"

    @freeContainersDescription = @komp =>
      "#{@free_containers()} container" + (if @free_containers() == 1 then "" else "s")

    @containerCostDescription = @komp =>
      if @container_cost
        "$#{@container_cost} / container"
      else
        "Contact us"

    @pricingDescription = @komp =>
      if VM.billing().chosenPlan()? and @.id == VM.billing().chosenPlan().id
        "Your current plan"
      else
        if not @price?
          "Contact us for pricing"
        else
          if VM.billing().chosenPlan()?
            "Switch plan $#{@price}/mo"
          else
            "Sign up now for $#{@price}/mo"

    @enterprise_p = @komp =>
      @name is "Enterprise"

  featureAvailableOuter: (feature) =>
    result = not feature.name? or feature.name in @features

  featureAvailable: (feature) =>
    result =
      tick: not feature.name? or feature.name in @features
    if feature.name?
      result[feature.name] = true
    result
