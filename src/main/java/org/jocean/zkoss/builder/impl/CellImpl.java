package org.jocean.zkoss.builder.impl;

import java.beans.PropertyEditor;
import java.beans.PropertyEditorManager;
import java.lang.reflect.Method;
import java.util.Set;

import org.jocean.idiom.ExceptionUtils;
import org.jocean.idiom.ReflectUtils;
import org.jocean.idiom.Triple;
import org.jocean.zkoss.annotation.CellSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zkoss.zk.ui.Component;
import org.zkoss.zk.ui.event.EventListener;
import org.zkoss.zk.ui.event.Events;
import org.zkoss.zk.ui.event.InputEvent;
import org.zkoss.zk.ui.ext.Disable;
import org.zkoss.zul.Combobox;
import org.zkoss.zul.Hlayout;
import org.zkoss.zul.Label;
import org.zkoss.zul.ListModel;
import org.zkoss.zul.Tree;
import org.zkoss.zul.TreeModel;
import org.zkoss.zul.event.ListDataEvent;
import org.zkoss.zul.event.ListDataListener;
import org.zkoss.zul.ext.Selectable;
import org.zkoss.zul.impl.InputElement;
import org.zkoss.zul.impl.LabelElement;

class CellImpl {
    private static final Logger LOG = 
            LoggerFactory.getLogger(CellImpl.class);

    @SuppressWarnings("unchecked")
    private static final Triple<Class<? extends Component>,Class<?>,Method>[] _COMPONENT_MODEL = 
            new Triple[] {
        Triple.of(Combobox.class, ListModel.class, 
                ReflectUtils.getMethodOf(Combobox.class, "setModel", ListModel.class)),
        Triple.of(Tree.class, TreeModel.class, 
                ReflectUtils.getMethodOf(Tree.class, "setModel", TreeModel.class)),
    };

    private final Object _bean;
    private final CellSource _cellsource;
    private final Component _component;
    private final Method _getter;
    private final Method _setter;
    
    private final ListDataListener _listDataListener = new ListDataListener() {
        @Override
        public void onChange(final ListDataEvent event) {
            if (event.getType() == ListDataEvent.SELECTION_CHANGED ) {
                if (event.getModel() instanceof Selectable) {
                    final Selectable<?> selectable = ((Selectable<?>)event.getModel());
                    if (!selectable.isSelectionEmpty()) {
                        final Object selected = selectable.getSelection().iterator().next();
                        if (setterParameterType().isAssignableFrom(selected.getClass())) {
                            setValueToCell(selected);
                        }
                    } else {
                        setValueToCell(null);
                    }
                }
            }
        }};
        
    CellImpl(final Component component) {
        this._component = component;
        this._cellsource = null;
        this._bean = null;
        this._getter = null;
        this._setter = null;
    }
    
    CellImpl(final CellSource cellsource, 
        final Object bean,
        final Method getter, 
        final Method setter) {
        this._cellsource = cellsource;
        this._component = buildFieldComponent(cellsource);
        this._bean = bean;
        this._getter = getter;
        this._setter = setter;
        disableCellBySetter();
        bindCellByGetterAndSetter();
    }
    
    private void disableCellBySetter() {
        if (null==_setter && getComponent() instanceof Disable) {
            ((Disable)getComponent()).setDisabled(true);
        }
    }

    private Component buildFieldComponent(final CellSource cellsource) {
        try {
            final Component cellcomp = cellsource.component().newInstance();
            if (cellcomp instanceof LabelElement) {
                ((LabelElement)cellcomp).setLabel(cellsource.name());
                return cellcomp;
            }
            return cellcomp;
        } catch (Exception e) {
            LOG.warn("exception when newInstance for {}, detail:{}",
                    cellsource.component(), ExceptionUtils.exception2detail(e));
            return new Label(e.toString());
        }
    }
    
    private void bindCellByGetterAndSetter() {
        final Object value = getValueFromCell();
        
        if (null!=value) {
            attachModelToCell(value);
            assignValueForInput(value);
        }
        
        if (isSetterValid()) {
            bindCellAsListModel(value);
            bindCellForInput();
        }
    }

    private void attachModelToCell(final Object value) {
        for (Triple<Class<? extends Component>,Class<?>,Method> triple : _COMPONENT_MODEL) {
            if (triple.first.isAssignableFrom(this.getComponent().getClass())
             && triple.second.isAssignableFrom(value.getClass())) {
                try {
                    triple.third.invoke(this.getComponent(), value);
                } catch (Exception e) {
                    LOG.warn("exception when invoke {}/{}, detail: {}",
                            this.getComponent(), triple.third, ExceptionUtils.exception2detail(e));
                }
            }
        }
    }

    private void assignValueForInput(final Object value) {
        if (this.getComponent() instanceof InputElement) {
            ((InputElement)this.getComponent()).setText(getValueAsText(value));
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
            return null!=value ? value.toString() : null;
        }
    }
    
    private void bindCellAsListModel(final Object value) {
        if (null!=value && value instanceof ListModel ) {
            ((ListModel<?>)value).addListDataListener(this._listDataListener);
        }
    }

    private void bindCellForInput() {
        final PropertyEditor editor = PropertyEditorManager.findEditor(setterParameterType());
        if ( this.getComponent() instanceof InputElement) {
            final InputElement input = (InputElement)this.getComponent();
            input.addEventListener(Events.ON_CHANGE, new EventListener<InputEvent>() {
                @Override
                public void onEvent(final InputEvent event) throws Exception {
                    if (null!=editor) {
                        editor.setAsText(event.getValue());
                        setValueToCell(editor.getValue());
                    }
                }});
        }
    }

    private boolean isSetterValid() {
        return null!= this._setter && this._setter.getParameterTypes().length>0;
    }

    private Class<?> setterParameterType() {
        return (null!=this._setter && _setter.getParameterTypes().length>0) 
                ? this._setter.getParameterTypes()[0]
                : null;
    }

    private Object getValueFromCell() {
        Object value = null;
        try {
            if (null!=this._getter) {
                value = _getter.invoke(_bean);
            }
        } catch (Exception e) {
            LOG.warn("exception when invoke {}.{}, detail: {}", 
                    _bean, _getter, ExceptionUtils.exception2detail(e));
        }
        return value;
    }

    private void setValueToCell(final Object v) {
        try {
            if (null!=this._setter) {
                this._setter.invoke(_bean, v);
            }
        } catch (Exception e) {
            LOG.warn("exception when invoke {}.{}, detail:{}",
                    _bean, _setter, ExceptionUtils.exception2detail(e));
        }
    }
    
    Component render() {
        if ( null==this._cellsource 
          || this.getComponent() instanceof LabelElement) {
            return this.getComponent();
        } else {
            return new Hlayout() {
                private static final long serialVersionUID = 1L;
            {
                this.appendChild(new Label(_cellsource.name()));
                this.appendChild(getComponent());
            }};
        }
    }
    
    void setDisableStatus(final boolean disabled) {
        if (null!= this._setter && this.getComponent() instanceof Disable) {
            ((Disable)this.getComponent()).setDisabled(disabled);
        }
    }

    Component getComponent() {
        return this._component;
    }
}
