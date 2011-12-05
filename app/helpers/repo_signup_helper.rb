module RepoSignupHelper

  def class_for_header(step, index)
    if step == index then
      "highlighted"
    else
      "subdued"
    end
  end

  # This uses step, not substep, since the field will either be visible or
  # hidden depending on the step, not the substep.
  def class_for_subsection(step, index)
    if step == index then
      "visible"
    else
      "hidden"
    end
  end
end
