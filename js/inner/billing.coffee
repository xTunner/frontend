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

  constructor: ->
    super

    @total = @komp =>
      @calculateCost(@chosenPlan(), parseInt(@concurrency()), parseInt(@parallelism()))

    @savedCardNumber = @komp =>
      return "" unless @cardInfo()
      "************" + @cardInfo().last4

    @wizardCompleted = @komp =>
      @wizardStep() > 3

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

  loadExistingCard: () =>
    $.getJSON '/api/v1/user/pay/card', (card) =>
      @cardInfo card

  loadInvoices: () =>
    $.getJSON '/api/v1/user/pay/invoices', (invoices) =>
      if invoices
        @invoices(new Invoice(i) for i in invoices)


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
