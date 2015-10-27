package org.jocean.zkoss.builder;

import org.zkoss.zk.ui.Component;

public interface ComponentHub {
    public <C extends Component> C getComponent(final String name);
}
