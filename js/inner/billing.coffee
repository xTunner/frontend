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
    containers: 1
    payor: null
    special_price_p: null

  constructor: ->
    super

    @savedCardNumber = @komp =>
      return "" unless @cardInfo()
      "************" + @cardInfo().last4

    @wizardCompleted = @komp =>
      @wizardStep() > 3

    @total = @komp =>
      @calculateCost(@chosenPlan(), parseInt(@containers()))

    @extra_containers = @komp =>
      if @chosenPlan()
        Math.max(0, @containers() - @chosenPlan().free_containers())

  containers_option_text: (c) =>
    container_price = @chosenPlan().container_cost
    cost = @containerCost(@chosenPlan(), c)
    "#{c} containers ($#{cost})"

  containerCost: (plan, containers) ->
    c = Math.min(containers or 0, plan.max_containers())
    free_c = plan.free_containers()

    Math.max(0, (c - free_c) * plan.container_cost)

  calculateCost: (plan, containers) =>
    unless plan
      0
    else
      plan.price + @containerCost(plan, containers)

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

  stripeDefaults: () =>
    key: @stripeKey()
    name: "CircleCI"
    address: false
    email: VM.current_user().selected_email()

  createCard: (plan, event) =>
    vals =
      panelLabel: 'Add card',
      price: 100 * plan.price
      description: "#{plan.name} plan"
      token: (token) =>
        @chosenPlan(plan)
        @recordStripeTransaction event, token

    StripeCheckout.open($.extend @stripeDefaults(), vals)


  updateCard: (data, event) =>
    vals =
      panelLabel: 'Update card',
      token: (token) =>
        @ajaxSetCard(event, token.id, "PUT")

    StripeCheckout.open($.extend @stripeDefaults(), vals)


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
        mixpanel.track("Paid")
    )
    false

  advanceWizard: =>
    @wizardStep(@wizardStep() + 1)

  closeWizard: =>
    @wizardStep(4)

  loadStripe: () =>
    $.getScript "https://js.stripe.com/v1/"
    # Stripe has a bug in v3 that makes ajax sends use url/form-encoded
    # instead of application/json, this breaks our ajax calls, so users
    # can't change their containers. If we want a true result, we'll
    # have to restart the test with a different name.
    if false #VM.ab().stripe_v3()
      $.getScript "https://checkout.stripe.com/v3/checkout.js"
    else
      $.getScript "https://checkout.stripe.com/v2/checkout.js"

  loadPlanData: (data) =>
    @oldTotal(data.amount / 100)
    @chosenPlan(new CI.inner.Plan(data.plan)) if data.plan
    @containers(data.containers or 1)
    @payor(data.payor) if data.payor
    @special_price_p(@oldTotal() <  @total())

  loadExistingPlans: () =>
    $.getJSON '/api/v1/user/existing-plans', (data) =>
      @loadPlanData data
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
        mixpanel.track("Save Organizations")

  # TODO: make the API call return existing plan
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
        mixpanel.track("Save Containers")
        @loadExistingPlans()

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
