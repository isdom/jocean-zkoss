package org.jocean.jetty;

public interface WebappMXBean {
    
    public int getLocalPort();

    public String getHost();
    
    public String getCategory();
    
    public String getPathPattern();
    
    public int getPriority();
}
