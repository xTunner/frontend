class FetcherController < ApplicationController
  def done
    id = params[:id].to_i
    # TODO: add security here
    if Backend.worker_done? id then
      render :text => Backend.wait_for_worker(id).to_json
    else
      render :status => 404
    end
  end
end
