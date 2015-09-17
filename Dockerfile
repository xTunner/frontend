FROM clojure:latest

RUN \
  curl --silent --location https://deb.nodesource.com/setup_0.12 | bash - && \
  apt-get update && \
  apt-get install -y haproxy nodejs bzip2 build-essential && \
  rm -rf /var/lib/apt/lists/*

RUN echo "\
global                                     \n\
    daemon                                 \n\
    maxconn 4096                           \n\
                                           \n\
listen http 0.0.0.0:13000                  \n\
  mode tcp                                 \n\
  server master 127.0.0.1:3000             \n\
  timeout client 3600s                     \n\
  timeout connect 3600s                    \n\
  timeout server 3600s                     \n\
                                           \n\
listen repl 0.0.0.0:17888                  \n\
  mode tcp                                 \n\
  server master 127.0.0.1:7888             \n\
  timeout client 3600s                     \n\
  timeout connect 3600s                    \n\
  timeout server 3600s                     \n\
                                           \n\
listen figwheel 0.0.0.0:13449              \n\
  mode tcp                                 \n\
  server master 127.0.0.1:3449             \n\
  timeout client 3600s                     \n\
  timeout connect 3600s                    \n\
  timeout server 3600s                     \n\
"> /etc/haproxy/haproxy.cfg

ADD docker-entrypoint.sh /docker-entrypoint.sh

WORKDIR /frontend

ADD project.clj /frontend/project.clj
RUN lein deps

ADD package.json /frontend/package.json
RUN npm install

ADD .bowerrc /frontend/.bowerrc
ADD bower.json /frontend/bower.json
RUN node_modules/.bin/bower --allow-root install

ADD . /frontend
RUN lein cljsbuild once dev

EXPOSE 13000 17888 13449

ENTRYPOINT ["/docker-entrypoint.sh"]
CMD ["start"]
