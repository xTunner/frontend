module Base
  def self.included(base)
    base.extend ClassMethods
  end

  module ClassMethods
    def unsafe_create(attrs={})
      raise unless Rails.env.test?

      p = self.create! attrs
      attrs.each { |k, v| p.send("#{k}=", v) }
      p.save!
      p
    end
  end
end
