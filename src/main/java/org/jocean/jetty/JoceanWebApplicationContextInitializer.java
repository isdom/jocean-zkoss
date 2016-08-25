package org.jocean.jetty;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.jocean.j2se.spring.PropertiesResourceSetter;
import org.jocean.j2se.spring.PropertyPlaceholderConfigurerSetter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.config.PropertyPlaceholderConfigurer;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.web.context.ConfigurableWebApplicationContext;

public class JoceanWebApplicationContextInitializer
        implements ApplicationContextInitializer<ConfigurableWebApplicationContext> {

    private static final Logger LOG = 
            LoggerFactory.getLogger(JoceanWebApplicationContextInitializer.class);
    
    public static void registerPlaceholderConfigurer(final String ctxPath, final PropertyPlaceholderConfigurer configurer) {
        _CONFIGURERS.put(ctxPath, configurer);
    }
    
    public static void registerPropertiesResource(final String ctxPath, final Properties properties) {
        _PROPERTIESRESES.put(ctxPath, properties);
    }
    
    @Override
    public void initialize(final ConfigurableWebApplicationContext applicationContext) {
        applyPropertyPlaceholderConfigurer(applicationContext);
        applyPropertiesResource(applicationContext);
    }

    private void applyPropertiesResource(
            final ConfigurableWebApplicationContext applicationContext) {
        final Properties properties = _PROPERTIESRESES.get(
                applicationContext.getServletContext().getContextPath());
            if (null != properties) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("inject Properties({}) to ConfigurableWebApplicationContext({})",
                            properties, applicationContext);
                }
                applicationContext.addBeanFactoryPostProcessor(new BeanFactoryPostProcessor() {
                    @Override
                    public void postProcessBeanFactory(
                            final ConfigurableListableBeanFactory beanFactory)
                            throws BeansException {
                        if (null != properties) {
                            beanFactory.addBeanPostProcessor(new PropertiesResourceSetter(properties));
                        }
                    }});
            }
    }

    private void applyPropertyPlaceholderConfigurer(
            final ConfigurableWebApplicationContext applicationContext) {
        final PropertyPlaceholderConfigurer configurer = _CONFIGURERS.get(
            applicationContext.getServletContext().getContextPath());
        if (null != configurer) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("apply PropertyPlaceholderConfigurer({}) to ConfigurableWebApplicationContext({})",
                        configurer, applicationContext);
            }
            applicationContext.addBeanFactoryPostProcessor(configurer);
            applicationContext.addBeanFactoryPostProcessor(new BeanFactoryPostProcessor() {
                @Override
                public void postProcessBeanFactory(
                        final ConfigurableListableBeanFactory beanFactory)
                        throws BeansException {
                    beanFactory.addBeanPostProcessor(new PropertyPlaceholderConfigurerSetter(configurer));
                }});
        }
    }

    private static final Map<String, PropertyPlaceholderConfigurer> _CONFIGURERS = new HashMap<>();
    private static final Map<String, Properties> _PROPERTIESRESES = new HashMap<>();
}
