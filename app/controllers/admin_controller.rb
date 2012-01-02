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

class AdminController < ApplicationController
  before_filter :authenticate_user!
  authorize_resource :class => false

  def show
    @projects = Project.order_by([[:vcs_url, :asc]])
    @builds = Build.order_by([[:start_time, :desc]]).limit(20).all
  end

  def sandbox
    SimpleMailer.test_email
  end

end
