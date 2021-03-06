package org.jocean.zkoss.builder;

import org.zkoss.zk.ui.Component;
import org.zkoss.zk.ui.ext.Disable;
import org.zkoss.zul.ListModel;
import org.zkoss.zul.RowRenderer;

public interface BeanGridRenderer<T> extends RowRenderer<T>, Disable, ComponentHub {
    public <C extends Component> C attachComponentToCell(final int row, final int col, final C comp);
    public ListModel<T> buildModel();
}
