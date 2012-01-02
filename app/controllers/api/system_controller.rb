
module Api
  class SystemController < ApplicationController
    ## API functions. Users authenticate via http basic auth, using
    ## devise. i.e. include http basic auth headers in the request, for
    ## a normal user that's in the DB.

    authorize_resource
    def shutdown
      puts "shutdown!"
      # render :text => ""
      head :ok
    end
  end
end
