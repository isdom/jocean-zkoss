package org.jocean.jetty;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.jocean.idiom.BeanHolder;
import org.jocean.j2se.jmx.MBeanRegister;
import org.jocean.j2se.jmx.MBeanRegisterSetter;
import org.jocean.j2se.spring.BeanHolderBasedInjector;
import org.jocean.j2se.spring.BeanHolderSetter;
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
    
    public static void registerBeanHolder(final String ctxPath, final BeanHolder beanHolder) {
        _BEANHOLDERS.put(ctxPath, beanHolder);
    }
    
    public static void registerMBeanRegister(final String ctxPath,final MBeanRegister mbeanRegister) {
        _MBEANREGISTERS.put(ctxPath, mbeanRegister);
    }
    
    @Override
    public void initialize(final ConfigurableWebApplicationContext applicationContext) {
        applyPropertyPlaceholderConfigurer(applicationContext);
        applyPropertiesResource(applicationContext);
        applyMBeanRegister(applicationContext);
        applyBeanHolder(applicationContext);
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

    private void applyMBeanRegister(
            final ConfigurableWebApplicationContext applicationContext) {
        final MBeanRegister register = _MBEANREGISTERS.get(
                applicationContext.getServletContext().getContextPath());
        if (null != register) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("apply MBeanRegister({}) to ConfigurableWebApplicationContext({})",
                        register, applicationContext);
            }
            applicationContext.addBeanFactoryPostProcessor(new BeanFactoryPostProcessor() {
                @Override
                public void postProcessBeanFactory(
                        final ConfigurableListableBeanFactory beanFactory)
                        throws BeansException {
                    beanFactory.addBeanPostProcessor(new MBeanRegisterSetter(register));
                }});
        }
    }

    private void applyBeanHolder(
            final ConfigurableWebApplicationContext applicationContext) {
        final BeanHolder holder = _BEANHOLDERS.get(
                applicationContext.getServletContext().getContextPath());
        if (null != holder) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("apply BeanHolder({}) to ConfigurableWebApplicationContext({})",
                        holder, applicationContext);
            }
            applicationContext.addBeanFactoryPostProcessor(new BeanFactoryPostProcessor() {
                @Override
                public void postProcessBeanFactory(
                        final ConfigurableListableBeanFactory beanFactory)
                        throws BeansException {
                    beanFactory.addBeanPostProcessor(new BeanHolderSetter(holder));
                    beanFactory.addBeanPostProcessor(new BeanHolderBasedInjector(new BeanHolder(){
                        @Override
                        public <T> T getBean(final Class<T> requiredType) {
                            try {
                                return applicationContext.getBean(requiredType);
                            } catch (Exception e) {
                                LOG.info("jettywebapp: can't found {} locally, try find global.", requiredType);
                            }
                            return holder.getBean(requiredType);
                        }

                        @Override
                        public <T> T getBean(final String name, final Class<T> requiredType) {
                            try {
                                return applicationContext.getBean(name, requiredType);
                            } catch (Exception e) {
                                LOG.info("jettywebapp:: can't found {}/{} locally, try find global.", name, requiredType);
                            }
                            return holder.getBean(name, requiredType);
                        }}));
                }});
        }
    }

    private static final Map<String, PropertyPlaceholderConfigurer> _CONFIGURERS = new HashMap<>();
    private static final Map<String, Properties> _PROPERTIESRESES = new HashMap<>();
    private static final Map<String, BeanHolder> _BEANHOLDERS = new HashMap<>();
    private static final Map<String, MBeanRegister> _MBEANREGISTERS = new HashMap<>();
    
}
