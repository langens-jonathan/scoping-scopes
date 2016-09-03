package scopes_service.query_pre_processor.Scopes;

import org.openrdf.model.vocabulary.SP;
import scopes_service.query_pre_processor.query.SPARQLService;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Created by langens-jonathan on 30.08.16.
 */
public class ScopeNode {
    private String name;
    private String UUID = java.util.UUID.randomUUID().toString();
    private String query = "select * from { ?s ?p ?o .}";
    private ScopeNode parent;
    private int scopeNodeType;
    private List<ScopeNode> children = new ArrayList<ScopeNode>();

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public ScopeNode getParent() {
        return parent;
    }

    public void setParent(ScopeNode parent) {
        this.parent = parent;
        parent.addChild(this);
    }

    public int getScopeNodeType() {
        return scopeNodeType;
    }

    public void setScopeNodeType(int scopeNodeType) {
        this.scopeNodeType = scopeNodeType;
    }

    public void addChild(ScopeNode child)
    {
        this.children.add(child);
    }

    public void setUUID(String uuid)
    {
        this.UUID = uuid;
    }

    public String getUUID()
    {
        return this.UUID;
    }

    private String getInsertName(String n)
    {
        return n + "";
    }

    private String getDeleteName(String n)
    {
        return n + "/delete";
    }

    public String calculateScopes()
    {
        // calculate the name of the instance graph
        String instanceGraph = this.name + "/instance";

        // clear the instance graph
        String clearQuery = "WITH <" + instanceGraph + ">\nDELETE\n{\n ?s ?p ?o .\n}\nWHERE\n{\n  ?s ?p ?o .\n}";
        try {
            SPARQLService.getInstance().postSPARQLResponse(SPARQLService.getLocalURL(), clearQuery);
        } catch (IOException e) {
            e.printStackTrace();
        }

        // adding in everything from my children
        for(ScopeNode child : this.children)
        {
            String childgraphname = child.calculateScopes();
            String pullInChildGraphQuery = "INSERT\n{\n  GRAPH <" + instanceGraph + ">\n  {\n    ?s ?p ?o .\n  }\n}";
            pullInChildGraphQuery += "WHERE\n{\n  GRAPH <" + childgraphname + ">\n  {\n      ?s ?p ?o.\n  }\n}";
            try {
                SPARQLService.getInstance().postSPARQLResponse(SPARQLService.getLocalURL(), pullInChildGraphQuery);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        // adding my own inserts
        String pullInInsertsQuery = "INSERT\n{\n  GRAPH <" + instanceGraph + ">\n  {\n    ?s ?p ?o .\n  }\n}";
        pullInInsertsQuery += "WHERE\n{\n  GRAPH <" + getInsertName(this.getUUID()) + ">\n  {\n      ?s ?p ?o.\n  }\n}";
        try {
            SPARQLService.getInstance().postSPARQLResponse(SPARQLService.getLocalURL(), pullInInsertsQuery);
        } catch (IOException e) {
            e.printStackTrace();
        }

        // remove deletes
        String pullInDeletesQuery = "DELETE\n{\n  GRAPH <" + instanceGraph + ">\n  {\n    ?s ?p ?o .\n  }\n}";
        pullInDeletesQuery += "WHERE\n{\n  GRAPH <" + getDeleteName(this.getUUID()) + ">\n  {\n      ?s ?p ?o.\n  }\n}";
        try {
            SPARQLService.getInstance().postSPARQLResponse(SPARQLService.getLocalURL(), pullInDeletesQuery);
        } catch (IOException e) {
            e.printStackTrace();
        }

        // then return my own instance name
        return instanceGraph;
    }
}
