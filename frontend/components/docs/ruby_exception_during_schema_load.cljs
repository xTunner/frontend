(ns frontend.components.docs.ruby-exception-during-schema-load)

(def article
  {:title "rake db:schema:load fails"
   :last-updated "Feb 3, 2013"
   :url :ruby-exception-during-schema-load
   :content [:div
             [:p
              "If your build fails during the"
              [:code "rake db:schema:create db:schema:load"]
              "step, there is usually a straightforward fix."]
             [:h2 "Identifying the problem"]
             [:p "Usually, this error looks like"]
             [:pre
              "’‘"
              [:code.no-highlight
               "’** Invoke db:create (first_time)** Invoke db:load_config (first_time)** Execute db:load_config** Invoke rails_env (first_time)** Execute rails_env** Execute db:create** Invoke db:schema:load (first_time)** Invoke environment (first_time)** Execute environmentrake aborted!PG::Error: ERROR:  relation \\users\\ does not existLINE 4:              WHERE a.foo = '\\users\\'::regclass                              ^SELECT a.attname, format_type(a.atttypid, a.atttypmod), d.adsrc, a.attnotnull      FROM pg_attribute a LEFT JOIN pg_attrdef d        ON a.attrelid = d.adrelid AND a.attnum = d.adnum     WHERE a.attrelid = '\\users\\'::regclass       AND a.attnum > 0 AND NOT a.attisdropped     ORDER BY a.attnum/home/ubuntu/FooBar/vendor/bundle/ruby/1.9.1/gems/activerecord-3.2.6/lib/active_record/connection_adapters/postgresql_adapter.rb:1151:in `async_exec'‘"]
              "’‘"]
             [:h2 "Understanding ActiveRecord Introspection"]
             [:p
              "ActiveRecord works by introspecting the database schema.ActiveRecord models rely on the schema being already loaded in the database to work."]
             [:p
              "This error occurs because ActiveRecord models are being used before theschema is loaded. On CircleCI, every build runs in a clean VM, so at thestart of the build, your database doesn't exist yet."]
             [:h2 "The Solution"]
             [:p
              "The solution is to make sure thatThere are many ways to trigger this bug, but the two most common are"]
             [:ul
              [:li
               "calling class methods on ActiveRecord code during rails configuration,"]
              [:li
               "requiring test code (such as spec/factories.rb) at file scope."]]
             "You should only require test code running tests (not whileloading config/environment.rb), and code that instantiatesmodels or calls class methods on models should be moved intoinitializers. As a happy side-effect, your rails boot timeshould decrease!"
             [:h2 "Identifying the Source"]
             [:p
              "If you have this problem, how do you figure out what line is responsible? By reading the stacktrace!"]
             [:pre.stacktrace "’‘" [:code "’‘"]]
             [:div.muted
              [:code
               "’\\.../activerecord-3.2.6/lib/active_record/connection_adapters/postgresql_adapter.rb:1151:in `async_exec'"
               [:br]
               "\\.../activerecord-3.2.6/lib/active_record/base.rb:482:in `initialize'"
               [:br]
               "vendor/bundle/ruby/1.9.1/gems/factory_girl-4.1.0/lib/factory_girl/decorator/new_constructor.rb:9:in `new'"
               [:br]
               "vendor/bundle/ruby/1.9.1/gems/factory_girl-4.1.0/lib/factory_girl/factory_runner.rb:23:in `block in run'"
               [:br]
               "‘"]
              [:div.important
               [:code "’spec/factories.rb:3:in `&lttop (required)>'‘"]]
              [:code
               "’"
               [:br]
               "vendor/bundle/ruby/1.9.1/gems/railties-3.2.6/lib/rails/railtie/configurable.rb:30:in `method_missing'"
               [:br]
               "‘"]
              [:div.important
               [:code "’config/environment.rb:5:in `&lttop (required)>'‘"]]
              [:code
               "’"
               [:br]
               "vendor/bundle/ruby/1.9.1/gems/activesupport-3.2.6/lib/active_support/dependencies.rb:251:in `require'"
               [:br]
               "vendor/bundle/ruby/1.9.1/gems/activesupport-3.2.6/lib/active_support/dependencies.rb:251:in `block in require'‘"]]
             [:code "’‘"]
             "’‘"
             [:p
              "Here, we can see that"
              [:code "config/environment.rb"]
              "loads, which thenrequireswhich loadsThe solution then, is to refactor the code in sucha way that spec/factories.rb isn't loaded unless tests areactually run."]
             [:p
              "The idiomatic way to do this is by putting that code inwhich are only run once the complete application is loaded."]]})

