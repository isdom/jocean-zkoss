package org.jocean.zkoss.model;

import java.beans.PropertyEditor;
import java.beans.PropertyEditorManager;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.jocean.idiom.ExceptionUtils;
import org.jocean.idiom.Pair;
import org.jocean.idiom.ReflectUtils;
import org.jocean.idiom.Triple;
import org.jocean.zkoss.annotation.GridCell;
import org.jocean.zkoss.annotation.ValueSetter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zkoss.zk.ui.Component;
import org.zkoss.zk.ui.event.EventListener;
import org.zkoss.zk.ui.event.Events;
import org.zkoss.zk.ui.event.InputEvent;
import org.zkoss.zk.ui.ext.Disable;
import org.zkoss.zul.AbstractListModel;
import org.zkoss.zul.Combobox;
import org.zkoss.zul.Hlayout;
import org.zkoss.zul.Label;
import org.zkoss.zul.ListModel;
import org.zkoss.zul.Row;
import org.zkoss.zul.Tree;
import org.zkoss.zul.TreeModel;
import org.zkoss.zul.event.ListDataEvent;
import org.zkoss.zul.event.ListDataListener;
import org.zkoss.zul.ext.Selectable;
import org.zkoss.zul.impl.InputElement;
import org.zkoss.zul.impl.LabelElement;

import rx.functions.Action1;

class BeanGridRendererImpl<T> implements BeanGridRenderer<T> {
    private static final Logger LOG = 
            LoggerFactory.getLogger(BeanGridRendererImpl.class);
    
    @SuppressWarnings("unchecked")
    private static final Triple<Class<? extends Component>,Class<?>,Method>[] _COMPONENT_MODEL = 
            new Triple[] {
        Triple.of(Combobox.class, ListModel.class, 
                ReflectUtils.getMethodOf(Combobox.class, "setModel", ListModel.class)),
        Triple.of(Tree.class, TreeModel.class, 
                ReflectUtils.getMethodOf(Tree.class, "setModel", TreeModel.class)),
    };

    public BeanGridRendererImpl(final T bean) {
        this._bean = bean;
        final Class<?> cls = bean.getClass();
        final Method[] getters = ReflectUtils.getAnnotationMethodsOf(cls, GridCell.class);
        final Method[] setters = ReflectUtils.getAnnotationMethodsOf(cls, ValueSetter.class);
        this._cols = calcColCount(getters);
        this._rows = calcRowCount(getters);
        for (Method getter : getters) {
            final GridCell gidcell = getter.getAnnotation(GridCell.class);
            this._xy2cell.put(Pair.of(gidcell.row(), gidcell.col()), 
                buildCellComponent(gidcell, getter, findSetter(setters, gidcell.name())));
        }
    }
    
    private Method findSetter(final Method[] setters, final String name) {
        for ( Method m : setters) {
            if ( name.equals(m.getAnnotation(ValueSetter.class).name())) {
                return m;
            }
        }
        return null;
    }

    private Cell buildCellComponent(final GridCell gidcell, 
            final Method getter, 
            final Method setter) {
        final Cell cell = new Cell(gidcell, getter, setter);
        this._name2cell.put(gidcell.name(), cell);
        return cell;
    }

    private void assignValueToField(final Object value,
            final Component cellcomp) {
        attachModelToField(value, cellcomp);
        
        if (cellcomp instanceof InputElement) {
            ((InputElement)cellcomp).setText(getValueAsText(value));
        } else if (cellcomp instanceof LabelElement) {
            ((LabelElement)cellcomp).setLabel(getValueAsText(value));
        }
    }

    private void attachModelToField(final Object value, final Component cellcomp) {
        for (Triple<Class<? extends Component>,Class<?>,Method> triple : _COMPONENT_MODEL) {
            if (triple.first.isAssignableFrom(cellcomp.getClass())
             && triple.second.isAssignableFrom(value.getClass())) {
                try {
                    triple.third.invoke(cellcomp, value);
                } catch (Exception e) {
                    LOG.warn("exception when invoke {}/{}, detail: {}",
                            cellcomp, triple.third, ExceptionUtils.exception2detail(e));
                }
            }
        }
    }

    private String getValueAsText(final Object value) {
        if (value instanceof Selectable) {
            @SuppressWarnings("unchecked")
            final Set<Object> selected = ((Selectable<Object>)value).getSelection();
            if (!selected.isEmpty()) {
                return selected.iterator().next().toString();
            } else {
                return null;
            }
        } else {
            return value.toString();
        }
    }

    @Override
    public <C extends Component> C attachComponentToCell(final int row, final int col, final C comp) {
        this._xy2cell.put(Pair.of(row, col),new Cell((Component)comp));
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

    private Component renderCell(final Cell cell) {
        if (null == cell) {
            return new Label("");
        } else {
            return cell.render();
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public <C extends Component> C getComponent(final String name) {
        return (C)this._name2cell.get(name)._component;
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
            for (Cell cell : this._name2cell.values()) {
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
    
    private class Cell {
        private final GridCell _gridcell;
        private final Component _component;
        private final Method _getter;
        private final Method _setter;
        
        Cell(final Component component) {
            this._gridcell = null;
            this._component = component;
            this._getter = null;
            this._setter = null;
        }
        
        Cell(final GridCell gridcell, 
            final Method getter, 
            final Method setter) {
            this._gridcell = gridcell;
            this._component = buildFieldComponent(gridcell);
            this._getter = getter;
            this._setter = setter;
            initAndBindField();
            
        }
        
        private Component buildFieldComponent(final GridCell gridcell) {
            try {
                final Component cellcomp = gridcell.component().newInstance();
                if (cellcomp instanceof LabelElement) {
                    ((LabelElement)cellcomp).setLabel(gridcell.name());
                    return cellcomp;
                }
                return cellcomp;
            } catch (Exception e) {
                LOG.warn("exception when newInstance for {}, detail:{}",
                        gridcell.component(), ExceptionUtils.exception2detail(e));
                return new Label(e.toString());
            }
        }
        
        private void initAndBindField() {
            Object value = null;
            try {
                value = _getter.invoke(_bean);
                if (null!=value) {
                    assignValueToField(value, this._component);
                }
            } catch (Exception e) {
                LOG.warn("exception when invoke {}.{}, detail: {}", 
                        _bean, _getter, ExceptionUtils.exception2detail(e));
            } 
            if (null==_setter) {
                if (_component instanceof Disable) {
                    ((Disable)_component).setDisabled(true);
                }
                return;
            } else if (_setter.getParameterTypes().length>0) {
                final Class<?> setterParamType = _setter.getParameterTypes()[0];
                final Action1<Object> injector = new Action1<Object>() {
                    @Override
                    public void call(final Object v) {
                        try {
                            _setter.invoke(_bean, v);
                        } catch (Exception e) {
                            LOG.warn("exception when invoke {}.{}, detail:{}",
                                    _bean, _setter, ExceptionUtils.exception2detail(e));
                        }
                    }};
                if (null!=value && value instanceof ListModel ) {
                    ((ListModel<?>)value).addListDataListener(new ListDataListener() {
                        @Override
                        public void onChange(final ListDataEvent event) {
                            if (event.getType() == ListDataEvent.SELECTION_CHANGED ) {
                                if (event.getModel() instanceof Selectable) {
                                    final Selectable<?> selectable = ((Selectable<?>)event.getModel());
                                    if (!selectable.isSelectionEmpty()) {
                                        final Object selected = selectable.getSelection().iterator().next();
                                        if (setterParamType.isAssignableFrom(selected.getClass())) {
                                            injector.call(selected);
                                        }
                                    } else {
                                        injector.call(null);
                                    }
                                }
                            }
                        }});
                }
                final PropertyEditor editor = PropertyEditorManager.findEditor(setterParamType);
                if (_component instanceof InputElement) {
                    final InputElement input = (InputElement)_component;
                    input.addEventListener(Events.ON_CHANGE, new EventListener<InputEvent>() {
                        @Override
                        public void onEvent(final InputEvent event) throws Exception {
                            if (null!=editor) {
                                editor.setAsText(event.getValue());
                                injector.call(editor.getValue());
                            }
                        }});
                }
            }
        }

        Component render() {
            if ( null==this._gridcell 
              || this._component instanceof LabelElement) {
                return this._component;
            } else {
                return new Hlayout() {
                    private static final long serialVersionUID = 1L;
                {
                    this.appendChild(new Label(_gridcell.name()));
                    this.appendChild(_component);
                }};
            }
        }
        
        void setDisableStatus(
                final boolean disabled) {
            if (null!= this._setter) {
                if ( this._component instanceof Disable) {
                    ((Disable)_component).setDisabled(disabled);
                }
            }
        }
    }
    
    private int _rows;
    private int _cols;
    private boolean _isDisabled = false;
    private final T _bean;
    private final Map<Pair<Integer,Integer>, Cell> _xy2cell = new HashMap<>();
    private final Map<String, Cell> _name2cell = new HashMap<>();
}
