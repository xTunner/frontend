module BuildsHelper
  def action_header_style(log)
    css = log.status.to_s
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
end
