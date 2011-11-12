# Be sure to restart your server when you modify this file.

# Convenience functions for clojure code.
def circle
  Java::circle
end

# Monkey-patched convenience function
class JRClj
  def run(code)
    self.eval(read_string(code))
  end
end

