
module Api
  class SystemController < ApplicationController
    ## API functions. Users authenticate via http basic auth, using
    ## devise. i.e. include http basic auth headers in the request, for
    ## a normal user that's in the DB.

    authorize_resource
    def shutdown
      JRClj.new("circle.system").graceful_shutdown()
      head :ok
    end

    def ping
      JRClj.new("circle.system")
      head :ok
    end

  end
end
