(ns frontend.components.docs.php-memcached-compile-error)

(def article
  {:title "Adding memcached with pecl on CircleCI"
   :last-updated "May 23, 2013"
   :url :php-memcached-compile-error
   :content [:div
             [:p
              "CircleCI is built on Ubuntu 12.04, and PHP's memcached module clashes with this. If you try to run"]
             [:pre
              "’‘"
              [:code.no-highlight "’pecl install memcached-stable‘"]
              "’‘"]
             [:p "you may come across this error:"]
             [:pre
              "’‘"
              [:code.no-highlight
               "’In file included from /tmp/pear/temp/memcached/php_memcached.h:22:0,                 from /tmp/pear/temp/memcached/php_memcached.c:47:/tmp/pear/temp/memcached/php_libmemcached_compat.h:5:40: fatal error: libmemcached-1.0/memcached.h: No such file or directorycompilation terminated.make: *** [php_memcached.lo] Error 1ERROR: `make' failed‘"]
              "’‘"]
             [:p "The solution is to install an older version of memcached:"]
             [:pre
              "’‘"
              [:code.no-highlight "’pecl install -f memcached-2.0.1‘"]
              "’‘"]
             [:p
              "See"
              [:a
               {:href
                "https://github.com/php-memcached-dev/php-memcached/issues/33"}
               "https://github.com/php-memcached-dev/php-memcached/issues/33"]
              "for more details."]]})

