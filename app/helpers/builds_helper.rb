module BuildsHelper
  def action_header_style(log)
    css = log.status.to_s
    if css == "timedout"
      css = "failed"
    end

    if log.success?
      css += " minimize"
    end
    if log["out"]
      css += " contents"
    end
    css
  end

  def action_log_style(log)
    log.success? ? "minimize" : ""
  end


  def action_log_output(logs)
    return "" if logs.nil? || logs.length == 0

    buf = ""

    type = nil
    logs.each do |l|

      # maybe close the last tag and open a new one
      if type != l['type']
        buf << "</span>"
        type = l['type']
        buf << "<span class='#{type}'>"
      end


      buf << "#{l['message']}"
    end

    # There should always be a tag open now
    buf << "</span>"
    buf
  end
end
