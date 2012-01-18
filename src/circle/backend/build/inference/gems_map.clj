(ns circle.backend.build.inference.gems-map)

(def gem-package-map
  ;; this is a map of rubygems to the ubuntu packages necessary for this gem to install. Incomplete.
  {"memcached" #{"libmemcached-dev" "libsasl2-dev"}
   "cdamian-geoip_city" #{"libgeoip-dev"}
   "capybara-webkit" #{"libqtwebkit-dev"}})