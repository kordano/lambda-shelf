FROM ubuntu:latest

# Update the APT cache
RUN apt-get update

# Install and setup project dependencies
RUN apt-get install -y curl git maven

# prepare for Java download
RUN apt-get install -y python-software-properties
RUN apt-get install -y software-properties-common

# grab oracle java (auto accept licence)
RUN add-apt-repository -y ppa:webupd8team/java
RUN apt-get update
RUN echo oracle-java7-installer shared/accepted-oracle-license-v1-1 select true | /usr/bin/debconf-set-selections
RUN apt-get install -y oracle-java7-installer

# grab leiningen
RUN curl https://raw.github.com/technomancy/leiningen/stable/bin/lein -o /usr/local/bin/lein
RUN chmod +x /usr/local/bin/lein
RUN lein

# grab project
RUN git clone https://github.com/kordano/lambda-shelf.git /usr/local/src/lambda-shelf

# define port
EXPOSE 8080

# create uberjar with leiningen
RUN cd /usr/local/src/nidaba; lein uberjar
