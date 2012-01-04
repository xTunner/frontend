# HTTPS fails in JRuby --1.9, this works around it until they fix it in 1.6.6.
# http://jira.codehaus.org/browse/JRUBY-5529
# https://gist.github.com/969527
Net::BufferedIO.class_eval do
  def rbuf_fill
    timeout(@read_timeout) {
      @rbuf << @io.sysread(1024 * 16)
    }
  end
end
