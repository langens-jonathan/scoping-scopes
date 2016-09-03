to run call:
```
docker run -e SPARQLENDPOINT=http://x.x.x.x:8890/sparql flowofcontrol/scoping-scopes
```


example call

method: GET
header: "user-uuid":"JONATHANUUID"
url: http://[IP OF THIS DOCKER]:8080/sparql?query=select%20*%20where%20%7B%3Fs%20%3Fp%20%3Fo%20.%7D

example call 2

method: GET
header: "user-uuid":"ERIKAUUID"
url: http://[IP OF THIS DOCKER]:8080/sparql?query=select%20*%20where%20%7B%3Fs%20%3Fp%20%3Fo%20.%7D
