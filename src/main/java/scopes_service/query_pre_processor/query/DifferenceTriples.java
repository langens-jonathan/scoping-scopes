package scopes_service.query_pre_processor.query;

import java.util.HashSet;
import java.util.Set;

/**
 * Created by langens-jonathan on 31.05.16.
 *
 * This is a data structure
 *
 * The class difference triples holds a set of all triples that will
 * be updated, one set for all triples that will be deleted on a
 * certain data set, on set for all triples that will effectivly be
 * updated and one set for all triples that will effectivly be deleted.
 */
public class DifferenceTriples
{
    // a set with all triples that will be updated in the store
    private Set<Triple> allInsertTriples;

    // a set with all triples that will be deleted in the store
    private Set<Triple> allDeleteTriples;

    // a set with allt triples that will EFFECTIVLY be inserted in the graph
    private Set<Triple> effectiveInsertTriples;

    // a set will all triples that will EFFECTIVLY be deleted from the graph
    private Set<Triple> effectiveDeleteTriples;

    /**
     * default constructor
     */
    public DifferenceTriples()
    {
        this.allInsertTriples = new HashSet<Triple>();
        this.allDeleteTriples = new HashSet<Triple>();
        this.effectiveInsertTriples = new HashSet<Triple>();
        this.effectiveDeleteTriples = new HashSet<Triple>();
    }

    /**
     * adds the given update triple to the set of update triples
     * @param triple
     */
    public void addAllInsertTriple(Triple triple)
    {
        this.allInsertTriples.add(triple);
    }

    /**
     * adds the given delete triple to the set of delete triples
     * @param triple
     */
    public void addAllDeleteTripel(Triple triple)
    {
        this.allDeleteTriples.add(triple);
    }

    /**
     * adds the given update triple to the set of update triples
     * @param triple
     */
    public void addEffectiveInsertTriple(Triple triple)
    {
        this.effectiveInsertTriples.add(triple);
    }

    /**
     * adds the given delete triple to the set of delete triples
     * @param triple
     */
    public void addEffectiveDeleteTripel(Triple triple)
    {
        this.effectiveDeleteTriples.add(triple);
    }

    public String getAllChangesAsJSON()
    {
        String jsonString = "";

        jsonString += "{\n\"inserts\":\n[\n";

        for(Triple t : this.getAllInsertTriples())
        {
            jsonString += "{\"s\":\"" + t.getSubject() + "\",\"p\":\"";
            jsonString += t.getPredicate() + "\",\"o\":\"" + t.getObjectString() + "\"},";
        }

        if(!this.getAllInsertTriples().isEmpty())
        {
            jsonString = jsonString.substring(0, jsonString.length() - 1);
        }

        jsonString += "\n]\n\"deletes\":\n[\n";

        for(Triple t : this.getAllDeleteTriples())
        {
            jsonString += "{\"s\":\"" + t.getSubject() + "\",\"p\":\"";
            jsonString += t.getPredicate() + "\",\"o\":\"" + t.getObjectString() + "\"},";
        }

        if(!this.getAllDeleteTriples().isEmpty())
        {
            jsonString = jsonString.substring(0, jsonString.length() - 1);
        }

        jsonString += "\n]\n}";

        return jsonString;
    }

    public String getEffectiveChangesAsJSON()
    {
        String jsonString = "";

        jsonString += "{\n\"inserts\":\n[\n";

        for(Triple t : this.getEffectiveInsertTriples())
        {
            jsonString += "{\"s\":\"" + t.getSubject() + "\",\"p\":\"";
            jsonString += t.getPredicate() + "\",\"o\":\"" + t.getObjectString() + "\"},";
        }

        if(!this.getEffectiveInsertTriples().isEmpty())
        {
            jsonString = jsonString.substring(0, jsonString.length() - 1);
        }

        jsonString += "\n]\n\"deletes\":\n[\n";

        for(Triple t : this.getEffectiveDeleteTriples())
        {
            jsonString += "{\"s\":\"" + t.getSubject() + "\",\"p\":\"";
            jsonString += t.getPredicate() + "\",\"o\":\"" + t.getObjectString() + "\"},";
        }

        if(!this.getEffectiveDeleteTriples().isEmpty())
        {
            jsonString = jsonString.substring(0, jsonString.length() - 1);
        }

        jsonString += "\n]\n}";

        return jsonString;
    }
    public Set<Triple> getAllInsertTriples() {
        return allInsertTriples;
    }

    public void setAllInsertTriples(Set<Triple> allInsertTriples) {
        this.allInsertTriples = allInsertTriples;
    }

    public Set<Triple> getAllDeleteTriples() {
        return allDeleteTriples;
    }

    public void setAllDeleteTriples(Set<Triple> allDeleteTriples) {
        this.allDeleteTriples = allDeleteTriples;
    }

    public Set<Triple> getEffectiveDeleteTriples() {
        return effectiveDeleteTriples;
    }

    public void setEffectiveDeleteTriples(Set<Triple> effectiveDeleteTriples) {
        this.effectiveDeleteTriples = effectiveDeleteTriples;
    }

    public Set<Triple> getEffectiveInsertTriples() {
        return effectiveInsertTriples;
    }

    public void setEffectiveInsertTriples(Set<Triple> effectiveInsertTriples) {
        this.effectiveInsertTriples = effectiveInsertTriples;
    }
}
