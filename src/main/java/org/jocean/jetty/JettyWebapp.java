package org.jocean.jetty;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.net.InetSocketAddress;
import java.nio.channels.ServerSocketChannel;

import org.eclipse.jetty.jmx.MBeanContainer;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.util.component.Container;
import org.eclipse.jetty.util.component.LifeCycle;
import org.eclipse.jetty.webapp.Configuration;
import org.eclipse.jetty.webapp.WebAppContext;
import org.jocean.idiom.ExceptionUtils;
import org.jocean.j2se.PropertyPlaceholderConfigurerAware;
import org.jocean.j2se.jmx.MBeanRegister;
import org.jocean.j2se.jmx.MBeanRegisterAware;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.PropertyPlaceholderConfigurer;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.util.StringUtils;

public class JettyWebapp implements MBeanRegisterAware, WebappMXBean, 
    ApplicationContextAware, PropertyPlaceholderConfigurerAware {
    
    private static final Logger LOG = 
            LoggerFactory.getLogger(JettyWebapp.class);
    
    public JettyWebapp(final String host, 
            final int port, 
            final String contextPath, 
            final String category,
            final int priority
            ) {
        this._host = host;
        this._port = port;
        this._contextPath = contextPath;
        this._category = category;
        this._priority = priority;
        this._mbContainer=new MBeanContainer(ManagementFactory.getPlatformMBeanServer());
    }
    
    public void start() throws Exception {
        
        JoceanContextLoaderListener.registerParentCtx(this._contextPath, this._applicationContext);
        if (null != this._configurer) {
            SetPlaceholderConfigurerInitializer.registerPlaceholderConfigurer(this._contextPath, this._configurer);
        }
        
        final InetSocketAddress address = new InetSocketAddress(this._host, this._port);
        final Server server = new Server(address);
        
        // Setup JMX
        server.addEventListener(this._mbContainer);
        server.addBean(this._mbContainer);
        
        server.addEventListener(new Container.Listener() {
            @Override
            public void beanAdded(Container parent, Object child) {
                if (child instanceof ServerConnector) {
                    ((ServerConnector) child).addLifeCycleListener(new LifeCycle.Listener() {
                        @Override
                        public void lifeCycleStarting(LifeCycle event) {
                        }
                        @Override
                        public void lifeCycleStarted(LifeCycle event) {
                            try {
                                final ServerConnector connector = (ServerConnector)event;
                                final ServerSocketChannel channel = (ServerSocketChannel)connector.getTransport();
                                final InetSocketAddress addr = (InetSocketAddress)channel.getLocalAddress();
                                _localPort = connector.getLocalPort();
                                _bindip = null != addr.getAddress()
                                        ? addr.getAddress().getHostAddress()
                                        : "0.0.0.0";
                            } catch(Exception e) {
                                LOG.warn("exception when get local address info, detail: {}", 
                                        ExceptionUtils.exception2detail(e));
                            }
                            _unitsRegister.registerMBean("webapp="+_contextPath+",address="+_bindip+",port="+_localPort, 
                                    JettyWebapp.this);
                        }
                        @Override
                        public void lifeCycleFailure(LifeCycle event,
                                Throwable cause) {
                        }
                        @Override
                        public void lifeCycleStopping(LifeCycle event) {
                        }
                        @Override
                        public void lifeCycleStopped(LifeCycle event) {
                            _unitsRegister.unregisterMBean("webapp="+_contextPath+",address="+_bindip+",port="+_localPort);
                        }});
                }
            }

            @Override
            public void beanRemoved(Container parent, Object child) {
            }});
        
        final WebAppContext context = new WebAppContext();
        context.setContextPath(this._contextPath);
        
        final String warfile = System.getProperty("user.dir") 
                + System.getProperty("file.separator")
                + "bin"
                + System.getProperty("file.separator")
                + System.getProperty("app.name")
                + ".jar";
        if ( new File(warfile).exists() ) {
            final File tmpdir = new File(System.getProperty("user.home") 
                    + System.getProperty("file.separator")
                    + ".jetty");
            if (!tmpdir.exists()) {
                tmpdir.mkdirs();
            }
            context.setTempDirectory(tmpdir);
            context.setWar(warfile);
        } else {
            context.setDescriptor( "scripts/webcontent/WEB-INF/web.xml");
            context.setResourceBase( "scripts/webcontent/");
            context.setParentLoaderPriority(true);
        }
        
        if (null != this._configurationClasses && this._configurationClasses.length > 0) {
            // This webapp will use jsps and jstl. We need to enable the
            // AnnotationConfiguration in order to correctly
            // set up the jsp container
            final Configuration.ClassList classlist = Configuration.ClassList
                    .setServerDefault( server );
            classlist.addBefore(
                    "org.eclipse.jetty.webapp.JettyWebXmlConfiguration", this._configurationClasses);
//                        "org.eclipse.jetty.annotations.AnnotationConfiguration" );
        }
        
        if (null != this._contextAttributes && this._contextAttributes.length > 0) {
            // Set the ContainerIncludeJarPattern so that jetty examines these
            // container-path jars for tlds, web-fragments etc.
            // If you omit the jar that contains the jstl .tlds, the jsp engine will
            // scan for them instead.
            for (int idx=0; idx<this._contextAttributes.length / 2; idx++) {
                context.setAttribute(this._contextAttributes[idx *2], this._contextAttributes[idx*2+1]);
                
            }
//            context.setAttribute(
//                    "org.eclipse.jetty.server.webapp.ContainerIncludeJarPattern",
//                    ".*/[^/]*servlet-api-[^/]*\\.jar$|.*/javax.servlet.jsp.jstl-.*\\.jar$|.*/[^/]*taglibs.*\\.jar$|.*/[^/]*struts2-core-[^/]*\\.jar$" );
        }
        
        server.setHandler(context);
 
        server.start();
        this._server = server;
    }
    
    public void stop() throws Exception {
        if (null != this._server) {
            final Server server = this._server;
            this._server = null;
            server.stop();
        }
    }
    
    /**
     * @return the host
     */
    @Override
    public String getHost() {
        return this._host;
    }

    @Override
    public String getBindIp() {
        return this._bindip;
    }
    
    /**
     * @return the localPort
     */
    @Override
    public int getPort() {
        return this._localPort;
    }

    @Override
    public String getCategory() {
        return this._category;
    }

    @Override
    public String getPathPattern() {
        return _contextPath+"/[/|\\w]*";
    }

    @Override
    public int getPriority() {
        return this._priority;
    }
    
    @Override
    public void setMBeanRegister(final MBeanRegister register) {
        this._unitsRegister = register;
    }

    @Override
    public void setApplicationContext(final ApplicationContext applicationContext)
            throws BeansException {
        this._applicationContext = applicationContext;
    }
    
    @Override
    public void setPropertyPlaceholderConfigurer(final PropertyPlaceholderConfigurer configurer) {
        this._configurer = configurer;
        
    }
    
    /**
     * @param configurationClassList the configurationClassList to set
     */
    public void setConfigurationClasses(final String configurationClassList) {
        if (null != configurationClassList && !configurationClassList.isEmpty()) {
            final String[] configurationClasses = 
                    StringUtils.commaDelimitedListToStringArray(configurationClassList);
            if (null != configurationClasses && configurationClasses.length > 0) {
                this._configurationClasses = configurationClasses;
            }
        }
    }
    
    @Override
    public String[] getConfigurationClasses() {
        return this._configurationClasses;
    }
    
    /**
     * @param contextAttributes the _contextAttributes to set
     */
    public void setContextAttributes(final String contextAttributes) {
        if (null != contextAttributes && !contextAttributes.isEmpty()) {
            final String[] attributes = 
                    StringUtils.commaDelimitedListToStringArray(contextAttributes);
            if (null != attributes && attributes.length > 0) {
                this._contextAttributes = attributes;
            }
        }
    }

    @Override
    public String[] getContextAttributes() {
        return this._contextAttributes;
    }

    private String[] _configurationClasses;
    private String[] _contextAttributes;
    
    private final String _category;
    private final int   _priority;
    private int _localPort;
    private final MBeanContainer _mbContainer;
    private final String  _host;
    private String  _bindip;
    private final int     _port;
    private final String  _contextPath;
    private MBeanRegister _unitsRegister;
    private ApplicationContext _applicationContext;
    private PropertyPlaceholderConfigurer _configurer;
    private Server _server;

}
