package org.jocean.jetty;

import java.util.HashMap;
import java.util.Map;

import javax.servlet.ServletContext;

import org.springframework.context.ApplicationContext;
import org.springframework.web.context.ContextLoaderListener;

public class JoceanContextLoaderListener extends ContextLoaderListener {
    @Override
    protected ApplicationContext loadParentContext(final ServletContext servletContext) {
        return _PARENT_CTXS.get(servletContext.getContextPath());
    }
    
    public static void registerParentCtx(final String ctxPath, final ApplicationContext ac) {
        _PARENT_CTXS.put(ctxPath, ac);
    }
    
    private static final Map<String, ApplicationContext> _PARENT_CTXS = new HashMap<>();
}
