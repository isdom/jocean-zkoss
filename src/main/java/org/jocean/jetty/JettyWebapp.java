package org.jocean.jetty;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.net.InetSocketAddress;

import org.eclipse.jetty.jmx.MBeanContainer;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.util.component.Container;
import org.eclipse.jetty.util.component.LifeCycle;
import org.eclipse.jetty.webapp.WebAppContext;
import org.jocean.j2se.jmx.MBeanRegister;
import org.jocean.j2se.jmx.MBeanRegisterAware;

public class JettyWebapp implements MBeanRegisterAware, WebappMXBean {
    
    public JettyWebapp(final String host, final int port, final String contextPath) {
        this._host = host;
        this._port = port;
        this._contextPath = contextPath;
        this._mbContainer=new MBeanContainer(ManagementFactory.getPlatformMBeanServer());
    }
    
    public void start() throws Exception {
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
                            _localPort = ((ServerConnector)event).getLocalPort();
                            _unitsRegister.registerMBean("webapp="+_contextPath, JettyWebapp.this);
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
                            _unitsRegister.unregisterMBean("webapp="+_contextPath);
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
        final File tmpdir = new File(System.getProperty("user.home") 
                + System.getProperty("file.separator")
                + ".jetty");
        if (!tmpdir.exists()) {
            tmpdir.mkdirs();
        }
        context.setTempDirectory(tmpdir);
        context.setWar(warfile);
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
     * @return the localPort
     */
    @Override
    public int getLocalPort() {
        return this._localPort;
    }

    /**
     * @return the host
     */
    @Override
    public String getHost() {
        return this._host;
    }

    @Override
    public void setMBeanRegister(final MBeanRegister register) {
        this._unitsRegister = register;
    }

    private int _localPort;
    private final MBeanContainer _mbContainer;
    private final String  _host;
    private final int     _port;
    private final String  _contextPath;
    private MBeanRegister _unitsRegister;
    private Server _server;
}
