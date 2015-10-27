package org.jocean.zkoss.builder.impl;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import org.jocean.idiom.Pair;
import org.jocean.idiom.ReflectUtils;
import org.jocean.zkoss.annotation.CellSetter;
import org.jocean.zkoss.annotation.GridCell;
import org.jocean.zkoss.builder.BeanGridRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zkoss.zk.ui.Component;
import org.zkoss.zul.AbstractListModel;
import org.zkoss.zul.Label;
import org.zkoss.zul.ListModel;
import org.zkoss.zul.Row;

public class BeanGridRendererImpl<T> implements BeanGridRenderer<T> {
    @SuppressWarnings("unused")
    private static final Logger LOG = 
            LoggerFactory.getLogger(BeanGridRendererImpl.class);
    
    public BeanGridRendererImpl(final T bean) {
        this._bean = bean;
        final Class<?> cls = bean.getClass();
        final Method[] getters = ReflectUtils.getAnnotationMethodsOf(cls, GridCell.class);
        final Method[] setters = ReflectUtils.getAnnotationMethodsOf(cls, CellSetter.class);
        this._cols = calcColCount(getters);
        this._rows = calcRowCount(getters);
        for (Method getter : getters) {
            final GridCell gridcell = getter.getAnnotation(GridCell.class);
            this._xy2cell.put(Pair.of(gridcell.row(), gridcell.col()), 
                buildCell(gridcell, getter, findSetter(setters, gridcell.name())));
        }
    }
    
    private Method findSetter(final Method[] setters, final String name) {
        for ( Method m : setters) {
            if ( name.equals(m.getAnnotation(CellSetter.class).name())) {
                return m;
            }
        }
        return null;
    }

    private CellImpl buildCell(final GridCell gridcell, 
            final Method getter, 
            final Method setter) {
        final CellImpl cell = new CellImpl(gridcell, this._bean, getter, setter);
        this._name2cell.put(gridcell.name(), cell);
        return cell;
    }

    @Override
    public <C extends Component> C attachComponentToCell(final int row, final int col, final C comp) {
        this._xy2cell.put(Pair.of(row, col),new CellImpl((Component)comp));
        enlargeRowCol(row, col);
        return comp;
    }
    
    private void enlargeRowCol(final int row, final int col) {
        if (this._rows <= row) {
            this._rows = row+1;
        }
        if (this._cols <= col) {
            this._cols = col+1;
        }
    }

    @Override
    public void render(final Row row, final T bean, int rowidx)
            throws Exception {
        for (int col = 0; col < this._cols; col++) {
            row.appendChild(renderCell(this._xy2cell.get(Pair.of(rowidx, col))));
        }
    }

    private Component renderCell(final CellImpl cell) {
        if (null == cell) {
            return new Label("");
        } else {
            return cell.render();
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public <C extends Component> C getComponent(final String name) {
        final CellImpl cell = this._name2cell.get(name);
        return null!=cell ? (C)cell.getComponent() : null;
    }
    
    @Override
    public ListModel<T> buildModel() {
        return new AbstractListModel<T>() {
            private static final long serialVersionUID = 1L;

            @Override
            public T getElementAt(final int index) {
                return _bean;
            }

            @Override
            public int getSize() {
                return _rows;
            }};
    }
    
    @Override
    public boolean isDisabled() {
        return this._isDisabled;
    }

    @Override
    public void setDisabled(final boolean disabled) {
        if (this._isDisabled != disabled) {
            this._isDisabled = disabled;
            for (CellImpl cell : this._name2cell.values()) {
                cell.setDisableStatus(disabled);
            }
        }
    }

    private static int calcRowCount(final Method[] methods) {
        int rows = -1;
        for (Method method : methods) {
            final GridCell cell = method.getAnnotation(GridCell.class);
            if (null!=cell) {
                if (cell.row() > rows) {
                    rows = cell.row();
                }
            }
        }
        return rows + 1;
    }
    
    private static int calcColCount(final Method[] methods) {
        int cols = -1;
        for (Method method : methods) {
            final GridCell cell = method.getAnnotation(GridCell.class);
            if (null!=cell) {
                if (cell.col() > cols) {
                    cols = cell.col();
                }
            }
        }
        return cols + 1;
    }
    
    private int _rows;
    private int _cols;
    private boolean _isDisabled = false;
    private final T _bean;
    private final Map<String, CellImpl> _name2cell = new HashMap<>();
    private final Map<Pair<Integer,Integer>, CellImpl> _xy2cell = new HashMap<>();
}
