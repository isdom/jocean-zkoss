package org.jocean.zkoss.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zkoss.zk.ui.Desktop;
import org.zkoss.zk.ui.Execution;
import org.zkoss.zk.ui.Executions;
import org.zkoss.zk.ui.util.DesktopCleanup;

import rx.functions.Action0;

public class Desktops {
    
    private static final Logger LOG =
            LoggerFactory.getLogger(Desktops.class);
    
    private Desktops() {
        throw new IllegalStateException("No instances!");
    }

    public static void addActionForCurrentDesktopCleanup(final Action0 action) {
        final Execution exec = Executions.getCurrent();
        if (null != exec) {
            final Desktop desktop = exec.getDesktop();
            if (null != desktop) {
                desktop.addListener(new DesktopCleanup() {
                    @Override
                    public void cleanup(final Desktop desktop) throws Exception {
                        LOG.info("desktop ({}) cleanup, call action({})", desktop, action);
                        action.call();
                    }});
                return;
            }
        }
        LOG.warn("must call from valid Execution, Action({}) NOT added for desktop cleanup.",
                action);
    }
}
