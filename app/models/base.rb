module Base
  def self.included(base)
    base.extend ClassMethods
  end

  module ClassMethods
    def unsafe_create(attrs={})
      raise if !Rails.env.test?

      p = self.create!
      attrs.each { |k, v| p.send("#{k}=", v) }
      p.save!
      p
    end
  end
end
