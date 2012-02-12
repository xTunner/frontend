(ns circle.backend.build.inference.gems-map)

(def gem-package-map
  ;; this is a map of rubygems to the ubuntu packages necessary for this gem to install. Incomplete.
  {"memcached" #{"libmemcached-dev" "libsasl2-dev"}
   "cdamian-geoip_city" #{"libgeoip-dev"}
   "capybara-webkit" #{"libqtwebkit-dev"}})

(def blacklisted-gems
  ;; gems that should be removed from the user's gemfile when running
  #{"rb-fsevent" "growl_notify" "autotest-fsevent"})

(def database-yml-gems
  ;; if using any of these gems, they'll need a database.yml. We will
  ;; generate a database.yml for them.

  ;; This list is ordered, if multiple gems appear, we'll generate
  ;; database.yml using the first listed.
  ["pg" "mysql2" "mysql" "sqlite3"])

(def gem-adapter-map
  {"pg" "postgresql"
   "sqlite3" "sqlite3"
   "mysql" "mysql"
   "mysql2" "mysql2"})