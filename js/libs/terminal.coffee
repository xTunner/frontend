CI.terminal =
  ansiToHtmlConverter: (defaultColor) ->
    trailing_raw = ""
    trailing_out = ""
    style =
      color: defaultColor
      italic: false
      bold: false

      reset: () ->
        @color = defaultColor
        @italic = false
        @bold = false

      add: (n) ->
        switch parseInt(n)
          when 0 then @reset()
          when 1 then @bold = true
          when 3 then @italic = true
          when 22 then @bold = false
          when 23 then @italic = false
          when 30 then @color = "white" ## actually black, but we use a black background
          when 31 then @color = "red"
          when 32 then @color = "green"
          when 33 then @color = "yellow"
          when 34 then @color = "blue"
          when 35 then @color = "magenta"
          when 36 then @color = "cyan"
          when 37 then @color = "white"
          when 39 then @color = defaultColor

      classes: () ->
        classes = []
        if @bold and not @color.match(/^br/)
          classes.push("br#{@color}")
        else if @color != defaultColor
          classes.push("#{@color}")
        if @italic
          classes.push("italic")

        classes


      applyTo: (content) ->
        if content
          classes = @classes()
          if classes.length
            "<span class='#{classes.join(' ')}'>#{content}</span>"
          else
            content
        else
          ""

    get_trailing: () ->
      trailing_out

    append: (str) ->
      # http://en.wikipedia.org/wiki/ANSI_escape_code
      start   = 0
      current = trailing_raw + str
      output  = ""

      trailing_raw = ""
      trailing_out = ""

      # loop over lines
      while current.length and ((line_end = current.search(/\r|\n|$/)) != -1)
        next_line_start = current.slice(line_end).search(/[^\r\n]/)
        if next_line_start == -1
          terminator = current.slice(line_end)
        else
          terminator = current.slice(line_end, line_end + next_line_start)
        input_line = current.slice(0, line_end + terminator.length)
        original_input_line = input_line
        output_line = ""

        # loop over escape sequences within the line
        while (escape_start = input_line.indexOf('\u001B[')) != -1
          # append everything up to the start of the escape sequence to the output
          output_line += style.applyTo(input_line.slice(0, escape_start))

          # find the end of the escape sequence -- a single letter
          rest = input_line.slice(escape_start + 2)
          escape_end = rest.search(/[A-Za-z]/)

          # point "input_line" at first character after the end of the escape sequence
          input_line = rest.slice(escape_end + 1)

          # only actually deal with 'm' escapes
          if rest.charAt(escape_end) == 'm'
            escape_sequence = rest.slice(0, escape_end)
            if escape_sequence == ''
              # \esc[m is equivalent to \esc[0m
              style.reset()
            else
              escape_codes = escape_sequence.split(';')
              style.add esc for esc in escape_codes

        current = current.slice(line_end + terminator.length)
        output_line += style.applyTo(input_line)

        if not current.length
          ## the last line is "trailing"
          trailing_raw = original_input_line
          trailing_out = output_line
        else
          # don't write the output line if it ends with a carriage return, for primitive
          # terminal animations...
          if terminator.search(/^\r+$/) == -1
            output += output_line

      output

  ansiToHtml: (str) ->
    # convenience function for testing
    converter = @ansiToHtmlConverter("brblue")
    converter.append(str) + converter.get_trailing()
