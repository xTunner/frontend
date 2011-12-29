module BuildsHelper
  def action_header_style(log)
    css = log.success? ? "success minimize" : "error"
    if log["out"]
      css += " contents"
    end
    css
  end

  def action_log_style(log)
    log.success? ? "minimize" : ""
  end
end
