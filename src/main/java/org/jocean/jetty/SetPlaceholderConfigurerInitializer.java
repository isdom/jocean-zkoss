package org.jocean.jetty;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.PropertyPlaceholderConfigurer;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.web.context.ConfigurableWebApplicationContext;

public class SetPlaceholderConfigurerInitializer
        implements ApplicationContextInitializer<ConfigurableWebApplicationContext> {

    private static final Logger LOG = 
            LoggerFactory.getLogger(SetPlaceholderConfigurerInitializer.class);
    
    public static void registerPlaceholderConfigurer(final String ctxPath, final PropertyPlaceholderConfigurer configurer) {
        _CONFIGURERS.put(ctxPath, configurer);
    }
    
    @Override
    public void initialize(final ConfigurableWebApplicationContext applicationContext) {
        final PropertyPlaceholderConfigurer configurer = _CONFIGURERS.get(
            applicationContext.getServletContext().getContextPath());
        if (null != configurer) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("apply PropertyPlaceholderConfigurer({}) to ConfigurableWebApplicationContext({})",
                        configurer, applicationContext);
            }
            applicationContext.addBeanFactoryPostProcessor(configurer);
        }
    }

    private static final Map<String, PropertyPlaceholderConfigurer> _CONFIGURERS = new HashMap<>();
}
