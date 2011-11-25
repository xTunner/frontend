# There are lots of way's of calling backend code, this allows us to mock it

# clj = JRClj.new
# clj.inc 0

# circle = JRClj.new "circle.init"

# db = JRClj.new "circle.db"
# db.run "circle.db/init"

# circle.run "circle.init/-main"
# circle.init

# JRClj.new("circle.util.time").ju_now

class Backend
  class_attribute :mock

  def self.github_hook(url, after, ref, json)
    return if Backend.mock
    clj = JRClj.new "circle.init"
    clj.maybe_change_dir
    clj._import "circle.hooks"
    clj.github url, after, ref, json
  end

end

Backend.mock = true
if RUBY_PLATFORM == 'java' || Rails.env != 'test' then
  Backend.mock = false
end
