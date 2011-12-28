$(document).ready ->
  resize = (event) ->
    $(this).toggleClass("minimize")
    $(this).siblings('.detail').toggleClass("minimize")

  $('.action_header').on("click", resize)
