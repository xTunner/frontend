# CircleCI's frontend

This is an open-source mirror of the code that is running
[CircleCI's](https://circleci.com) frontend. CircleCI provides powerful
Continuous Integration and Deployment with easy setup and maintenance.

Feel free to fork and make contributions. We'll try to get them into the main
application.


## Dependencies and Setup

### Node.js

Install [Node.js](http://nodejs.org/) and node dependencies:

```
npm install
```

Download all of the 3rd-party javascript dependencies:

```
node_modules/.bin/bower install
```

### Clojure

Install [Leiningen](http://leiningen.org/).

### Hosts

In your `/etc/hosts`, add the following line:

```
127.0.0.1 prod.circlehost
```

If you have access to the backend code, you can also add this line:

```
127.0.0.1 dev.circlehost
```


## Usage

### Development Processes

Two processes must be running while you work.

First, start the HTTP server that will serve the compiled assets on port 3000:

```
lein run
```

Second, the frontend clojurescript asset compiler:

```
lein cljsbuild auto dev
```

### Sanity Check

To test that everything worked, visit
[http://prod.circlehost:3000/assets/css/app.css](http://prod.circlehost:3000/assets/css/app.css)
and
[http://prod.circlehost:3000/assets/js/om-dev.js.stefon](http://prod.circlehost:3000/assets/js/om-prod.js.stefon)
in your browser.

### Production & Development Backends

Now you should have everything you need to start hacking on Circle's frontend!

Visit
[http://prod.circlehost:3000?om-build-id=dev](http://prod.circlehost:3000?om-build-id=dev)
for the a production backend with locally build development assets. Again, if
you've got access to the backend code (NOTE: it's not open source), you can
run it locally on `circlehost:8080`. To connect to the development backend,
visit [http://dev.circlehost:3000](http://dev.circlehost:3000). The dev server
will default to dev assets, so you don't need the query parameter.

**NOTE:** login and logout will invalidate the session, so you'll need to use
the `?om-build-id=dev` query parameter again. This will be fixed soon.
