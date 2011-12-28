module BuildsHelper
  def action_header_style(log)
    log.success? ? "action_button success minimize" : "action_button error"
  end

  def action_log_style(log)
    log.success? ? "minimize" : ""
  end
end
