module JoinHelper

  def active_header_style()
    "success"
  end

  def inactive_header_style()
    "info"
  end

  def class_for_header(step, index)
    if step == index then
      active_header_style
    else
      inactive_header_style
    end
  end

  # This uses step, not substep, since the field will either be visible or
  # hidden depending on the step, not the substep.
  def class_for_subsection(step, index)
    if step != index then
      "hidden"
    end
  end
end
