package org.jocean.zkoss.model;

import java.beans.PropertyEditor;
import java.beans.PropertyEditorManager;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

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
import org.zkoss.zul.Hbox;
import org.zkoss.zul.Label;
import org.zkoss.zul.ListModel;
import org.zkoss.zul.Row;
import org.zkoss.zul.impl.InputElement;
import org.zkoss.zul.impl.LabelElement;

class BeanGridRendererImpl2<T> implements BeanGridRenderer<T> {
    private static final Logger LOG = 
            LoggerFactory.getLogger(BeanGridRendererImpl2.class);
    
    public BeanGridRendererImpl2(final Class<T> cls) {
        final Method[] cellmethods = ReflectUtils.getAnnotationMethodsOf(cls, GridCell.class);
        final Method[] settermethods = ReflectUtils.getAnnotationMethodsOf(cls, ValueSetter.class);
        this._cols = calcColCount(cellmethods);
        this._rows = calcRowCount(cellmethods);
        for (Method method : cellmethods) {
            final GridCell cell = method.getAnnotation(GridCell.class);
            this._components.put(Pair.of(cell.row(), cell.col()), 
                Triple.of(buildComponent(method, cell), 
                        method, 
                        findSetter(settermethods, cell.name())));
        }
    }
    
    private Method findSetter(final Method[] methods, final String name) {
        for ( Method m : methods) {
            if ( name.equals(m.getAnnotation(ValueSetter.class).name())) {
                return m;
            }
        }
        return null;
    }

    private Component buildComponent(final Method method, final GridCell cell) {
        final Component element = buildElement(cell);
        this._elements.put(cell.name(), element);
        if (element instanceof LabelElement) {
            ((LabelElement)element).setLabel(cell.name());
            return element;
        } else {
//            if ("".equals(cell.name())) {
//                return element;
//            } else {
                return new Hbox() {
                    private static final long serialVersionUID = 1L;
                {
                    this.appendChild(new Label(cell.name()));
                    this.appendChild(element);
                }};
//            }
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
            row.appendChild(renderComponent(this._components.get(Pair.of(rowidx, col)), bean));
        }
    }

    private Component renderComponent(final Triple<Component, Method, Method> triple, final T bean) {
        if (null == triple) {
            return new Label("");
        } else {
            final Method cellmethod = triple.second;
            final Method setter = triple.third;
            if (null!=cellmethod) {
                final Component element = this._elements.get(cellmethod.getAnnotation(GridCell.class).name());
                if (null!=element) {
                    assignValueToElement(bean, cellmethod, element);
                    attachMethodToElement(bean, setter, element);
                }
            }
            return triple.first;
        }
    }

    private void assignValueToElement(final Object bean, final Method method, final Component element) {
        try {
            final Object value = method.invoke(bean);
            if (null!=value) {
                if (element instanceof InputElement) {
                    ((InputElement)element).setText(value.toString());
                } else if (element instanceof LabelElement) {
                    ((LabelElement)element).setLabel(value.toString());
                }
            }
        } catch (Exception e) {
            LOG.warn("exception when invoke {}.{}, detail: {}", 
                    bean, method, ExceptionUtils.exception2detail(e));
        } 
    }

    private void attachMethodToElement(final T bean, final Method method, final Component element) {
        if (null==method) {
            if (element instanceof Disable) {
                ((Disable)element).setDisabled(true);
            }
            return;
        }
        if (element instanceof InputElement) {
            final InputElement input = (InputElement)element;
            input.addEventListener(Events.ON_CHANGE, new EventListener<InputEvent>() {
                @Override
                public void onEvent(final InputEvent event) throws Exception {
                    setTextViaMethod(bean, method, event.getValue());
                }});
        }
    }

    private void setTextViaMethod(
            final Object bean,
            final Method method, 
            final String text) throws Exception {
        final PropertyEditor editor = PropertyEditorManager.findEditor(method.getParameterTypes()[0]);
        if (null!=editor) {
            editor.setAsText(text);
            method.invoke(bean, editor.getValue());
        }
    }

    private Component buildElement(final GridCell cell) {
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
        return (C)this._elements.get(name);
    }
    
    @Override
    public ListModel<T> buildModel(final T bean) {
        return new AbstractListModel<T>() {
            private static final long serialVersionUID = 1L;

            @Override
            public T getElementAt(final int index) {
                return bean;
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
    
    private final Map<Pair<Integer,Integer>, Triple<Component, Method, Method>> _components = new HashMap<>();
    private final Map<String, Component> _elements = new HashMap<>();
    private int _rows;
    private int _cols;
}
