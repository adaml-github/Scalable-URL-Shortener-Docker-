FROM java:8
COPY a1 /usr/src/myapp
WORKDIR /usr/src/myapp
RUN javac -classpath .:./lib/jedis-2.8.1.jar:./lib/commons-pool2-2.4.2.jar:./lib/postgresql-42.2.5.jar URLShortner.java
EXPOSE 8080
CMD ["java","-classpath",".:./lib/jedis-2.8.1.jar:./lib/commons-pool2-2.4.2.jar:./lib/postgresql-42.2.5.jar", "URLShortner"]
