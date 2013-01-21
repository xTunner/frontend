CI.inner.Billing = class Billing extends CI.inner.Obj
  observables: =>
    stripeToken: null
    cardInfo: null

    # old data
    oldPlan: null
    oldTotal: 0

    # metadata
    wizardStep: 1
    planFeatures: []
    loadingOrganizations: false

    # new data
    organizations: {}
    chosenPlan: null
    plans: []
    parallelism: 1
    concurrency: 1

  constructor: ->
    super

    @total = @komp =>
      @calculateCost(@chosenPlan(), parseInt(@concurrency()), parseInt(@parallelism()))

    @savedCardNumber = @komp =>
      return "" unless @cardInfo()
      "************" + @cardInfo().last4

    @wizardCompleted = @komp =>
      @wizardStep() > 4

    # Make sure @parallelism remains a number
    @editParallelism = @komp
      read: ->
        @parallelism()
      write: (val) ->
        if val? then @parallelism(parseInt(val))
      owner: @

    @editConcurrency = @komp
      read: ->
        @concurrency()
      write: (val) ->
        if val? then @concurrency(parseInt(val))
      owner: @

  parallelism_option_text: (plan, p) =>
    "#{p}-way ($#{@parallelism_cost(plan, p)})"

  concurrency_option_text: (plan, c) =>
    "#{c} build#{if c > 1 then 's' else ''} at a time ($#{@concurrency_cost(plan, c)})"

  raw_parallelism_cost: (p) ->
    if p == 1
      0
    else
      Math.round(CI.math.log2(p) * 99)

  parallelism_cost: (plan, p) =>
    Math.max(0, @calculateCost(plan, null, p) - @calculateCost(plan))
    #Math.max(0, @raw_parallelism_cost(p) - @raw_parallelism_cost(plan.min_parallelism))

  # p2 > p1
  parallelism_cost_difference: (plan, p1, p2) =>
    @parallelism_cost(plan, p2) - @parallelism_cost(plan, p1)

  concurrency_cost: (plan, c) ->
    if plan.concurrency == "Unlimited"
      0
    else
      Math.max(0, @calculateCost(plan, c) - @calculateCost(plan))

  calculateCost: (plan, concurrency, parallelism) ->
    unless plan
      0
    else
      c = concurrency or 0
      extra_c = Math.max(0, c - 1)

      p = parallelism or 1
      p = Math.max(p, 2)
      extra_p = (CI.math.log2 p) - 1
      extra_p = Math.max(0, extra_p)

      plan.price + (extra_c * 49) + (Math.round(extra_p * 99))

  selectPlan: (plan) =>
    if plan.price?
      @oldPlan(@chosenPlan())
      @chosenPlan(plan)
      if @wizardCompleted()
        $("#confirmForm").modal({keyboard: false})
      else
        @advanceWizard(2)
    else
      VM.raiseIntercomDialog("I'd like ask about enterprise pricing...\n\n")


  cancelUpdate: (data, event) =>
    $('#confirmForm').modal('hide')
    @chosenPlan(@oldPlan())

  doUpdate: (data, event) =>
    @stripeUpdate(data, event)
    $('#confirmForm').modal('hide')

  load: (hash="small") =>
    unless @loaded
      @loadPlans()
      @loadPlanFeatures()
      @loadExistingPlans()
      @loadOrganizations()
      @loadStripe()
      @loaded = true

  stripeSubmit: (data, event) ->
    number = $('.card-number').val()
    cvc = $('.card-cvc').val()
    exp_month = $('.card-expiry-month').val()
    exp_year = $('.card-expiry-year').val()

    unless Stripe.validateCardNumber number
      notifyError "Invalid credit card number, please try again."
      event.preventDefault()
      return false

    unless Stripe.validateExpiry exp_month, exp_year
      notifyError "Invalid expiry date, please try again."
      event.preventDefault()
      return false

    unless Stripe.validateCVC cvc
      notifyError "Invalid CVC, please try again."
      event.preventDefault()
      return false

    key = switch renderContext.env
      when "production" then "pk_ZPBtv9wYtkUh6YwhwKRqL0ygAb0Q9"
      else 'pk_Np1Nz5bG0uEp7iYeiDIElOXBBTmtD'
    Stripe.setPublishableKey(key)

    # disable the submit button to prevent repeated clicks
    button = $('.submit-button')
    button.addClass "disabled"

    Stripe.createToken {
      number: number,
      cvc: cvc,
      exp_month: exp_month,
      exp_year: exp_year
    }, (status, response) =>
      if response.error
        button.removeClass "disabled"
        notifyError response.error.message
      else
        @recordStripeTransaction event, response # TODO: add the plan

    # prevent the form from submitting with the default action
    return false;

  stripeUpdate: (data, event) ->
    @recordStripeTransaction event, null

  recordStripeTransaction: (event, stripeInfo) =>
    $.ajax(
      url: "/api/v1/user/pay"
      event: event
      type: if stripeInfo then "POST" else "PUT"
      data: JSON.stringify
        token: stripeInfo
        plan: @chosenPlan().id

      success: () =>
        @cardInfo(stripeInfo.card) if stripeInfo?
        @oldTotal(@total())
        @advanceWizard(3)
    )
    false

  advanceWizard: (new_step) =>
    @wizardStep(Math.max(new_step, @wizardStep() + 1))


  loadStripe: () =>
    $.getScript "https://js.stripe.com/v1/"

  loadExistingPlans: () =>
    $.getJSON '/api/v1/user/existing-plans', (data) =>
      @cardInfo(data.card_info)
      @oldTotal(data.amount / 100)
      @chosenPlan(new CI.inner.Plan(data.plan)) if data.plan
      @concurrency(data.concurrency or 1)
      @parallelism(data.parallelism or 1)
      if @chosenPlan()
        @advanceWizard(5)

  loadOrganizations: () =>
    @loadingOrganizations(true)
    $.getJSON '/api/v1/user/stripe-organizations', (data) =>
      @loadingOrganizations(false)
      @organizations(data)

  saveOrganizations: (data, event) =>
    $.ajax
      type: "PUT"
      event: event
      url: "/api/v1/user/organizations"
      data: JSON.stringify
        organizations: @organizations()
      success: =>
        @advanceWizard(4)

  saveParallelism: (data, event) =>
    $.ajax
      type: "PUT"
      event: event
      url: "/api/v1/user/parallelism"
      data: JSON.stringify
        parallelism: @parallelism()
        concurrency: @concurrency()
      success: (data) =>
        @oldTotal(@total())
        @advanceWizard(5)


  loadPlans: () =>
    $.getJSON '/api/v1/user/plans', (data) =>
      @plans((new CI.inner.Plan(d) for d in data))

  loadPlanFeatures: () =>
    @planFeatures(renderContext.pricingFeatures)
    $('html').popover
      html: true
      delay: 0
      # this will break when we change bootstraps! take the new template from bootstrap.js
      template: '<div class="popover billing-popover"><div class="popover-inner"><h3 class="popover-title"></h3><div class="popover-content"></div></div></div>'
      placement: "bottom"
      trigger: "hover"
      selector: ".more-info"
