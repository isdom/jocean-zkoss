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
