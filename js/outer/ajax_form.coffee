CI.outer.AjaxForm = class AjaxForm
  $element = null

  constructor: ($element) ->
    @$element = $element

  submitAjaxForm: () =>
    form = @$element
    $form = $(form)
    $.ajax {
      data: $form.serialize()
      contentType: "application/x-www-form-urlencoded; charset=UTF-8"
      success: (data) =>
        if data && data.notice
          # set the width so the sticky window doesn"t change width if we show a notice
          $form.css("width", $form.width())

          # show the message from the server
          $notice = $form.find(".notice")
          $notice.text(data.notice)
          $notice.show()

        # clear inputs
        $form.find("input").val("")
        $form.find("textarea").val("")
      type: "post"
      url: form.action
    }
