# All this is [experi]-MENTAL

All this code is experimental and is not intended to be used for anything! Further I introduce some new things for practical purposes, because they are handy and/or because I am too lazy to try to come up with better solutions at this point. Everything is still up for debate but we want something usable quick. So this thing, whatever it is, will not follow the idea we talked about fully. It will however adhere to all the general principles and it will, hopefully, be a source of inspiration, a testiment that the idea we have might just be valid.

# What is here already?

## For now

A lot of things are for now.

## No API's whatsoever

Sorry no api's whatsoever! What does this mean? Well instead of having a GUI we will have to inject triples in graphs to make this thing sing.

## SPARQL Graph free

Whatever you graph you specify will be replaced with the graph that is currently your graph. If you don't specify a graph then your 'current' graph is added to your queries. This means that you can already use graph-less (or graph-free) queries. For now I have switched everything that would be in the application graph to be in the graphs graph. At this point I have the following meta-graphs in mind:
* the meta-graph which describes that metagraphs and what would fall in their bucket by default
* a user meta-graph
* a graphs meta-graph (describes 'graphs' and what users have access)
* a nodes meta-graph (describes the tree we drew on the whiteboard for every user)
* a update meta-graph (describes for every user: the sensible default, if this is none then all triples that would go there are thrown away <- seems like a handy feature should we have a anonymous user, and all it's buckets.
* a instances meta-graph (describes just ready made instances, this is handy for knowing what to update and to facilitate gets)

## all GET's should work

All get's should work, some updates may look like they might work. But updates are not supported yet whatsoever.

## it supports complete trees

The way we drew it on the board is how it works. The cardinality of the nodes is important, the computation of the eventual graph is more or less done in the way we drew it out on the whiteboard. I think we want to allow for subscopes but just write it to remind myself of this. Anyway the complex seven node tree is no problem, I have tested it!.

# How to use?

## Prepping (heating the spoon, taking a fresh needle, pumping out the air, ... you know the drill)

Although I forsee the use of several meta-graphs and what not for now I use only 2. The instance graph at <http://mu.semte.ch/instances> which will hold all calculated instances. And the graphs graph <http://mu.semte.ch/graphs> which functions as all meta-graphs. This means that to make this work you will need to insert some data in the graphs graph. The instances are taken care of by the system. To insert the correct data execute the following query on your SPARQL endpoint:
```
prefix mu:<http://mu.semte.ch/vocabularies/core/>
prefix foaf:<http://xmlns.com/foaf/0.1/>
prefix graphs:<http://mu.semte.ch/vocabularies/graphs/>
with <http://mu.semte.ch/graphs>
insert
{
<http://mu.semte.ch/users/jonathan> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type>	<http://xmlns.com/foaf/0.1/OnlineAccount>;
				    <http://mu.semte.ch/vocabularies/core/uuid>	"JONATHAN";
				    <http://mu.semte.ch/vocabularies/account/status> <http://mu.semte.ch/vocabularies/account/status/active>;
				    graphs:hasAccessToGraph <http://mu.semte.ch/graphs/graph1>, <http://mu.semte.ch/graphs/personalGraph1>;
				    graphs:hasNode <http://mu.semte.ch/nodes/1>, <http://mu.semte.ch/nodes/2>.
<http://mu.semte.ch/nodes/1> graphs:forGraph <http://mu.semte.ch/graphs/graph1>; <http://mu.semte.ch/vocabularies/graphs/hasParent> <http://mu.semte.ch/nodes/2>.
<http://mu.semte.ch/nodes/2> graphs:forGraph <http://mu.semte.ch/graphs/personalGraph1>.
				    
<http://mu.semte.ch/sessions/0c2e05a5-0b52-419c-a99a-394c30979706> <http://mu.semte.ch/vocabularies/session/account> <http://mu.semte.ch/users/jonathan>;
								     <http://mu.semte.ch/vocabularies/core/uuid> "SESSIONJONATHAN".

<http://mu.semte.ch/graphs/graph1> mu:uuid "GRAPH1";
graphs:graphName "http://mu.semte.ch/application";
graphs:graphType "hive";
graphs:graphQuery "";
a graphs:Graph.
<http://mu.semte.ch/graphs/personalGraph1> mu:uuid "PERSONALGRAPH1";
graphs:graphName "http://langens.jonathan/Personal-Graph-1";
graphs:graphType "personal";
graphs:graphQuery "";
a graphs:Graph.
}

```

## What are these graphs?

After this is done there are 2 graphs created, the application graph and the personal graph. You can also verify that since no default update point nor any baskets are set that no update will be persistent (at least not beyond the instance graphs, and they don't matter anyway).

As you guessed these graphs are a little bit more complex than the concept of a graph that is used in a triple store. I refer to our whiteboard to explain. A graph has a set of inserts (this is just the graph as you would expect), a set of deletes and a query (only half-assed implemented so this doesn't work). The application is a hive graph (level 4 on the whiteboard) so it's deletes are mute.
To test the functionality you can, again using your SPARQL endpoint insert data in the following graphs:
* <http://mu.semte.ch/application>
* <http://mu.semte.ch/graphs/personalGraph1>
* <http://mu.semte.ch/graphs/personalGraph1/delete>

Normally the instance graph should be what you expect it to be using the 'graph math'.

# now you want to query, no?

Well first you have to start the endpoint, do this by running the following command:
```
docker run -e SPARQLENDPOINT=http://x.x.x.x:8890/sparql flowofcontrol/scoping-scopes
```

Then you can send queries to this thing all day long. I will include more stuff in the example data but for now this is what it is. To query the instance graph for me you can send the following request:
```
method: GET
header: "MU-SESSION-ID":"http://mu.semte.ch/sessions/0c2e05a5-0b52-419c-a99a-394c30979706"
url: http://[IP OF THIS DOCKER]:8080/sparql?query=select%20*%20where%20%7B%3Fs%20%3Fp%20%3Fo%20.%7D
```

Of course if you want to send a POST and have the query in the body (see the SPARQL spec for more info) that is also perfectably usable!
