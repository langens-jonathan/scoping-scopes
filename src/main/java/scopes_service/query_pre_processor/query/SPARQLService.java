package scopes_service.query_pre_processor.query;


import com.fasterxml.jackson.databind.ObjectMapper;
import org.openrdf.query.TupleQuery;
import org.openrdf.query.TupleQueryResult;
import org.openrdf.query.Update;
import org.openrdf.repository.Repository;
import org.openrdf.repository.RepositoryConnection;
import org.openrdf.repository.manager.SystemRepository;
import virtuoso.sesame4.driver.VirtuosoRepository;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Map;
import java.util.List;

/**
 * Created by jonathan-langens on 3/4/16.
 */
public class SPARQLService
{
    /**
     * Data base connection constants
     *
     *These are private but they can be overridden by the child class by using the special constructor
     */
    private String url = "jdbc:virtuoso://localhost:1111";

    private String user = "dba";

    private String pwd = "dba";

    private String defgraph = "http://mu.semte.ch/application";

    /**
     * Other class member variables
     */
    private Repository repository;

    private RepositoryConnection connection;

    private boolean initialized = false;


    /**
     * Special Constructor that allows the child class to set the database constants needed to
     * query the RDF store.
     *
     * @param url the url where the RDF store resides
     * @param user the username needed to connect
     * @param pwd the password for that user
     * @param defgraph the default graph
     *
     * @result the connection is set up for this object and the query functionss will work as expected.
     */
    protected SPARQLService(String url, String user, String pwd, String defgraph)
    {
        this.url = url;
        this.user = user;
        this.pwd = pwd;
        this.defgraph = defgraph;

        initializeConnection();
    }

    public String getDefaultGraph()
    {
        return this.defgraph;
    }

    /**
     * The default constructor will initialize the connection with the default settings
     *
     * @result the connection is set up for this object and the query functions will work as expected
     */
    protected SPARQLService()
    {
        initializeConnection();
    }

    /**
     * Private method
     *
     * Goes through the trouble of setting up the connection.
     */
    private void initializeConnection()
    {
        System.out.println("loading jdbc driver");
        try
        {
            Class.forName("virtuoso.jdbc4.Driver");
        }
        catch (ClassNotFoundException e)
        {
            e.printStackTrace();
        }

        System.out.println("loading repository connection");
        repository = new VirtuosoRepository(url, user, pwd);//new VirtuosoRepository(url, user, pwd, defgraph);

        try
        {
            repository.initialize();
            connection = repository.getConnection();
            this.initialized = true;
        }catch(Exception e)
        {
            e.printStackTrace();
        }

        System.out.println("connection was successful");
    }

    /**
     * Returns true if the initialization process was succesful
     *
     * @return true if the connection was setup correctly
     */
    protected boolean isInitialized()
    {
        return this.initialized;
    }

    /**
     * This function expects and executes a select query on the initialized connection.
     *
     * @param query a string representation of a SPARQL select query
     * @return a tuple query result containing the result of your query
     */
    protected TupleQueryResult selectQuery(String query)
    {
        try {
            TupleQuery tupleQuery = connection.prepareTupleQuery(org.openrdf.query.QueryLanguage.SPARQL, query);
            return tupleQuery.evaluate();
        } catch(Exception e)
        {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Expects an insert query and executes it.
     *
     * @param query the insert query to be executed.
     */
    protected void insertQuery(String query)
    {
        try{
            Update update = connection.prepareUpdate(org.openrdf.query.QueryLanguage.SPARQL, query);
            update.execute();
            connection.commit();
        } catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    /**
     * Expects a delete query and executes it
     *
     * @param query the string representation of a SPARQL delete query
     */
    protected void deleteQuery(String query)
    {
        this.insertQuery(query);
    }

    /**
     * this sends a GET request to the given URL and produces a list of triple objects
     * that match the query.
     *
     * the url is supposed to be formatted as follows
     *   http://localhost:8890/sparql?query=URL_ENCODED_SELECT_QUERY
     *
     * TODO this method is still quite ugly, the main reason for implementing this is that
     * TODO the sesame library does not allow you to post a query with 2 graphs
     *
     * TODO this method is also not generic and will only work for the specific use case for which it was implemented
     *
     * @param url a fully url-endpoint with query url that you would use to do a GET with postman
     * @return a list of triples that were returned by the SPARQL endpoint
     * @throws MalformedURLException if the URL cannot be passed to the constructor of a java.util.URL object
     * @throws IOException if the connection to the SPARQL enpoint cannot be opened
     */
    @SuppressWarnings("unchecked")
    public List<Triple> getTriplesViaGet(String url) throws MalformedURLException, IOException
    {
        if(url ==  null)
            url = SPARQLService.getLocalURL();


        URL u = new URL(url);
        HttpURLConnection connection = (HttpURLConnection) u.openConnection();

        // just want to do an HTTP GET here
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Accept", "application/json");

        // uncomment this if you want to write output to this url
        //connection.setDoOutput(true);

        // give it 15 seconds to respond
        connection.setReadTimeout(15*1000);
        connection.connect();

        BufferedReader reader = null;
        StringBuilder stringBuilder;

        // read the output from the server
        reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
        stringBuilder = new StringBuilder();

        String line = null;
        while ((line = reader.readLine()) != null)
        {
            stringBuilder.append(line + "\n");
        }
        String jsonString = stringBuilder.toString();

        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> jsonMap = mapper.readValue(jsonString, Map.class);


        List l =((List<Map<String,Object>>)((Map) jsonMap.get("results")).get("bindings"));
        List<Triple> triples = new ArrayList<Triple>();

        for(Object tripleMap : l)
        {
            Map<String, Object> cTripleMap = (Map<String, Object>) tripleMap;
            
            Map<String, Object> sMap = (Map<String, Object>) cTripleMap.get("s");
            String sValue = (String) sMap.get("value");
            
            Map<String, Object> pMap = (Map<String, Object>) cTripleMap.get("p");
            String pValue = (String) pMap.get("value");

            Map<String, Object> oMap = (Map<String, Object>) cTripleMap.get("o");
            String oValue = (String) oMap.get("value");
            String oType = (String) oMap.get("datatype");
            

            Triple t = new Triple();

            // first extract the subject
            t.setSubject(sValue);

            // then the predicate
            t.setPredicate(pValue);

            // and finally the object
            t.setObjectString(oValue);
            t.setObjectType(oType);

            // add it to the triples
            triples.add(t);
        }

        return triples;
    }

    @SuppressWarnings("unchecked")
    public String getSPARQLResponse(String url) throws MalformedURLException, IOException
    {
        if(url ==  null)
            url = SPARQLService.getLocalURL();


        URL u = new URL(url);
        HttpURLConnection connection = (HttpURLConnection) u.openConnection();

        // just want to do an HTTP GET here
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Accept", "application/json");

        // give it 15 seconds to respond
        connection.setReadTimeout(15*1000);
        connection.connect();

        BufferedReader reader = null;
        StringBuilder stringBuilder;

        // read the output from the server
        reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
        stringBuilder = new StringBuilder();

        String line = null;
        while ((line = reader.readLine()) != null)
        {
            stringBuilder.append(line + "\n");
        }
        return stringBuilder.toString();
    }

    @SuppressWarnings("unchecked")
    public String postSPARQLResponse(String url, String query) throws MalformedURLException, IOException
    {
        if(url ==  null)
            url = SPARQLService.getLocalURL();

        URL obj = new URL(url);

        HttpURLConnection con = (HttpURLConnection) obj.openConnection();

        //add reuqest header
        con.setRequestMethod("POST");
        con.setRequestProperty("Accept", "application/json");

        // Send post request
        con.setDoOutput(true);
        DataOutputStream wr = new DataOutputStream(con.getOutputStream());
        wr.writeBytes("query=" + URLEncoder.encode(query, "UTF-8"));
        wr.flush();
        wr.close();

        int responseCode = con.getResponseCode();

        BufferedReader in = new BufferedReader(
                new InputStreamReader(con.getInputStream()));
        String inputLine;
        StringBuffer response = new StringBuffer();

        while ((inputLine = in.readLine()) != null) {
            response.append(inputLine);
        }
        in.close();

        //print result
        System.out.println(response.toString());

        return (response.toString());
    }
    private static SPARQLService instance = null;
    public static SPARQLService getInstance()
    {
        if(SPARQLService.instance == null)
        {
            SPARQLService.instance = new SPARQLService();
        }
        return SPARQLService.instance;
    }

    public static String getLocalURL()
    {
        if(System.getenv("SPARQLENDPOINT") != null && !System.getenv("SPARQLENDPOINT").isEmpty())
            return System.getenv("SPARQLENDPOINT");
        else
            return "http://localhost/sparql";
    }
}