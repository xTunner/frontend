<!--

title: Rake giving ActionFailed errors
last_updated: Feb 3, 2013

-->

Sometimes in Rails projects you can see Rake throw ActionFailed errors
which simply do not make sense. If you are using `Test::Unit`, it may
be the problem.

Test::Unit installs an `at_exit` handler that will automatically try to
run the tests when the Ruby process exits. Basically, as soon as you
require 'test/unit' it installs this handler.
It processes all the CLI args as well at this step.

So first off it tries to run after your Rake task and then tries to
parse Rake's CLI args, which is where it breaks.

The source of the error is that somewhere in the code loaded by your
Rakefile is something that requires test/unit.

## Solution

An appropriate fix has been suggested [in this blog
post](http://www.jonathanleighton.com/articles/2012/stop-test-unit-autorun/)—
monkey-patching `Test::Unit` to disable this behavior:

```
require 'test/unit'

class Test::Unit::Runner
  @@stop_auto_run = true
end

class FooTest < Test::Unit::TestCase
  def test_foo
    assert true
  end
end

Test::Unit::Runner.new.run(ARGV)
```
