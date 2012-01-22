class Hash
  def select_keys(keys)
    ## returns a new hash containing only the keys listed. Keys must support include?
    self.select { |k,v| keys.include?(k) }
  end
end
