package scopes_service.query_pre_processor.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tenforce.semtech.SPARQLParser.SPARQL.InvalidSPARQLException;
import com.tenforce.semtech.SPARQLParser.SPARQL.SPARQLQuery;
import scopes_service.query_pre_processor.Scopes.Scope;
import scopes_service.query_pre_processor.Scopes.ScopeNode;
import scopes_service.query_pre_processor.callback.CallBackService;
import scopes_service.query_pre_processor.query.QueryService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import scopes_service.query_pre_processor.query.SPARQLService;
import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.*;

@RestController
public class RootController {

  @Inject
  private QueryService queryService;

  @Inject
  private CallBackService callBackService;

    private String userUUID = "JONATHANUUID";

    private String localSPARQLURL = "http://localhost:8890/sparql";

  /**
   * initializes the callback service with 2 call back sets (allDifferences and effectiveDifferences)
   */
  @PostConstruct
  public void init()
  {
    this.callBackService.addCallBackSet("allDifferences");
    this.callBackService.addCallBackSet("effectiveDifferences");
      if(System.getenv("SPARQLENDPOINT") != null && !System.getenv("SPARQLENDPOINT").isEmpty())
          this.localSPARQLURL = System.getenv("SPARQLENDPOINT");
  }

    @RequestMapping(value="/users", method = RequestMethod.GET)
    public ResponseEntity<String> getGraphUserSettings()
    {
        Map<String, String> graphUuids = new HashMap<String, String>();
        List<String> userUuids = new ArrayList<String>();

        try
        {
            String graphsJSON = SPARQLService.getInstance().getSPARQLResponse(SPARQLService.getLocalURL() +
            "?query=" + URLEncoder.encode("SELECT ?uuid ?name FROM <http://mu.semte.ch/graphs> WHERE {" +
                    "?uri a <http://mu.semte.ch/vocabularies/graphs/Graph> . ?uri <http://mu.semte.ch/vocabularies/core/uuid> ?uuid ." +
        "?uri <http://xmlns.com/foaf/0.1/name> ?name .}", "UTF-8"));


            ObjectMapper mapper = new ObjectMapper();
            Map<String, Object> jsonMap = mapper.readValue(graphsJSON, Map.class);
            Map<String, ScopeNode> nodeMap = new HashMap<String, ScopeNode>();

            List l = ((List<Map<String, Object>>) ((Map) jsonMap.get("results")).get("bindings"));

            for (Object tripleMap : l) {
                Map<String, Object> cTripleMap = (Map<String, Object>) tripleMap;

                Map<String, Object> sMap = (Map<String, Object>) cTripleMap.get("uuid");
                Map<String, Object> nMap = (Map<String, Object>) cTripleMap.get("name");
                graphUuids.put((String) sMap.get("value"), (String) nMap.get("value"));
            }

        }catch (Exception e)
        {
            e.printStackTrace();
        }

        try
        {
            String graphsJSON = SPARQLService.getInstance().getSPARQLResponse(SPARQLService.getLocalURL() +
                    "?query=" + URLEncoder.encode("SELECT ?uuid FROM <http://mu.semte.ch/graphs> WHERE {" +
                    "?uri a <http://xmlns.com/foaf/0.1/OnlineAccount> . ?uri <http://mu.semte.ch/vocabularies/core/uuid> ?uuid .}", "UTF-8"));


            ObjectMapper mapper = new ObjectMapper();
            Map<String, Object> jsonMap = mapper.readValue(graphsJSON, Map.class);
            Map<String, ScopeNode> nodeMap = new HashMap<String, ScopeNode>();

            List l = ((List<Map<String, Object>>) ((Map) jsonMap.get("results")).get("bindings"));

            for (Object tripleMap : l) {
                Map<String, Object> cTripleMap = (Map<String, Object>) tripleMap;

                Map<String, Object> sMap = (Map<String, Object>) cTripleMap.get("uuid");
                userUuids.add((String) sMap.get("value"));
            }

        }catch (Exception e)
        {
            e.printStackTrace();
        }

        String r = "{\"data\":[";

        for(String userUUID : userUuids)
        {
            r += "{\"user\":\"" + userUUID + "\", \"graphs\":[";
            for(String graphUUID : graphUuids.keySet())
            {

                r += "{\"graph\":\"" + graphUUID + "\", \"name\":\"" + graphUuids.get(graphUUID) + "\", \"access\":\"";
                boolean hasAccess = false;
                String query = "prefix mu:<http://mu.semte.ch/vocabularies/core/>\n" +
                        "prefix foaf:<http://xmlns.com/foaf/0.1/>\n" +
                        "prefix graphs:<http://mu.semte.ch/vocabularies/graphs/>\n" +
                        "\n" +
                        "ask\n" +
                        "{\n" +
                        "graph <http://mu.semte.ch/graphs>\n" +
                        "{\n" +
                        "?user graphs:hasAccessToGraph ?graph .\n" +
                        "?user mu:uuid \"" + userUUID + "\" .\n" +
                        "?graph mu:uuid \"" + graphUUID + "\" .\n" +
                        "}\n" +
                        "}";
                try {
                    String response = SPARQLService.getInstance().postSPARQLResponse(SPARQLService.getLocalURL(), query);
                    if(response.toLowerCase().equals("true"))
                    {
                        hasAccess = true;
                    }
                }catch(Exception e)
                {
                    e.printStackTrace();
                }
                if(hasAccess)
                {
                    r += "\"true\"";
                }
                else
                {
                    r += "\"false\"";
                }
                r += "},";
            }
            if(graphUuids.keySet().size() > 0)
                r = r.substring(0, r.length() - 1);
            r += "]},";
        }
        if(userUuids.size() > 0)
            r = r.substring(0, r.length() - 1);

        r += "]}";

        return new ResponseEntity<String>(r, HttpStatus.OK);
    }

    @RequestMapping(value="/graphs", method = RequestMethod.POST)
    public ResponseEntity<String> addGraph(HttpServletRequest request, HttpServletResponse response, @RequestBody String body)
    {
        try {
            ObjectMapper mapper = new ObjectMapper();
            Map<String, Object> jsonMap = mapper.readValue(body, Map.class);
            Map<String, ScopeNode> nodeMap = new HashMap<String, ScopeNode>();

            Map<String, Object> cTripleMap = (Map<String, Object>) ((Map) jsonMap.get("data")).get("attributes");

            String name = (String) cTripleMap.get("name");
            String type = (String) cTripleMap.get("type");

            String uuid = UUID.randomUUID().toString();
            String uri = "http://mu.semte.ch/vocabularies/graphs/Graph/" + uuid;

            String insertQuery = "prefix mu:<http://mu.semte.ch/vocabularies/core/>\n" +
                    "prefix foaf:<http://xmlns.com/foaf/0.1/>\n" +
                    "prefix graphs:<http://mu.semte.ch/vocabularies/graphs/>\n" +
                    "with <http://mu.semte.ch/graphs>\n" +
                    "insert\n" +
                    "{\n" +
                    "<" + uri + "> mu:uuid \"" + uuid + "\".\n" +
                    "<" + uri + "> a graphs:Graph.\n" +
                    "<" + uri + "> graphs:graphType \"" + type + "\".\n" +
                    "<" + uri + "> foaf:name \"" + name + "\"\n" +
                    "}";

            SPARQLService.getInstance().postSPARQLResponse(SPARQLService.getLocalURL(), insertQuery);

        }catch(Exception e)
        {
            e.printStackTrace();
        }

        return new ResponseEntity<String>("", HttpStatus.ACCEPTED);
    }

    @RequestMapping(value="/graphs", method = RequestMethod.GET)
    public ResponseEntity<String> getGraphs()
    {
        String getAllGraphsQuery = "prefix mu:<http://mu.semte.ch/vocabularies/core/>\n" +
                "prefix foaf:<http://xmlns.com/foaf/0.1/>\n" +
                "prefix graphs:<http://mu.semte.ch/vocabularies/graphs/>\n" +
                "select ?uuid ?name ?type\n" +
                "from <http://mu.semte.ch/graphs>\n" +
                "where\n" +
                "{\n" +
                "?uri mu:uuid ?uuid .\n" +
                "?uri foaf:name ?name .\n" +
                "?uri graphs:graphType ?type .\n" +
                "?uri a graphs:Graph .\n" +
                "}\n";

        String r = "{\"data\":[";

        try {
            String jsonString = this.queryService.sparqlService.getSPARQLResponse(this.localSPARQLURL + "?query=" + URLEncoder.encode(getAllGraphsQuery, "UTF-8"));

            ObjectMapper mapper = new ObjectMapper();
            Map<String, Object> jsonMap = mapper.readValue(jsonString, Map.class);
            Map<String, ScopeNode> nodeMap = new HashMap<String, ScopeNode>();

            List l = ((List<Map<String, Object>>) ((Map) jsonMap.get("results")).get("bindings"));

            for (Object tripleMap : l) {
                Map<String, Object> cTripleMap = (Map<String, Object>) tripleMap;

                Map<String, Object> sMap = (Map<String, Object>) cTripleMap.get("uuid");
                String uuid = (String) sMap.get("value");

                Map<String, Object> nMap = (Map<String, Object>) cTripleMap.get("name");
                String name = (String) nMap.get("value");

                Map<String, Object> tMap = (Map<String, Object>) cTripleMap.get("type");
                String type = (String) tMap.get("value");
                r += "{\"type\":\"graph\", \"id\":\"" + uuid + "\", \"attributes\":{";
                r += "\"name\":\"" + name + "\", \"type\":\"" + type + "\"}}";
            }
        }catch(Exception e)
        {
            e.printStackTrace();
        }

        r += "]}";

        return new ResponseEntity<String>(r, HttpStatus.OK);
    }

    @RequestMapping(value="/ping")
    public ResponseEntity<String> ping(HttpServletRequest request, HttpServletResponse response, @RequestBody(required = false) String body)
    {
        return new ResponseEntity<String>(System.getenv("SPARQLENDPOINT"), HttpStatus.OK);
    }

  @RequestMapping(value = "/sparql")
  public ResponseEntity<String> preProcessQuery(HttpServletRequest request, HttpServletResponse response, @RequestBody(required = false) String body) throws InvalidSPARQLException
  {
    try {
        String queryString;

        if (request.getParameterMap().containsKey("query")) {
            queryString = request.getParameter("query");
            try {
                queryString = URLDecoder.decode(queryString, "UTF-8");
            }catch(Exception e)
            {
                e.printStackTrace();
            }
        }
        else
        {
            queryString = body;
        }
        SPARQLQuery parsedQuery = new SPARQLQuery(queryString);

        if(parsedQuery.getType().equals(SPARQLQuery.Type.UPDATE)) {
            /*DifferenceTriples diff = this.queryService.getDifferenceTriples(parsedQuery);

            String resp = diff.getAllChangesAsJSON();

            String allJson = diff.getAllChangesAsJSON();
            String effectiveJson = diff.getEffectiveChangesAsJSON();

            try {
                this.callBackService.notifyCallBacks("effectiveDifferences", effectiveJson);
                this.callBackService.notifyCallBacks("allDifferences", allJson);
            } catch (CallBackSetNotFoundException cb) {
                cb.printStackTrace();
            }*/
        }
        String url = this.localSPARQLURL;

        String userUUID = this.userUUID;

        Enumeration<String> hn = request.getHeaderNames();
        while(hn.hasMoreElements())
        {
            if(hn.nextElement().toUpperCase().equals("MU-SESSION-ID"))
            {
                userUUID = this.getUserUUIDFromSession(request.getHeader("MU-SESSION-ID"));
            }
        }

        String graphName = this.getGraphName(userUUID);

        parsedQuery.replaceGraphStatements(graphName);

        String constructedQuery = parsedQuery.constructQuery();

        if(parsedQuery.getType().equals(SPARQLQuery.Type.UPDATE)) {
            return new ResponseEntity<String>(this.queryService.sparqlService.postSPARQLResponse(url, constructedQuery), HttpStatus.OK);
        }
        else
        {
            return new ResponseEntity<String>(this.queryService.sparqlService.getSPARQLResponse(url + "?query=" + URLEncoder.encode(constructedQuery, "UTF-8")), HttpStatus.OK);
        }

    }catch(InvalidSPARQLException e)
    {
      e.printStackTrace();
    } catch (MalformedURLException e) {
        e.printStackTrace();
    } catch (IOException e) {
        e.printStackTrace();
    }

      return ResponseEntity.ok("");
  }

    private String getGraphName(String userUUID)
    {
        String askGraphInstanceName = "WITH <http://mu.semte.ch/instances> SELECT ?instance WHERE { ";
        askGraphInstanceName += "?user <http://mu.semte.ch/vocabularies/core/uuid> \"" + userUUID + "\" .\n";
        askGraphInstanceName += "?user <http://mu.semte.ch/vocabularies/graphs/hasInstanceGraph> ?instanceuri .\n";
        askGraphInstanceName += "?instanceuri <http://mu.semte.ch/vocabularies/graphs/hasGraph> ?instance .\n}";

        try {
            String jsonString = this.queryService.sparqlService.getSPARQLResponse(this.localSPARQLURL + "?query=" + URLEncoder.encode(askGraphInstanceName, "UTF-8"));

            String instanceName = "";
            ObjectMapper mapper = new ObjectMapper();
            Map<String, Object> jsonMap = mapper.readValue(jsonString, Map.class);
            Map<String, ScopeNode> nodeMap = new HashMap<String, ScopeNode>();

            List l =((List<Map<String,Object>>)((Map) jsonMap.get("results")).get("bindings"));

            for(Object tripleMap : l) {
                Map<String, Object> cTripleMap = (Map<String, Object>) tripleMap;

                Map<String, Object> sMap = (Map<String, Object>) cTripleMap.get("instance");
                instanceName = (String) sMap.get("value");
            }

            if(!instanceName.isEmpty())
            {
                return instanceName;
            }
            else
            {
                Scope scope = this.buildScopeForUser(userUUID);
                return scope.calculateGraphToQuery();
            }
        } catch (IOException e) {
            e.printStackTrace();
            return "ERROR";
        }
    }

    private String getUserUUIDFromSession(String sessionID)
    {
        String getuseruuid = "\n" +
                "select ?useruuid\n" +
                "from <http://mu.semte.ch/graphs>\n" +
                "where\n" +
                "{\n" +
                "<" + sessionID + "> <http://mu.semte.ch/vocabularies/session/account> ?useruri .\n" +
                "?useruri <http://mu.semte.ch/vocabularies/core/uuid> ?useruuid .\n" +
                "}";

	System.out.println(getuseruuid);

        try {
            String jsonString = this.queryService.sparqlService.getSPARQLResponse(this.localSPARQLURL + "?query=" + URLEncoder.encode(getuseruuid, "UTF-8"));
	    System.out.println(jsonString);
            ObjectMapper mapper = new ObjectMapper();
            Map<String, Object> jsonMap = mapper.readValue(jsonString, Map.class);
            Map<String, ScopeNode> nodeMap = new HashMap<String, ScopeNode>();

            List l = ((List<Map<String, Object>>) ((Map) jsonMap.get("results")).get("bindings"));

            for (Object tripleMap : l) {
                Map<String, Object> cTripleMap = (Map<String, Object>) tripleMap;

                Map<String, Object> sMap = (Map<String, Object>) cTripleMap.get("useruuid");
                return (String) sMap.get("value");
            }
        }catch (Exception e)
        {
            e.printStackTrace();
        }
        return "";
    }

    private Scope buildScopeForUser(String userUUID)
    {
        String getNodeInfo = "WITH <http://mu.semte.ch/graphs> SELECT ?node ?graphName ?graphType ?graphQuery WHERE { ?user <http://mu.semte.ch/vocabularies/core/uuid> \"" + userUUID;
        getNodeInfo += "\" .\n?user <http://mu.semte.ch/vocabularies/graphs/hasNode> ?node .\n";
        getNodeInfo += "?node <http://mu.semte.ch/vocabularies/graphs/forGraph> ?graph .\n";
        getNodeInfo += "?graph <http://mu.semte.ch/vocabularies/graphs/graphName> ?graphName .\n";
        getNodeInfo += "?graph <http://mu.semte.ch/vocabularies/graphs/graphType> ?graphType .\n";
        getNodeInfo += "?graph <http://mu.semte.ch/vocabularies/graphs/graphQuery> ?graphQuery .\n}";

        Scope scope = new Scope(userUUID);

        try {
            String jsonString = this.queryService.sparqlService.getSPARQLResponse(this.localSPARQLURL + "?query=" + URLEncoder.encode(getNodeInfo, "UTF-8"));
            ObjectMapper mapper = new ObjectMapper();
            Map<String, Object> jsonMap = mapper.readValue(jsonString, Map.class);
            Map<String, ScopeNode> nodeMap = new HashMap<String, ScopeNode>();

            List l =((List<Map<String,Object>>)((Map) jsonMap.get("results")).get("bindings"));

            for(Object tripleMap : l) {
                Map<String, Object> cTripleMap = (Map<String, Object>) tripleMap;

                Map<String, Object> sMap = (Map<String, Object>) cTripleMap.get("node");
                String node = (String) sMap.get("value");

                Map<String, Object> pMap = (Map<String, Object>) cTripleMap.get("graphName");
                String graphName = (String) pMap.get("value");

                Map<String, Object> tMap = (Map<String, Object>) cTripleMap.get("graphType");
                String graphType = (String) tMap.get("value");

                ScopeNode scopeNode = new ScopeNode();
                scopeNode.setUUID(graphName);
                scopeNode.setName(node);
                int scopeNodeType = 1;
                if(graphType.equals("hive"))
                    scopeNodeType = 4;
                scopeNode.setScopeNodeType(scopeNodeType);

                nodeMap.put(node, scopeNode);

                scope.getNodes().add(scopeNode);
            }
            for(String node : nodeMap.keySet())
            {
                String getNodeParent = "WITH <http://mu.semte.ch/graphs> SELECT ?parent WHERE { ";
                getNodeParent += "<" + node + "> <http://mu.semte.ch/vocabularies/graphs/hasParent> ?parent .\n}";

                String jsonStringP = this.queryService.sparqlService.getSPARQLResponse(this.localSPARQLURL + "?query=" + URLEncoder.encode(getNodeParent, "UTF-8"));
                ObjectMapper mapperP = new ObjectMapper();
                Map<String, Object> jsonMapP = mapperP.readValue(jsonStringP, Map.class);

                List lP =((List<Map<String,Object>>)((Map) jsonMapP.get("results")).get("bindings"));

                for(Object tripleMap : lP) {
                    Map<String, Object> cTripleMap = (Map<String, Object>) tripleMap;

                    Map<String, Object> parent = (Map<String, Object>) cTripleMap.get("parent");
                    ScopeNode sn = ((ScopeNode) nodeMap.get(node));
                    ScopeNode pn = ((ScopeNode) nodeMap.get(parent.get("value")));
                    sn.setParent(pn);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        scope.calculateGraphToQuery();

        return scope;
    }


}
