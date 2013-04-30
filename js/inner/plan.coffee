CI.inner.Plan = class Plan extends CI.inner.Obj
  constructor: ->
    super

    # Temporary limit so that users don't "block all builds forever"
    @max_purchasable_parallelism  = @komp =>
      Math.min(4, @max_parallelism)

    # Change max_purchasable_parallelism to @max_parallelism when we can
    # do unlimited parallelism
    @parallelism_options = ko.observableArray([1..@max_purchasable_parallelism()])

    @concurrency_options = ko.observableArray([1..20])

    @allowsParallelism = @komp =>
      @max_parallelism > 1

    @projectsTitle = @komp =>
      "#{@projects} project" + (if @projects == 1 then "" else "s")

    @minParallelismDescription = @komp =>
      "#{@min_parallelism}x"

    @maxParallelismDescription = @komp =>
      "up to #{@max_parallelism}x"

    @freeContainersDescription = @komp =>
      "#{@free_containers}"

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


  featureAvailable: (feature) =>
    result =
      tick: not feature.name? or feature.name in @features
    if feature.name?
      result[feature.name] = true
    result
