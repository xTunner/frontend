CI.outer.MarketoForms = class MarketoForms

  constructor: (@form, @formid) ->
    @FirstName = ko.observable()
    @LastName = ko.observable()
    @Email = ko.observable()
    @Company = ko.observable()
    @repo_solution__c = ko.observable()
    @munchkinId = "894-NPA-635"
    @_mkto_trk = $.cookie('_mkto_trk')
    @notice = ko.observable()
    @message = ko.observable()
    @other_field = ko.observable()
    @display_other = ko.computed =>
      @repo_solution__c() == 'other'
    @other_input = ko.observable()

  submitShopifyStoryForm: (data, event) =>
    if not (@Email())
      @notice
        type: 'error'
        message: 'Email is required.'

    else
      $.ajax
        url: "http://app-abm.marketo.com/index.php/leadCapture/save2"
        type: "POST"
        event: event
        data:
          FirstName: @FirstName()
          LastName: @LastName()
          Email: @Email()
          repo_solution__c: if @repo_solution__c() == 'other' then @other_input() else @repo_solution__c()  
          Company: @Company()
          munchkinId: @munchkinId
          formid: @formid
          _mkt_trk: @_mkt_trk
          formVid: @formid
        contentType: "application/x-www-form-urlencoded; charset=UTF-8"

        success: () =>
          @FirstName(null)
          @LastName(null)
          @Email(null)
          @Company(null)
          @message(null)
          @repo_solution__c(null)
          @notice
            type: 'success'
            message: 'Thanks! We will be in touch soon.'
        error: (error) =>
          @notice
            type: 'error'
            message: 'Network error! Please reach out at sayhi@circleci.com. Thanks!'
          
          
