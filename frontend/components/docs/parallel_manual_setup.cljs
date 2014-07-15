(ns frontend.components.docs.parallel-manual-setup)

(def article
  {:title "Manually setting up parallelism"
   :last-updated "Feb 2, 2013"
   :url :parallel-manual-setup
   :content [:div
             [:p
              "If you want the benefits of parallel testing, and you're notusing one of our automatically supported test runners, or ifyou've overridden our test commands, you'll still be able to set up parallelism and reduce your test run-times."]
             [:p
              "To begin with, you'll need to turn on parallelism from your project's settings page.Go to "
              [:strong "Edit settings > Parallelism"]
              " to adjust the settings."]
             [:h2 "Splitting your test suite"]
             [:p
              "When you use CircleCI's parallelization, we run your code on multiple separate VMs.To use parallelism, you make your test runner run only a subset of tests on each VM.There are two mechanisms for splitting tests among nodes:  Using the "
              [:code "files"]
              "configuration modifier - a very simply and straightforward way for most use cases, andusing parallelism environment variables - aimed for the more complex scenarios."]
             [:h2#files-splitting
              "Using configuration "
              [:code "files"]
              " modifier"]
             [:p
              "Parallelizing test runners that accept file names is straightforward!  The "
              [:code "files"]
              " modifiercan list paths to the test files, and CircleCI will run the test runners with different test files in each node.For example, to parallelize an rspec command, you can set the following:"]
             [:pre
              "’‘"
              [:code
               "’test:  override:    - bundle exec rspec:        parallel: true        files:          - spec/unit/sample.rb   # can be a direct path to file          - spec/**/*.rb          # or a glob (ruby globs)‘"]
              "’‘"]
             [:p
              "In this example, we will run `bundle exec rspec` in all nodes appended withroughly "
              [:code "1/N"]
              " of the files on each VM."]
             [:p
              "By default, the file arguments will be appended to the end of the command.Support for positional arguments is coming very soon."]
             [:h2#env-splitting "Using environment variables"]
             [:p
              "For more control over parallelism, we use environment variables to denote the number of VMs and to identify each one, and you can access these from your test runner:"]
             [:dl
              [:dt [:code "CIRCLE_NODE_TOTAL"]]
              [:dd
               "is the total number of parallel VMs being used to run your tests on each push."]
              [:dt [:code "CIRCLE_NODE_INDEX"]]
              [:dd
               "is the index of the particular VM."
               [:code "CIRCLE_NODE_INDEX"]
               "is indexed from zero."]]
             [:h3#simple-example "A simple example"]
             [:p
              "If you want to run the two commands"
              [:code "rake spec"]
              "and"
              [:code "npm test"]
              "in parallel, you can use a bash case statement:"]
             [:pre
              "’‘"
              [:code
               "’test:  override:    - case $CIRCLE_NODE_INDEX in 0) rake spec ;; 1) npm test ;; esac:        parallel: true‘"]
              "’‘"]
             [:p
              "Note the final colon, and"
              [:code "parallel: true"]
              "on the next line.This is a command modifier which tells circle that the command should be run in parallel on all test machines. It defaults to true for commands in the machine, checkout, dependencies and database build phases, and it defaults to false for commands in the test and deployment phases."]
             [:p
              "Obviously, this is slightly limited because it's hard-coded toonly work with two nodes, and the test time might not balanceacross all nodes equally."]
             [:h3#balancing "Balancing"]
             [:p
              "A more powerful version evenly splits all test files across N nodes. We recommend you write a script that does something like:"]
             [:pre
              "’‘"
              [:code
               "’#!/bin/bashi=0files=()for file in $(find ./test -name \\*.py\\ | sort)do  if [ $(($i % $CIRCLE_NODE_TOTAL)) -eq $CIRCLE_NODE_INDEX ]  then    files+=\\ $file\\  fi  ((i++))donetest-runner ${files[@]}‘"]
              "’‘"]
             [:p
              "This script partitions the test files into N equally sized buckets, and calls \\test-runner\\ on the bucket for this machine."]
             [:h2 "Contact Us"]
             [:p
               "If you set this up for a library or framework that we should beable to infer automatically, pleaseWe are always interested in adding support for more languages and frameworks."]]})
