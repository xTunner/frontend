  CircleCI supports testing Android applications. The SDK is
  already installed on the VM at
  `
    /usr/local/android-sdk-linux
  `

  To save space, we don't download
  every android version, so you'll need to specify the versions
  you use:

```
dependencies:
  pre:
    - echo y | android update sdk --no-ui --filter "android-18"```

  Note that if you need extended SDK components, such as build-tools, you'll
  also need to install those separately. For example:

```
dependencies:
  pre:
    - echo y | android update sdk --no-ui --filter "build-tools-20.0.0"```

### Caching Android SDK components

  Installing SDK components can be expensive if you do it every time, so to speed
  up your builds, it is wise to copy
  the Android SDK to your home directory and add it to the set of cached directories.
  Also, we want to have `ANDROID_HOME` point to this new location.

  To accomplish this, put something like the following in your `circle.yml`:

<pre>machine:&#x000A;  environment:&#x000A;    ANDROID_HOME: /home/ubuntu/android&#x000A;dependencies:&#x000A;  cache_directories:&#x000A;    - ~/.android&#x000A;    - ~/android&#x000A;  override:&#x000A;    - ./install-dependencies.sh
</pre>

  This references the `install-dependencies.sh`
  script. This is a script that you should write that installs
  any Android dependencies, creates any test AVDs that you'll need, etc.
  It should only do this once, so it should have a check to see
  if the cached directories already have all of your dependencies.

  For example, here is a basic `install-dependencies.sh`
  that installs `android-18`
  and some common tools. It also installs the x86 emulator image
  and builds an AVD called `testing`.
  (Note that if you ever change this file, you should clear your CircleCI
  cache.)

<pre>
  &#x000A;#!/bin/bash&#x000A;&#x000A;# Fix the CircleCI path&#x000A;export PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/tools:$PATH"&#x000A;&#x000A;DEPS="$ANDROID_HOME/installed-dependencies"&#x000A;&#x000A;if [ ! -e $DEPS ]; then&#x000A;  cp -r /usr/local/android-sdk-linux $ANDROID_HOME &&&#x000A;  echo y | android update sdk -u -a -t android-18 &&&#x000A;  echo y | android update sdk -u -a -t platform-tools &&&#x000A;  echo y | android update sdk -u -a -t build-tools-20.0.0 &&&#x000A;  echo y | android update sdk -u -a -t sys-img-x86-android-18 &&&#x000A;  echo y | android update sdk -u -a -t addon-google_apis-google-18 &&&#x000A;  echo n | android create avd -n testing -f -t android-18 &&&#x000A;  touch $DEPS&#x000A;fi
</pre>

### Starting the Android emulator

  For your actual tests, the first thing you should do is start up
  the emulator, as this usually takes several minutes, sadly.

  It's best if you can separate your actual build from installing it and
  running tests on the emulator. If at all possible, start the build, and
  then you MUST wait for the emulator to finish booting before
  installing the APK and running your tests.

  To wait for the emulator to boot, you need to wait for the
  `init.svc.bootanim` property to be set to
  `stopped`.

  Here's an example script for that, `wait.sh`:

<pre>
  &#x000A;#!/bin/bash&#x000A;&#x000A;export PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/tools:$PATH"&#x000A;&#x000A;while true; do&#x000A;  BOOTUP=$(adb shell getprop init.svc.bootanim | grep -oe '[a-z]\+')&#x000A;  if [[ "$BOOTUP" = "stopped" ]]; then&#x000A;    break&#x000A;  fi&#x000A;&#x000A;  echo "Got: '$BOOTUP', waiting for 'stopped'"&#x000A;  sleep 5&#x000A;done
</pre>

  Then, you need to boot up your emulator (in the background), start your build, wait for your
  emulator to finish booting, and then run your tests.
  In your `circle.yml` file, it will look something like

<pre>test:&#x000A;  override:&#x000A;  - $ANDROID_HOME/tools/emulator -avd testing -no-window -no-boot-anim -no-audio:&#x000A;      background: true&#x000A;  - # start your build here&#x000A;  - ./wait.sh&#x000A;  - # install your APK&#x000A;  - # run your tests
</pre>

### Running your tests

  The standard way to run tests in the Android emulator is with something like

<pre>
  &#x000A;adb logcat &&#x000A;adb wait-for-device&#x000A;adb shell am instrument -w com.myapp.test/android.test.InstrumentationTestRunner&#x000A;
</pre>

  Unfortunately, this always succeeds, even if the tests fail.
  (There's a known bug that `adb shell` doesn't set its exit
  code to reflect the command that was run.
  See [Android issue 3254](https://code.google.com/p/android/issues/detail?id=3254).)

  The only way around this is to parse your test output in a script
  and check to see if your tests passed.
  For example, if the tests pass, there should be a line that looks like
  `OK (15 tests)`.

  Here's an example bash script that uses Python to look for that pattern,
  and exits with code 0 (success) if the success line is found, and otherwise
  with code 1 (error).

<pre>
  &#x000A;#!/bin/bash&#x000A;&#x000A;export PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/tools:$PATH"&#x000A;&#x000A;pushd YourTestApp&#x000A;&#x000A;# clear the logs&#x000A;adb logcat -c&#x000A;&#x000A;# run tests and check output&#x000A;python - << END&#x000A;import re&#x000A;import subprocess as sp&#x000A;import sys&#x000A;import threading&#x000A;import time&#x000A;&#x000A;done = False&#x000A;&#x000A;def update():&#x000A;  # prevent CircleCI from killing the process for inactivity&#x000A;  while not done:&#x000A;    time.sleep(5)&#x000A;    print "Running..."&#x000A;&#x000A;t = threading.Thread(target=update)&#x000A;t.dameon = True&#x000A;t.start()&#x000A;&#x000A;def run():&#x000A;  sp.Popen('adb wait-for-device').communicate()&#x000A;  p = sp.Popen('adb shell am instrument -w com.myapp.test/android.test.InstrumentationTestRunner',&#x000A;               shell=True, stdout=sp.PIPE, stderr=sp.PIPE, stdin=sp.PIPE)&#x000A;  return p.communicate()&#x000A;&#x000A;success = re.compile(r'OK \(\d+ tests\)')&#x000A;stdout, stderr = run()&#x000A;&#x000A;done = True&#x000A;print stderr&#x000A;print stdout&#x000A;&#x000A;if success.search(stderr + stdout):&#x000A;  sys.exit(0)&#x000A;else:&#x000A;  sys.exit(1) # make sure we fail if the test failed&#x000A;END&#x000A;&#x000A;RETVAL=$?&#x000A;&#x000A;# dump the logs&#x000A;adb logcat -d&#x000A;&#x000A;popd&#x000A;exit $RETVAL&#x000A;
</pre>

* * *
</hr>

  Please don't hesitate to
  [contact us](mailto:sayhi@circleci.com)
  if you have any questions at all about how to best test Android on
  CircleCI.