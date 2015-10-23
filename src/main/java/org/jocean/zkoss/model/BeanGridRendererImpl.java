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
import org.zkoss.zul.ext.Selectable;
import org.zkoss.zul.impl.InputElement;
import org.zkoss.zul.impl.LabelElement;

class BeanGridRendererImpl<T> implements BeanGridRenderer<T> {
    private static final Logger LOG = 
            LoggerFactory.getLogger(BeanGridRendererImpl.class);
    
    @SuppressWarnings("unchecked")
    private static final Triple<Class<? extends Component>,Class<?>,Method>[] _COMPONENT_MODEL = 
            new Triple[] {
        Triple.of(Combobox.class, ListModel.class, genMethod(Combobox.class, "setModel", ListModel.class)),
        Triple.of(Tree.class, TreeModel.class, genMethod(Tree.class, "setModel", TreeModel.class)),
    };

    private static Method genMethod(final Class<?> cls, final String methodName, final Class<?> ...parameterTypes ) {
        try {
            final Method method = cls.getDeclaredMethod(methodName, parameterTypes);
            if (null!=method) {
                method.setAccessible(true);
            }
            return method;
        } catch (Exception e) {
            LOG.warn("exception when getDeclaredMethod for {}/{}, detail:{}",
                    cls, methodName, ExceptionUtils.exception2detail(e));
            return null;
        }
    }
    
    public BeanGridRendererImpl(final T bean) {
        this._bean = bean;
        final Class<?> cls = bean.getClass();
        final Method[] getters = ReflectUtils.getAnnotationMethodsOf(cls, GridCell.class);
        final Method[] setters = ReflectUtils.getAnnotationMethodsOf(cls, ValueSetter.class);
        this._cols = calcColCount(getters);
        this._rows = calcRowCount(getters);
        for (Method getter : getters) {
            final GridCell cell = getter.getAnnotation(GridCell.class);
            this._components.put(Pair.of(cell.row(), cell.col()), 
                Triple.of(buildComponent(cell), 
                        getter, 
                        findSetter(setters, cell.name())));
        }
        for (Triple<Component, Method, Method> triple : this._components.values()) {
            prepareComponent(triple, this._bean);
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

    private Component buildComponent(final GridCell cell) {
        final Component cellcomp = buildCell(cell);
        this._cells.put(cell.name(), cellcomp);
        if (cellcomp instanceof LabelElement) {
            ((LabelElement)cellcomp).setLabel(cell.name());
            return cellcomp;
        } else {
            return new Hlayout() {
                private static final long serialVersionUID = 1L;
            {
                this.appendChild(new Label(cell.name()));
                this.appendChild(cellcomp);
            }};
        }
    }
    
    @Override
    public <C extends Component> C attachComponent(final int row, final int col, final C comp) {
        this._components.put(Pair.of(row, col), Triple.of((Component)comp, (Method)null, (Method)null));
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
            row.appendChild(renderComponent(this._components.get(Pair.of(rowidx, col))));
        }
    }

    private Component renderComponent(final Triple<Component, Method, Method> triple) {
        if (null == triple) {
            return new Label("");
        } else {
            return triple.first;
        }
    }
    
    private void prepareComponent(final Triple<Component, Method, Method> triple, final T bean) {
        final Method getter = triple.second;
        final Method setter = triple.third;
        if (null!=getter) {
            final Component cellcomp = this._cells.get(getter.getAnnotation(GridCell.class).name());
            if (null!=cellcomp) {
                attachBeanToCell(bean, getter, setter, cellcomp);
            }
        }
    }

    private void attachBeanToCell(final T bean, final Method getter, final Method setter, final Component cellcomp) {
        try {
            final Object value = getter.invoke(bean);
            if (null!=value) {
                assignValueToCell(value, cellcomp);
            }
        } catch (Exception e) {
            LOG.warn("exception when invoke {}.{}, detail: {}", 
                    bean, getter, ExceptionUtils.exception2detail(e));
        } 
        if (null==setter) {
            if (cellcomp instanceof Disable) {
                ((Disable)cellcomp).setDisabled(true);
            }
            return;
        }
        if (cellcomp instanceof InputElement) {
            final InputElement input = (InputElement)cellcomp;
            input.addEventListener(Events.ON_CHANGE, new EventListener<InputEvent>() {
                @Override
                public void onEvent(final InputEvent event) throws Exception {
                    setTextViaMethod(bean, setter, event.getValue());
                }});
        }
    }

    private void assignValueToCell(final Object value,
            final Component cellcomp) {
        attachModelToComponent(value, cellcomp);
        
        if (cellcomp instanceof InputElement) {
            ((InputElement)cellcomp).setText(getValueAsText(value));
        } else if (cellcomp instanceof LabelElement) {
            ((LabelElement)cellcomp).setLabel(getValueAsText(value));
        }
    }

    private void attachModelToComponent(final Object value, final Component cellcomp) {
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

    private void setTextViaMethod(
            final Object bean,
            final Method setter, 
            final String text) throws Exception {
        final PropertyEditor editor = PropertyEditorManager.findEditor(setter.getParameterTypes()[0]);
        if (null!=editor) {
            editor.setAsText(text);
            setter.invoke(bean, editor.getValue());
        }
    }

    private Component buildCell(final GridCell cell) {
        try {
            return cell.component().newInstance();
        } catch (Exception e) {
            LOG.warn("exception when newInstance for {}, detail:{}",
                    cell.component(), ExceptionUtils.exception2detail(e));
            return new Label(e.toString());
        }
    }
    
    @SuppressWarnings("unchecked")
    @Override
    public <C extends Component> C getComponent(final String name) {
        return (C)this._cells.get(name);
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
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public void setDisabled(final boolean disabled) {
        // TODO Auto-generated method stub
        
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
    private final T _bean;
    private final Map<Pair<Integer,Integer>, Triple<Component, Method, Method>> _components = new HashMap<>();
    private final Map<String, Component> _cells = new HashMap<>();
}
