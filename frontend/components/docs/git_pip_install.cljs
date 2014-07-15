(ns frontend.components.docs.git-pip-install)

(def article
  {:title "Git errors during pip install"
   :last-updated "Feb 3, 2013"
   :url :git-pip-install
   :content [:div
             [:p
              "When your tests run, during the"
              [:code "pip install"]
              "step, you might see something like this:"]
             [:pre
              "’‘"
              [:code.no-highlight
               "’Obtaining somerepo from git+ssh://git@github.com/someorg/somerepo.git#egg=somerepo (from -r requirements.txt (line 23))Cloning ssh://git@github.com/someorg/somerepo.git to ./venv/src/somerepoComplete output from command /usr/bin/git clone -q ssh://git@github.com/someorg/somerepo.git /home/ubuntu/someorg/venv/src/somerepo:----------------------------------------Command /usr/bin/git clone -q ssh://git@github.com/someorg/somerepo.git /home/ubuntu/somerepo/venv/src/somerepo failed with error code 128 in NoneStoring complete log in /home/ubuntu/.pip/pip.logERROR: Repository not found.fatal: The remote end hung up unexpectedly‘"]
              "’‘"]
             [:p
              "This happens because you have a git repository listed as a dependency in your requirement.txt file:"]
             [:pre
              "’‘"
              [:code.ruby "’git+git://github.com/someorg/somerepo.git‘"]
              "’‘"]
             [:p
              "If the repository is public, just change the dependency to use a"
              [:code "http"]
              "url:"]
             [:pre
              "’‘"
              [:code.ruby "’git+http://github.com/someorg/somerepo.git‘"]
              "’‘"]
             [:p
              "If the repository is private, you will need to enable user keysfrom your project's"
              [:strong "Edit settings > GitHub user"]
              "page."]]})

