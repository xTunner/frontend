##
## In production, we're behind an AWS load balancer, which decrypts
## all SSL traffic for us, making the built-in force SSL useless.
##

## This 'mostly' working, the only real issue is that we're not keying
## on the correct header that AWS sends to indicate whether the
## request was on https

class ForceSSL
  def initialize(app)
    @app = app
  end

  def call(env)
    if env['HTTPS'] == 'on' || env['HTTP_X_FORWARDED_PROTO'] == 'https'
      puts "ForceSSL: calling"
      @app.call(env)
    else
      req = Rack::Request.new(env)
      loc = req.url.gsub(/^http:/, "https:")
      puts "ForceSSL: redirecting to #{loc}"
      [301, { "Location" =>  loc}, []]
    end
  end
end
