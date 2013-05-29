CI.inner.Invoice = class Invoice extends CI.inner.Obj
  total: =>
    "$#{@amount_due / 100}"

  zeroize: (val) =>
    if val < 10
      "0" + val
    else
      val

  as_string: (timestamp) =>
    m = moment.unix(timestamp).utc()
    "#{m.year()}/#{@zeroize(m.month()+1)}/#{@zeroize(m.date())}"

  time_period: =>
    "#{@as_string(@period_start)} - #{@as_string(@period_end)}"

  invoice_date: =>
    "#{@as_string(@date)}"

CI.inner.Billing = class Billing extends CI.inner.Obj
  observables: =>
    stripeToken: null
    cardInfo: null
    invoices: []

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
    containers: 1

  constructor: ->
    super

    @savedCardNumber = @komp =>
      return "" unless @cardInfo()
      "************" + @cardInfo().last4

    @wizardCompleted = @komp =>
      @wizardStep() > 3

    # Handles the plan templates we send
    @concurrency_p =  @komp =>
      @plans()[0] and (@plans()[0].type is "concurrency")

    @containers_p = @komp =>
      @plans()[0] and (@plans()[0].type is "containers")

    # Handles their current plan
    @chosen_plan_concurrency_p = @komp =>
      @chosenPlan() and (@chosenPlan().type is "concurrency")

    @chosen_plan_containers_p = @komp =>
      @chosenPlan() and (@chosenPlan().type is "containers")

    @total = @komp =>
      if @chosen_plan_concurrency_p()
        @calculateCost(@chosenPlan(), parseInt(@concurrency()), parseInt(@parallelism()))
      else if @chosen_plan_containers_p()
        @calculateCost(@chosenPlan(), parseInt(@containers()))

  parallelism_option_text: (p) =>
    "#{p}-way ($#{@parallelism_cost(@chosenPlan(), p)})"

  concurrency_option_text: (c) =>
    "#{c} build#{if c > 1 then 's' else ''} at a time ($#{@concurrency_cost(@chosenPlan(), c)})"

  containers_option_text: (c) =>
    "#{c} containers ($#{@containerCost(@chosenPlan(), c)})"

  parallelism_cost: (plan, p) =>
    Math.max(0, @calculateCost(plan, null, p) - @calculateCost(plan))

  # p2 > p1
  parallelism_cost_difference: (plan, p1, p2) =>
    @parallelism_cost(plan, p2) - @parallelism_cost(plan, p1)

  concurrency_cost: (plan, c) ->
    if plan.concurrency == "Unlimited"
      0
    else
      Math.max(0, @calculateCost(plan, c) - @calculateCost(plan))

  calculateCostConcurrency: (plan, concurrency, parallelism) ->
    c = concurrency or 0
    extra_c = Math.max(0, c - 1)

    p = parallelism or 1
    p = Math.max(p, 2)
    extra_p = (CI.math.log2 p) - 1
    extra_p = Math.max(0, extra_p)

    plan.price + (extra_c * 49) + (Math.round(extra_p * 99))

  containerCost: (plan, containers) ->
    c = Math.min(containers or 0, plan.max_containers())
    free_c = plan.free_containers()

    Math.max(0, (c - free_c) * plan.container_cost)

  calculateCostContainers: (plan, containers) =>
    plan.price + @containerCost(plan, containers)

  calculateCost: (plan, args...) =>
    unless plan
      0
    else
      if plan.type is "concurrency"
        @calculateCostConcurrency(plan, args...)
      else if plan.type is "containers"
        @calculateCostContainers(plan, args...)

  selectPlan: (plan, event) =>
    if plan.price?
      if @wizardCompleted()
        @oldPlan(@chosenPlan())
        @chosenPlan(plan)
        $("#confirmForm").modal({keyboard: false})
      else
        @createCard(plan, event)
    else
      VM.raiseIntercomDialog("I'd like ask about enterprise pricing...\n\n")

  cancelUpdate: (data, event) =>
    $('#confirmForm').modal('hide')
    @chosenPlan(@oldPlan())

  doUpdate: (data, event) =>
    @recordStripeTransaction event, null
    $('#confirmForm').modal('hide')
    if @wizardCompleted() # go to the speed nav
      # fight jQuery plugins with more jQuery
      $("#speed > a").click()

  ajaxSetCard: (event, token, type) =>
    $.ajax
      type: type
      url: "/api/v1/user/pay/card"
      event: event
      data: JSON.stringify
        token: token
      success: =>
        @loadExistingCard()

  createCard: (plan, event) =>
    StripeCheckout.open
      key: @stripeKey()
      name: 'CircleCi',
      panelLabel: 'Add card',
      description: "#{plan.name} plan"
      price: 100 * plan.price
      token: (token) =>
        @chosenPlan(plan)
        @recordStripeTransaction event, token


  updateCard: (data, event) =>
    StripeCheckout.open
      key: @stripeKey()
      name: 'CircleCi',
      panelLabel: 'Update card',
      token: (token) =>
        @ajaxSetCard(event, token.id, "PUT")


  load: (hash="small") =>
    unless @loaded
      @loadPlans()
      @loadPlanFeatures()
      @loadExistingCard()
      @loadInvoices()
      @loadExistingPlans()
      @loadOrganizations()
      @loadStripe()
      @loaded = true


  stripeKey: () =>
    switch renderContext.env
      when "production" then "pk_ZPBtv9wYtkUh6YwhwKRqL0ygAb0Q9"
      else 'pk_Np1Nz5bG0uEp7iYeiDIElOXBBTmtD'



  recordStripeTransaction: (event, stripeInfo) =>
    $('button').attr('disabled','disabled') # disable other buttons during payment
    $.ajax(
      url: "/api/v1/user/pay"
      event: event
      type: if stripeInfo then "POST" else "PUT"
      data: JSON.stringify
        token: stripeInfo
        plan: @chosenPlan().id

      success: () =>
        $('button').removeAttr('disabled') # payment done, reenable buttons
        @cardInfo(stripeInfo.card) if stripeInfo?
        @oldTotal(@total())

        # just to be sure
        @loadExistingCard()
        @loadInvoices()

        @advanceWizard()
    )
    false

  advanceWizard: =>
    @wizardStep(@wizardStep() + 1)

  closeWizard: =>
    @wizardStep(4)


  loadStripe: () =>
    $.getScript "https://js.stripe.com/v1/"
    $.getScript "https://checkout.stripe.com/v2/checkout.js"

  loadExistingPlans: () =>
    $.getJSON '/api/v1/user/existing-plans', (data) =>
      @oldTotal(data.amount / 100)
      @chosenPlan(new CI.inner.Plan(data.plan)) if data.plan
      @concurrency(data.concurrency or 1)
      @parallelism(data.parallelism or 1)
      @containers(data.containers or 1)
      if @chosenPlan()
        @closeWizard()

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
        @advanceWizard()

  saveSpeed: (data, event) =>
    if @chosen_plan_containers_p()
      @saveContainers(data, event)
    else
      @saveParallelism(data, event)

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
        @closeWizard()

  saveContainers: (data, event) =>
    $.ajax
      type: "PUT"
      event: event
      url: "/api/v1/user/containers"
      data: JSON.stringify
        containers: @containers()
      success: (data) =>
        @oldTotal(@total())
        @closeWizard()

  loadExistingCard: () =>
    $.getJSON '/api/v1/user/pay/card', (card) =>
      @cardInfo card

  loadInvoices: () =>
    $.getJSON '/api/v1/user/pay/invoices', (invoices) =>
      if invoices
        @invoices(new Invoice(i) for i in invoices)


  loadPlans: () =>
    $.getJSON '/api/v1/plans', (data) =>
      @plans((new CI.inner.Plan(d) for d in data))

  loadPlanFeatures: () =>
    @planFeatures(CI.content.pricing_features)

  popover_options: (extra) =>
    options =
      html: true
      trigger: 'hover'
      delay: 0
      animation: false
      placement: 'bottom'
     # this will break when we change bootstraps! take the new template from bootstrap.js
      template: '<div class="popover billing-popover"><div class="popover-inner"><h3 class="popover-title"></h3><div class="popover-content"></div></div></div>'

    for k, v of extra
      options[k] = v

    options