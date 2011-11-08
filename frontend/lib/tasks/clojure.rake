namespace :clojure do

  task :import do
    Kernel.system "cd ../backend && lein uberjar"
    require 'fileutils'
    FileUtils.mv("../backend/circle-0.1.0-SNAPSHOT-standalone.jar", "classes")
  end

end
