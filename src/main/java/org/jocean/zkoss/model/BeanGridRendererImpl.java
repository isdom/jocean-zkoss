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
import org.jocean.zkoss.annotation.GridField;
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
        final Method[] getters = ReflectUtils.getAnnotationMethodsOf(cls, GridField.class);
        final Method[] setters = ReflectUtils.getAnnotationMethodsOf(cls, ValueSetter.class);
        this._cols = calcColCount(getters);
        this._rows = calcRowCount(getters);
        for (Method getter : getters) {
            final GridField field = getter.getAnnotation(GridField.class);
            this._cells.put(Pair.of(field.row(), field.col()), 
                buildCellComponent(this._bean, field, getter, findSetter(setters, field.name())));
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

    private Triple<Component,Method,Method> buildCellComponent(
            final T bean, final GridField field, final Method getter, final Method setter) {
        final Component fieldcomp = buildFieldComponent(field);
        initAndBindField(bean, getter, setter, fieldcomp);
        this._fields.put(field.name(), Pair.of(fieldcomp, setter));
        return Triple.of(getOrBuildCell(field.name(), fieldcomp), getter, setter);
    }

    private Component getOrBuildCell(final String name,
            final Component fieldcomp) {
        if (fieldcomp instanceof LabelElement) {
            ((LabelElement)fieldcomp).setLabel(name);
            return fieldcomp;
        } else {
            return new Hlayout() {
                private static final long serialVersionUID = 1L;
            {
                this.appendChild(new Label(name));
                this.appendChild(fieldcomp);
            }};
        }
    }
    
    private Component buildFieldComponent(final GridField field) {
        try {
            return field.component().newInstance();
        } catch (Exception e) {
            LOG.warn("exception when newInstance for {}, detail:{}",
                    field.component(), ExceptionUtils.exception2detail(e));
            return new Label(e.toString());
        }
    }
    
    private void initAndBindField(final T bean, final Method getter, final Method setter, final Component fieldcomp) {
        Object value = null;
        try {
            value = getter.invoke(bean);
            if (null!=value) {
                assignValueToField(value, fieldcomp);
            }
        } catch (Exception e) {
            LOG.warn("exception when invoke {}.{}, detail: {}", 
                    bean, getter, ExceptionUtils.exception2detail(e));
        } 
        if (null==setter) {
            if (fieldcomp instanceof Disable) {
                ((Disable)fieldcomp).setDisabled(true);
            }
            return;
        } else if (setter.getParameterTypes().length>0) {
            final Class<?> setterParamType = setter.getParameterTypes()[0];
            final Action1<Object> injector = new Action1<Object>() {
                @Override
                public void call(final Object v) {
                    try {
                        setter.invoke(bean, v);
                    } catch (Exception e) {
                        LOG.warn("exception when invoke {}.{}, detail:{}",
                                bean, setter, ExceptionUtils.exception2detail(e));
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
            if (fieldcomp instanceof InputElement) {
                final InputElement input = (InputElement)fieldcomp;
                input.addEventListener(Events.ON_CHANGE, new EventListener<InputEvent>() {
                    @Override
                    public void onEvent(final InputEvent event) throws Exception {
                        if (null!=editor) {
                            setTextViaMethod(bean, setter, editor, event.getValue());
                        }
                    }});
            }
        }
    }

    private void assignValueToField(final Object value,
            final Component fieldcomp) {
        attachModelToField(value, fieldcomp);
        
        if (fieldcomp instanceof InputElement) {
            ((InputElement)fieldcomp).setText(getValueAsText(value));
        } else if (fieldcomp instanceof LabelElement) {
            ((LabelElement)fieldcomp).setLabel(getValueAsText(value));
        }
    }

    private void attachModelToField(final Object value, final Component fieldcomp) {
        for (Triple<Class<? extends Component>,Class<?>,Method> triple : _COMPONENT_MODEL) {
            if (triple.first.isAssignableFrom(fieldcomp.getClass())
             && triple.second.isAssignableFrom(value.getClass())) {
                try {
                    triple.third.invoke(fieldcomp, value);
                } catch (Exception e) {
                    LOG.warn("exception when invoke {}/{}, detail: {}",
                            fieldcomp, triple.third, ExceptionUtils.exception2detail(e));
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
            final PropertyEditor editor, 
            final String text) throws Exception {
        editor.setAsText(text);
        setter.invoke(bean, editor.getValue());
    }
    
    @Override
    public <C extends Component> C attachComponentToCell(final int row, final int col, final C comp) {
        this._cells.put(Pair.of(row, col), Triple.of((Component)comp, (Method)null, (Method)null));
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
            row.appendChild(renderCell(this._cells.get(Pair.of(rowidx, col))));
        }
    }

    private Component renderCell(final Triple<Component, Method, Method> triple) {
        if (null == triple) {
            return new Label("");
        } else {
            return triple.first;
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public <C extends Component> C getComponent(final String name) {
        return (C)this._fields.get(name).first;
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
            for (Pair<Component,Method> pair : this._fields.values()) {
                setFieldDisableStatus(pair.first, pair.second, disabled);
            }
        }
    }

    private void setFieldDisableStatus(
            final Component fieldcomp,
            final Method setter,
            final boolean disabled) {
        if (null!=setter) {
            if ( fieldcomp instanceof Disable) {
                ((Disable)fieldcomp).setDisabled(disabled);
            }
        }
    }
    
    private static int calcRowCount(final Method[] methods) {
        int rows = -1;
        for (Method method : methods) {
            final GridField cell = method.getAnnotation(GridField.class);
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
            final GridField cell = method.getAnnotation(GridField.class);
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
    private final Map<Pair<Integer,Integer>, Triple<Component, Method, Method>> _cells = new HashMap<>();
    private final Map<String, Pair<Component,Method>> _fields = new HashMap<>();
}
