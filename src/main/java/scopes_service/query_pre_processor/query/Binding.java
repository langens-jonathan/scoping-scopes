package scopes_service.query_pre_processor.query;

import java.util.HashMap;
import java.util.List;

/**
 * Created by langens-jonathan on 31.05.16.
 */
public class Binding
{
    private HashMap<String, String> bindings = new HashMap<String, String>();

    public void addBinding(String k, String v)
    {
        this.bindings.put(k, v);
    }

    public String getBindingFor(String k)
    {
        return this.bindings.get(k);
    }

    public boolean canHandle(List<String> unknowns)
    {
        for(String k : unknowns)
        {
            if(this.bindings.containsKey(k) == false)
                return false;
        }
        return true;
    }
}
