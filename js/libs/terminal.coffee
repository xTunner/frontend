CI.terminal =
  ansiToHtml: (str) ->
    # http://en.wikipedia.org/wiki/ANSI_escape_code
    start   = 0
    current = str
    output  = ""

    style =
      color: null
      italic: false
      bold: false

      reset: () ->
        @color = null
        @italic = false
        @bold = false

      add: (n) ->
        switch parseInt(n)
          when 0 then @reset()
          when 1 then @bold = true
          when 3 then @italic = true
          when 22 then @bold = false
          when 23 then @italic = false
          when 30 then @color = "black"
          when 31 then @color = "red"
          when 32 then @color = "green"
          when 33 then @color = "yellow"
          when 34 then @color = "blue"
          when 35 then @color = "magenta"
          when 36 then @color = "cyan"
          when 37 then @color = "white"
          when 39 then @color = null

      openSpan: () ->
        styles = []
        if @color?
          styles.push("color: #{@color}")
        if @italic
          styles.push("font-style: italic")
        if @bold
          styles.push("font-weight: bold")

        s = "<span"
        if styles.length > 0
          s += " style='" + styles.join("; ") + "'"
        s += ">"

      applyTo: (content) ->
        if content
          @openSpan() + content + "</span>"
        else
          ""

    # loop over escape sequences
    while (escape_start = current.indexOf('\u001B[')) != -1
      # append everything up to the start of the escape sequence to the output
      output += style.applyTo(current.slice(0, escape_start))

      # find the end of the escape sequence -- a single letter
      rest = current.slice(escape_start + 2)
      escape_end = rest.search(/[A-Za-z]/)

      # point "current" at first character after the end of the escape sequence
      current = rest.slice(escape_end + 1)

      # only actually deal with 'm' escapes
      if rest.charAt(escape_end) == 'm'
        escape_sequence = rest.slice(0, escape_end)
        if escape_sequence == ''
          # \esc[m is equivalent to \esc[0m
          style.reset()
        else
          escape_codes = escape_sequence.split(';')
          style.add esc for esc in escape_codes

    output += style.applyTo(current)
    output
