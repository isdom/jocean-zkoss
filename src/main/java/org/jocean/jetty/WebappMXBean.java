package org.jocean.jetty;

public interface WebappMXBean {
    
    public String getHost();
    
    public String getBindIp();
    
    public int getPort();

    public String getCategory();
    
    public String getPathPattern();
    
    public int getPriority();
    
    public String[] getContextAttributes();
    
    public String[] getConfigurationClasses();
}
