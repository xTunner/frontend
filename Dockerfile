FROM clojure:latest

RUN \
  curl --silent --location https://deb.nodesource.com/setup_0.12 | bash - && \
  apt-get update && \
  apt-get install -y haproxy nodejs bzip2 build-essential && \
  rm -rf /var/lib/apt/lists/*

WORKDIR /frontend

ADD project.clj /frontend/project.clj
RUN lein deps

ADD package.json /frontend/package.json
RUN npm install

ADD .bowerrc /frontend/.bowerrc
ADD bower.json /frontend/bower.json
RUN node_modules/.bin/bower --allow-root install

ADD . /frontend
#RUN lein cljsbuild once dev

EXPOSE 13000 17888 13449 14443 14444

ADD docker-entrypoint.sh /docker-entrypoint.sh

ENTRYPOINT ["/docker-entrypoint.sh"]
CMD ["start"]
