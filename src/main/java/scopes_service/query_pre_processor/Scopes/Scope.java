package scopes_service.query_pre_processor.Scopes;

import scopes_service.query_pre_processor.query.SPARQLService;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Created by langens-jonathan on 30.08.16.
 */
public class Scope {
    private List<ScopeNode> nodes = new ArrayList<ScopeNode>();
    private String name;

    public Scope(String name)
    {
        this.name = name;
    }

    public String getName()
    {
        return this.name;
    }

    public void setName(String name)
    {
        this.name = name;
    }

    public List<ScopeNode> getNodes() {
        return nodes;
    }

    public void setNodes(List<ScopeNode> nodes) {
        this.nodes = nodes;
    }

    public String calculateGraphToQuery()
    {
        String instanceGraph = this.name + "/instance";

        // clear the instance graph
        String clearQuery = "WITH <" + instanceGraph + ">\nDELETE\n{\n ?s ?p ?o .\n}\nWHERE\n{\n  ?s ?p ?o .\n}";
        try {
            SPARQLService.getInstance().postSPARQLResponse(SPARQLService.getLocalURL(), clearQuery);
        } catch (IOException e) {
            e.printStackTrace();
        }

        List<ScopeNode> parentNodes = new ArrayList<ScopeNode>();

        for(ScopeNode child : this.nodes)
        {
            if(child.getParent() == null)
                parentNodes.add(child);
        }

        for(int level = 4; level > 0; --level) {
            List<String> names = new ArrayList<String>();

            // then get all level 4
            for (ScopeNode levelnodes : this.nodes) {
                if (levelnodes.getScopeNodeType() == level && levelnodes.getParent() == null) {
                    String instanceName = levelnodes.calculateScopes();
                    instanceName = instanceName.substring(0, instanceName.length() - 9);
                    names.add(instanceName);
                }
            }

            // then add in all levelnode's
            for (String name : names) {
                // adding my own inserts
                String pullInInsertsQuery = "INSERT\n{\n  GRAPH <" + instanceGraph + ">\n  {\n    ?s ?p ?o .\n  }\n}";
                pullInInsertsQuery += "WHERE\n{\n  GRAPH <" + name + "/instance>\n  {\n      ?s ?p ?o.\n  }\n}";
                try {
                    SPARQLService.getInstance().postSPARQLResponse(SPARQLService.getLocalURL(), pullInInsertsQuery);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            // then add in all levelnode's
            for (String name : names) {
                // remove deletes
                String pullInDeletesQuery = "DELETE\n{\n  GRAPH <" + instanceGraph + ">\n  {\n    ?s ?p ?o .\n  }\n}";
                pullInDeletesQuery += "WHERE\n{\n  GRAPH <" + name + "/delete>\n  {\n      ?s ?p ?o.\n  }\n}";
                try {
                    SPARQLService.getInstance().postSPARQLResponse(SPARQLService.getLocalURL(), pullInDeletesQuery);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        String clearMetaInfoQuery = "WITH <http://mu.semte.ch/instances>\n DELETE\n{\n";
        clearMetaInfoQuery += "?user <http://mu.semte.ch/vocabularies/graphs/hasInstanceGraph> ?uuid .\n";
        clearMetaInfoQuery += "?uuid <http://mu.semte.ch/vocabularies/graphs/hasGraph> ?gname .\n}";
        clearMetaInfoQuery += "WHERE\n{\n?user <http://mu.semte.ch/vocabularies/core/uuid> \"" + this.name + "\" .\n}";
        try {
            SPARQLService.getInstance().postSPARQLResponse(SPARQLService.getLocalURL(), clearMetaInfoQuery);
        } catch (IOException e) {
            e.printStackTrace();
        }

        String UUID = java.util.UUID.randomUUID().toString();
        String setMetaInfoQuery = "WITH <http://mu.semte.ch/instances>\n INSERT\n{\n";
        setMetaInfoQuery += "?user <http://mu.semte.ch/vocabularies/graphs/hasInstanceGraph> <http://mu.semte.ch/vocabularies/graphs/InstanceGraph/" + UUID + "> .\n";
        setMetaInfoQuery += "<http://mu.semte.ch/vocabularies/graphs/InstanceGraph/" + UUID + "> <http://mu.semte.ch/vocabularies/graphs/hasGraph> \"" + instanceGraph + "\".\n}";
        setMetaInfoQuery += "WHERE\n{\n?user <http://mu.semte.ch/vocabularies/core/uuid> \"" + this.name + "\" .\n}";
        try {
            SPARQLService.getInstance().postSPARQLResponse(SPARQLService.getLocalURL(), setMetaInfoQuery);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return instanceGraph;
    }
}
