FROM tomcat

COPY target/SCOPES-ENDPOINT-1.0-SNAPSHOT.war /usr/local/tomcat/webapps/scopes.war

EXPOSE 8080
