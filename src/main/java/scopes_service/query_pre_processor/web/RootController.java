package scopes_service.query_pre_processor.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tenforce.semtech.SPARQLParser.SPARQL.InvalidSPARQLException;
import com.tenforce.semtech.SPARQLParser.SPARQL.SPARQLQuery;
import scopes_service.query_pre_processor.Scopes.Scope;
import scopes_service.query_pre_processor.Scopes.ScopeNode;
import scopes_service.query_pre_processor.callback.CallBackService;
import scopes_service.query_pre_processor.callback.CallBackSetNotFoundException;
import scopes_service.query_pre_processor.query.DifferenceTriples;
import scopes_service.query_pre_processor.query.QueryService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import scopes_service.query_pre_processor.query.Triple;

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

  /**
   * initializes the callback service with 2 call back sets (allDifferences and effectiveDifferences)
   */
  @PostConstruct
  public void init()
  {
    this.callBackService.addCallBackSet("allDifferences");
    this.callBackService.addCallBackSet("effectiveDifferences");
  }

    @RequestMapping(value="/setUser?")
    public ResponseEntity<String> ping(HttpServletRequest request, HttpServletResponse response, @RequestBody(required = false) String body)
    {
        return new ResponseEntity<String>("pong", HttpStatus.OK);
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
        String url = "http://localhost:8890/sparql";

        String userUUID = this.userUUID;

        Enumeration<String> hn = request.getHeaderNames();
        while(hn.hasMoreElements())
        {
            if(hn.nextElement().equals("user-uuid"))
            {
                userUUID = request.getHeader("user-uuid");
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
        String askGraphInstanceName = "WITH <http://mu.semte.ch/application> SELECT ?instance WHERE { ";
        askGraphInstanceName += "?user <http://mu.semte.ch/vocabularies/core/uuid> \"" + userUUID + "\" .\n";
        askGraphInstanceName += "?user <http://mu.semte.ch/vocabularies/core/hasInstanceGraph> ?instanceuri .\n";
        askGraphInstanceName += "?instanceuri <http://mu.semte.ch/vocabularies/core/hasGraph> ?instance .\n}";

        try {
            String jsonString = this.queryService.sparqlService.getSPARQLResponse("http://localhost:8890/sparql?query=" + URLEncoder.encode(askGraphInstanceName, "UTF-8"));

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

    private Scope buildScopeForUser(String userUUID)
    {
        String getNodeInfo = "WITH <http://mu.semte.ch/application> SELECT ?node ?graphName ?graphType WHERE { ?user <http://mu.semte.ch/vocabularies/core/uuid> \"" + userUUID;
        getNodeInfo += "\" .\n?user <http://mu.semte.ch/vocabularies/core/hasNode> ?node .\n";
        getNodeInfo += "?node <http://mu.semte.ch/vocabularies/core/forGraph> ?graph .\n";
        getNodeInfo += "?graph <http://mu.semte.ch/vocabularies/core/graphName> ?graphName .\n";
        getNodeInfo += "?graph <http://mu.semte.ch/vocabularies/core/graphType> ?graphType .\n}";

        Scope scope = new Scope(userUUID);

        try {
            String jsonString = this.queryService.sparqlService.getSPARQLResponse("http://localhost:8890/sparql?query=" + URLEncoder.encode(getNodeInfo, "UTF-8"));
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
                String getNodeParent = "WITH <http://mu.semte.ch/application> SELECT ?parent WHERE { ";
                getNodeParent += "<" + node + "> <http://mu.semte.ch/vocabularies/core/hasParent> ?parent .\n}";

                String jsonStringP = this.queryService.sparqlService.getSPARQLResponse("http://localhost:8890/sparql?query=" + URLEncoder.encode(getNodeParent, "UTF-8"));
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


//    @RequestMapping(value = "/wtf")
//    public ResponseEntity<String> preTESTWTF(HttpServletRequest request, HttpServletResponse response, @RequestBody(required = false) String body) throws InvalidSPARQLException {
//        this.buildScopeForUser("JONATHANUUID");
//        this.getGraphName("JONATHANUUID");
//
//
//        Scope scope = new Scope("test");
//        ScopeNode scopeNode1 = new ScopeNode();
//        scopeNode1.setUUID("1");
//        scopeNode1.setScopeNodeType(1);
//        ScopeNode scopeNode2 = new ScopeNode();
//        scopeNode2.setUUID("2");
//        scopeNode2.setParent(scopeNode1);
//        scopeNode2.setScopeNodeType(4);
//        ScopeNode scopeNode3 = new ScopeNode();
//        scopeNode3.setUUID("3");
//        scopeNode3.setScopeNodeType(4);
//        scope.getNodes().add(scopeNode1);
//        scope.getNodes().add(scopeNode2);
//        scope.getNodes().add(scopeNode3);
//
//        return new ResponseEntity<String>(scope.calculateGraphToQuery(), HttpStatus.OK);
//    }



}
