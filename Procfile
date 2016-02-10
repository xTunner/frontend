web: lein run
figwheel: lein with-profile +devtools figwheel dev
# Run a separate figwheel for devcards while it requires a separate profile.
# Once Om 1.0 is released, this will no longer be necessary.
figwheel-devcards: lein with-profile +devcards figwheel devcards
nginx: nginx -c `pwd`/etc/nginx.conf
