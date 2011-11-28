module HomeHelper
  def centering(id, &block)
    xm = Builder::XmlMarkup.new
    xm.div({:id => id, :class => "vcenter1"}) do
      xm << content_tag(:div, {:class => "vcenter2"}) do
        content_tag(:div, {:class => "vcenter3"}) do
          capture(&block)
        end
      end
    end
  end
end
