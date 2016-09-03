package scopes_service.query_pre_processor.query;

import com.tenforce.semtech.SPARQLParser.SPARQL.InvalidSPARQLException;
import com.tenforce.semtech.SPARQLParser.SPARQL.SPARQLQuery;
import com.tenforce.semtech.SPARQLParser.SPARQLStatements.BlockStatement;
import com.tenforce.semtech.SPARQLParser.SPARQLStatements.IStatement;
import com.tenforce.semtech.SPARQLParser.SPARQLStatements.UpdateBlockStatement;
import org.openrdf.query.BindingSet;
import org.openrdf.query.TupleQueryResult;
import org.springframework.stereotype.Service;
import java.net.URLEncoder;
import java.util.*;

/**
 * Created by langens-jonathan on 31.05.16.
 */
@Service
public class QueryService
{
    public SPARQLService sparqlService;
    public QueryService() {
        this.sparqlService = new SPARQLService();
    }
    public QueryService(SPARQLService service){this.sparqlService = service;}

    /**
     * returns a construct that describes the triples that will EFFECTIVELY be inserted
     * or deleted by running a certain query on the triple store.
     *
     * the triple store on which the query will be exectued is assumed to be the
     * default store for the sparql service.
     *
     * @param parsedQuery
     * @return
     */

    public DifferenceTriples getDifferenceTriples(SPARQLQuery parsedQuery) throws InvalidSPARQLException
    {
        DifferenceTriples differenceTriples = new DifferenceTriples();

        String queryPrefix = "";
        for(String key : parsedQuery.getPrefixes().keySet())
        {
            queryPrefix += "PREFIX " + key + ": <" + parsedQuery.getPrefixes().get(key) + ">\n";
        }

        List<Triple> deleteTriples = new ArrayList<Triple>();
        List<Triple> insertTriples = new ArrayList<Triple>();

        SPARQLQuery clonedQuery = parsedQuery.clone();
        parsedQuery.replaceGraphStatements("");
        clonedQuery.replaceGraphStatements("");

        for(IStatement statement : parsedQuery.getStatements()) {
            if (statement.getType().equals(IStatement.StatementType.UPDATEBLOCK)) {
                UpdateBlockStatement updateBlockStatement = (UpdateBlockStatement)statement;

                String graph = this.sparqlService.getDefaultGraph();
                if(!parsedQuery.getGraph().isEmpty())
                {
                    graph = parsedQuery.getGraph();
                }
                if(!updateBlockStatement.getGraph().isEmpty())
                {
                    graph = updateBlockStatement.getGraph();
                }

                String extractQuery = queryPrefix + "WITH <" + graph + ">\n";
                extractQuery += "CONSTRUCT\n{\n";

                for(IStatement innerStatement : updateBlockStatement.getStatements())
                {
                    extractQuery += innerStatement.toString() + "\n";
                }

                extractQuery += "}\nWHERE\n{\n";

                if(updateBlockStatement.getWhereBlock() != null) {
                    for (IStatement whereStatement : updateBlockStatement.getWhereBlock().getStatements()) {
                        extractQuery += whereStatement.toString() + "\n";
                    }
                }

                extractQuery += "}";

                TupleQueryResult result = this.sparqlService.selectQuery(extractQuery);

                while (result.hasNext()) {
                    Triple triple = new Triple();
                    BindingSet bs = result.next();
                    Iterator<org.openrdf.query.Binding> b = bs.iterator();
                    while (b.hasNext()) {
                        org.openrdf.query.Binding bind = b.next();
                        if (bind.getName().equals("P"))
                            triple.setPredicate(bind.getValue().stringValue());
                        if (bind.getName().equals("S"))
                            triple.setSubject(bind.getValue().stringValue());
                        if (bind.getName().equals("O"))
                            triple.setObject(bind.toString().substring(2, bind.toString().length()));
                    }
                    if (updateBlockStatement.getUpdateType().equals(BlockStatement.BLOCKTYPE.INSERT)) {
                        insertTriples.add(triple);
                    }
                    else
                    {
                        deleteTriples.add(triple);
                    }
                }
            }
        }

        // now insert the delete triples in a temporary graph
        String deleteGraph = "<http://tmp-delete-graph>";

        // first clear the graph
        this.sparqlService.deleteQuery("with " + deleteGraph + " delete {?s ?p ?o} where {?s ?p ?o.}");

        String tmpDeleteInsert = queryPrefix + "\n with " + deleteGraph + "\ninsert data\n{\n";
        for(Triple t : deleteTriples)
//            tmpDeleteInsert += "<" + t.getSubject() + "> <" + t.getPredicate() + "> " + t.getObjectAsString() + " .\n";
            if(t.getObjectType().endsWith(".org/2001/XMLSchema#string>"))
                tmpDeleteInsert += "<" + t.getSubject().substring(1, t.getSubject().length()) + "> <" + t.getPredicate() + "> " + t.getObjectAsString() + " .\n";
            else
                tmpDeleteInsert += "<" + t.getSubject().substring(1, t.getSubject().length()) + "> <" + t.getPredicate() + "> " + t.getObjectAsString() + " .\n";

        tmpDeleteInsert += "}";

        // then we store it
        this.sparqlService.insertQuery(tmpDeleteInsert);

        // then we insert the insert triples in a temporary graph
        String insertGraph = "<http://tmp-insert-graph>";

        // again first clear it
        this.sparqlService.deleteQuery("with " + insertGraph + " delete {?s ?p ?o} where {?s ?p ?o.}");

        String tmpInsertInsert = queryPrefix + "\n with " + insertGraph + "\ninsert data\n{\n";
        for(Triple t : insertTriples)
//                tmpInsertInsert += "<" + t.getSubject() + "> <" + t.getPredicate() + "> " + t.getObjectAsString() + " .\n";
            if(t.getObjectType().endsWith("w3.org/2001/XMLSchema#string>"))
                tmpInsertInsert += "<" + t.getSubject().substring(1, t.getSubject().length()) + "> <" + t.getPredicate() + "> " + t.getObjectAsString() + " .\n";
            else
                tmpInsertInsert += "<" + t.getSubject().substring(1, t.getSubject().length()) + "> <" + t.getPredicate() + "> " + t.getObjectAsString() + " .\n";

        tmpInsertInsert += "}";

        // then we store it
        this.sparqlService.insertQuery(tmpInsertInsert);

        differenceTriples.setAllDeleteTriples(new HashSet<Triple>(deleteTriples));
        differenceTriples.setAllInsertTriples(new HashSet<Triple>(insertTriples));


        // then we check what would be deleted by doing an intersection between the
        // real graph and our tmp delete graph
        String newQuery = "SELECT ?s ?p ?o WHERE { GRAPH <" + this.sparqlService.getDefaultGraph() + "> { ?s ?p ?o . } .\n GRAPH " + deleteGraph + " { ?s ?p ?o . } .\n}";

        List<Triple> confirmedDeletes = new ArrayList<Triple>();

        try
        {
            String url = "http://localhost:8890/sparql?query=" + URLEncoder.encode(newQuery, "UTF-8");
            confirmedDeletes = this.sparqlService.getTriplesViaGet(url);
        } catch (Exception e)
        {
            e.printStackTrace();
        }

        differenceTriples.setEffectiveDeleteTriples(new HashSet<Triple>(confirmedDeletes));


        // same thing for inserts
        List<Triple> confirmedInserts = new ArrayList<Triple>();
        newQuery = "SELECT ?s ?p ?o WHERE {graph " + insertGraph + " {?s ?p ?o.}.\nminus\n{\ngraph <" + this.sparqlService.getDefaultGraph() + "> {?s ?p ?o.}.}\n}";
        try
        {
            String url = "http://localhost:8890/sparql?query=" + URLEncoder.encode(newQuery, "UTF-8");
            confirmedInserts = this.sparqlService.getTriplesViaGet(url);
        } catch (Exception e)
        {
            e.printStackTrace();
        }

        differenceTriples.setEffectiveInsertTriples(new HashSet<Triple>(confirmedInserts));

        return differenceTriples;

    }
/*
    public DifferenceTriples getDifferenceTriples(String query) throws InvalidSPARQLException
    {
        SPARQLQuery pq = new SPARQLQuery(query);

        DifferenceTriples differenceTriples = new DifferenceTriples();

        if(!pq.getType().name().equals("UPDATE"))
        {
            return differenceTriples;
        }

        // extract all prefixes
        Map<String, String> prefixes = getPrefixes(query);

        // extract all insert blocks
        List<String> insertBlocks = getTypedBlocks(query, "INSERT");

        // all the delete blocks
        List<String> deleteBlocks = getTypedBlocks(query, "DELETE");

        // then all the where blocks
        List<String> whereBlocks = getTypedBlocks(query, "WHERE");

        // get a list of unknowns
        Set<String> unkowns = getUnknowns(insertBlocks);

        // add the unknowns from the delete blocks
        unkowns.addAll(getUnknowns(deleteBlocks));

        // build the prefix for the queries
        String queryPrefix = "";
        for(String k : prefixes.keySet())
        {
            queryPrefix += "PREFIX " + k + ": <" + prefixes.get(k) + ">\n";
        }

        String whereBlock = "";

        for(String block : whereBlocks)
        {
            if(!block.endsWith("."))block += ".";
            whereBlock += "\n" + block;
        }

        List<Triple> deleteTriples = new ArrayList<Triple>();
        for(String block : deleteBlocks)
        {
            String q = queryPrefix + "\nWITH <" + this.sparqlService.getDefaultGraph() + ">\nCONSTRUCT\n{\n";
            q += block;
            q += "\n}\nWHERE\n{\n" + whereBlock + "\n}";

            TupleQueryResult result = this.sparqlService.selectQuery(q);

            while (result.hasNext()) {
                Triple triple = new Triple();
                BindingSet bs = result.next();
                Iterator<org.openrdf.query.Binding> b = bs.iterator();
                while (b.hasNext()) {
                    org.openrdf.query.Binding bind = b.next();
                    if(bind.getName().equals("P"))
                        triple.setPredicate(bind.getValue().stringValue());
                    if(bind.getName().equals("S"))
                        triple.setSubject(bind.getValue().stringValue());
                    if(bind.getName().equals("O"))
                        triple.setObject(bind.getValue());
                }
                deleteTriples.add(triple);
            }
        }

        List<Triple> insertTriples = new ArrayList<Triple>();
        for(String block : insertBlocks)
        {
            String q = queryPrefix + "\nWITH <" + this.sparqlService.getDefaultGraph() + ">\nCONSTRUCT\n{\n";
            q += block;
            q += "\n}\nWHERE\n{\n" + whereBlock + "\n}";

            TupleQueryResult result = this.sparqlService.selectQuery(q);

            while (result.hasNext()) {
                Triple triple = new Triple();
                BindingSet bs = result.next();
                Iterator<org.openrdf.query.Binding> b = bs.iterator();
                while (b.hasNext()) {
                    org.openrdf.query.Binding bind = b.next();
                    if(bind.getName().equals("P"))
                        triple.setPredicate(bind.getValue().stringValue());
                    if(bind.getName().equals("S"))
                        triple.setSubject(bind.getValue().stringValue());
                    if(bind.getName().equals("O"))
                        triple.setObject(bind.getValue());
                }
                insertTriples.add(triple);
            }
        }

        // now insert the delete triples in a temporary graph
        String deleteGraph = "<http://tmp-delete-graph>";

        // first clear the graph
        this.sparqlService.deleteQuery("with " + deleteGraph + " delete {?s ?p ?o} where {?s ?p ?o.}");

        String tmpDeleteInsert = queryPrefix + "\n with " + deleteGraph + "\ninsert data\n{\n";
        for(Triple t : deleteTriples)
            if(t.getObject().toString().endsWith("^^<http://www.w3.org/2001/XMLSchema#string>"))
                tmpDeleteInsert += "<" + t.getSubject().substring(1, t.getSubject().length()) + "> <" + t.getPredicate() + "> \"" + t.getObject().stringValue() + "\" .\n";
            else
                tmpDeleteInsert += "<" + t.getSubject().substring(1, t.getSubject().length()) + "> <" + t.getPredicate() + "> " + t.getObject().toString() + " .\n";

        tmpDeleteInsert += "}";

        // then we store it
        this.sparqlService.insertQuery(tmpDeleteInsert);

        // then we insert the insert triples in a temporary graph
        String insertGraph = "<http://tmp-insert-graph>";

        // again first clear it
        this.sparqlService.deleteQuery("with " + insertGraph + " delete {?s ?p ?o} where {?s ?p ?o.}");

        String tmpInsertInsert = queryPrefix + "\n with " + insertGraph + "\ninsert data\n{\n";
        for(Triple t : insertTriples)
            if(t.getObject().toString().endsWith("^^<http://www.w3.org/2001/XMLSchema#string>"))
                tmpInsertInsert += "<" + t.getSubject().substring(1, t.getSubject().length()) + "> <" + t.getPredicate() + "> \"" + t.getObject().stringValue() + "\" .\n";
            else
                tmpInsertInsert += "<" + t.getSubject().substring(1, t.getSubject().length()) + "> <" + t.getPredicate() + "> " + t.getObject().toString() + " .\n";

        tmpInsertInsert += "}";

        // then we store it
        this.sparqlService.insertQuery(tmpInsertInsert);

        differenceTriples.setAllDeleteTriples(new HashSet<Triple>(deleteTriples));
        differenceTriples.setAllInsertTriples(new HashSet<Triple>(insertTriples));


        // then we check what would be deleted by doing an intersection between the
        // real graph and our tmp delete graph
        String newQuery = "SELECT ?s ?p ?o WHERE { GRAPH <" + this.sparqlService.getDefaultGraph() + "> { ?s ?p ?o . } .\n GRAPH " + deleteGraph + " { ?s ?p ?o . } .\n}";

        List<Triple> confirmedDeletes = new ArrayList<Triple>();

        try
        {
            String url = "http://localhost:8890/sparql?query=" + URLEncoder.encode(newQuery, "UTF-8");
            confirmedDeletes = this.sparqlService.getTriplesViaGet(url);
        } catch (Exception e)
        {
            e.printStackTrace();
        }

        differenceTriples.setEffectiveDeleteTriples(new HashSet<Triple>(confirmedDeletes));


        // same thing for inserts
        List<Triple> confirmedInserts = new ArrayList<Triple>();
        newQuery = "SELECT ?s ?p ?o WHERE {graph " + insertGraph + " {?s ?p ?o.}.\nminus\n{\ngraph <" + this.sparqlService.getDefaultGraph() + "> {?s ?p ?o.}.}\n}";
        try
        {
            String url = "http://localhost:8890/sparql?query=" + URLEncoder.encode(newQuery, "UTF-8");
            confirmedInserts = this.sparqlService.getTriplesViaGet(url);
        } catch (Exception e)
        {
            e.printStackTrace();
        }

        differenceTriples.setEffectiveInsertTriples(new HashSet<Triple>(confirmedInserts));

        return differenceTriples;
    }*/

    /**
     * returns a set with all unknowns in the query blocks
     * @param blocks
     * @return a set with all unknowns
     */
    public Set<String> getUnknowns(List<String> blocks)
    {
        Set<String> unknowns = new HashSet<String>();

        for(String block : blocks)
        {
            for(String s : block.split(" "))
            {
                s = s.trim();
                if(s.startsWith("?"))
                {
                    unknowns.add(s);
                }
            }
        }

        return unknowns;
    }

    /**
     * Splits a query in to an array of elements with instead of 1 split character all
     * whitespace characters
     * @param query
     * @return an array of strings that are split based on whitespace characters
     */
    public String [] splitQuery(String query)
    {
        Vector<String> splitQuery = new Vector<String>();

        String [] myArr;

        String currentBuffer = "";

        for(byte b : query.getBytes())
        {
            if(((char) b)==' ' || ((char) b) == '\n' || ((char) b) == '\t' || ((char) b) == '\r')
            {
                currentBuffer = currentBuffer.trim();
                if(currentBuffer.length() > 0)splitQuery.add(currentBuffer);
                currentBuffer = "";
                continue;
            }
            currentBuffer += ((char) b);
        }

        String [] arr = new String[splitQuery.size()];

        for(int i = 0; i < splitQuery.size(); ++i)
        {
            arr[i] = splitQuery.elementAt(i);
        }

        return arr;
    }

    /**
     * extracts the prefixes from a query and returns them as a hash
     * @param query
     * @return an map of prefixes and replacements
     */
    public Map<String, String> getPrefixes(String query)
    {
        Map<String, String> prefixes = new HashMap<String, String>();

        boolean inPrefix = false;
        String currentPrefix = "";

        for(String s: splitQuery(query))//query.split(" "))
        {
            s = s.trim();
            if(s.length()==0)
                continue;

            if(s.toLowerCase().equals("prefix"))
            {
                inPrefix = true;
                currentPrefix = "";
                continue;
            }

            if(s.toLowerCase().equals(":"))continue;

            if(inPrefix)
            {
                if(currentPrefix=="")
                {
                    if(s.endsWith(":"))
                        s = s.substring(0, s.length()-1);
                    currentPrefix=s;
                    continue;
                }
                else
                {
                    s = s.substring(1, s.length()-1);
                    prefixes.put(currentPrefix, s);
                    currentPrefix = "";
                    inPrefix = false;
                    continue;
                }
            }
        }

        return prefixes;
    }

    /**
     * returns the first index of the searchString in the baseString or -1 if it was not found
     * @param baseString
     * @param searchString
     * @return the index of the searchString
     */
    public int indexOfInsertCase(String baseString, String searchString)
    {
        return baseString.toLowerCase().indexOf(searchString.toLowerCase());
    }


    /**
     * Returns a list of 'block' contents for blocks that followed
     * the given block type.
     *
     * @param query the query from which the blocks need to be extracted
     * @param type the key word that will proceed the { block contents }
     * @return a list with 1 string per block
     */
    public List<String> getTypedBlocks(String query, String type)
    {
        List<String> blocks = new ArrayList<String>();

        int nextOccurance = indexOfInsertCase(query, type);

        String subQuery = query.substring(0, query.length());

        while(nextOccurance > -1)
        {
            // restrict the subquery to the first occurrence of the start block
            subQuery = subQuery.substring(nextOccurance, subQuery.length());

            // first find the '{' that follows the next occurance
            int startOfBlock = subQuery.indexOf('{') + 1;

            // next find the first '}' of that block
            int endOfBlock = subQuery.indexOf('}');

            // add the block to the list
            blocks.add(subQuery.substring(startOfBlock, endOfBlock));

            // strip away the current block
            subQuery = subQuery.substring(endOfBlock);

            // update the next occurance
            nextOccurance = indexOfInsertCase(subQuery, type);
        }

        return blocks;
    }
}
