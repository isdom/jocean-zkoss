package org.jocean.zkoss.model;

import java.beans.PropertyEditor;
import java.beans.PropertyEditorManager;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

import org.jocean.idiom.ExceptionUtils;
import org.jocean.idiom.Pair;
import org.jocean.idiom.ReflectUtils;
import org.jocean.zkoss.annotation.UIGrid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zkoss.zk.ui.Component;
import org.zkoss.zk.ui.event.EventListener;
import org.zkoss.zk.ui.event.Events;
import org.zkoss.zk.ui.event.InputEvent;
import org.zkoss.zul.Hbox;
import org.zkoss.zul.Label;
import org.zkoss.zul.Row;
import org.zkoss.zul.impl.InputElement;
import org.zkoss.zul.impl.LabelElement;

class BeanGridRendererImpl<T> implements BeanGridRenderer<T> {
    private static final Logger LOG = 
            LoggerFactory.getLogger(BeanGridRendererImpl.class);
    
    public BeanGridRendererImpl(final Class<T> cls) {
        final Field[] fields = ReflectUtils.getAnnotationFieldsOf(cls, UIGrid.class);
        this._cols = GridUtil.calcColCount(fields);
        for (Field field : fields) {
            final UIGrid ui = field.getAnnotation(UIGrid.class);
            this._components.put(Pair.of(ui.row(), ui.col()), 
                    Pair.of(field, buildComponent(field, ui)));
        }
    }
    
    private Component buildComponent(final Field field, final UIGrid ui) {
        final Component element = buildElement(field, ui);
        this._elements.put(field.getName(), element);
        if (element instanceof LabelElement) {
            ((LabelElement)element).setLabel(ui.name());
            return element;
        } else {
            if ("".equals(ui.name())) {
                return element;
            } else {
                return new Hbox() {
                    private static final long serialVersionUID = 1L;
                {
                    this.appendChild(new Label(ui.name()));
                    this.appendChild(element);
                }};
            }
        }
    }
    
    @Override
    public void render(final Row row, final T data, int rowidx)
            throws Exception {
        for (int col = 0; col < this._cols; col++) {
            row.appendChild(renderField(this._components.get(Pair.of(rowidx, col)), data));
        }
    }

    private Component renderField(final Pair<Field, Component> pair, final T bean) {
        if (null == pair) {
            return new Label("");
        } else {
            final Field field = pair.first;
            final Component element = this._elements.get(field.getName());
            if (null!=element) {
                attachFieldToElement(bean, field, element);
                try {
                    assignValueToElement(field.get(bean), element);
                } catch (Exception e) {
                    LOG.warn("exception when get value for {}, detail:{}",
                            field, ExceptionUtils.exception2detail(e));
                }
            }
            return pair.second;
        }
    }

    private void attachFieldToElement(final T bean, final Field field, final Component element) {
        if (element instanceof InputElement) {
            final InputElement input = (InputElement)element;
            input.addEventListener(Events.ON_CHANGE, new EventListener<InputEvent>() {
                @Override
                public void onEvent(final InputEvent event) throws Exception {
                    setTextToField(bean, field, event.getValue());
                }});
        }
    }

    private void setTextToField(
            final Object bean,
            final Field field, 
            final String text) throws Exception {
        final PropertyEditor editor = PropertyEditorManager.findEditor(field.getType());
        if (null!=editor) {
            editor.setAsText(text);
            field.set(bean, editor.getValue());
        }
    }

    private void assignValueToElement(final Object value, final Component element) {
        if (null!=value) {
            if (element instanceof InputElement) {
                ((InputElement)element).setText(value.toString());
            } else if (element instanceof LabelElement) {
                ((LabelElement)element).setLabel(value.toString());
            }
        }
    }

    private Component buildElement(final Field field, final UIGrid ui) {
        try {
            return ui.uitype().newInstance();
        } catch (Exception e) {
            LOG.warn("exception when newInstance for {}, detail:{}",
                    ui.uitype(), ExceptionUtils.exception2detail(e));
            return new Label(e.toString());
        }
    }
    
    @SuppressWarnings("unchecked")
    @Override
    public <C extends Component> C getComponent(final String name) {
        return (C)this._elements.get(name);
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
    
    private final Map<Pair<Integer,Integer>, Pair<Field, Component>> _components = new HashMap<>();
    private final Map<String, Component> _elements = new HashMap<>();
    private final int _cols;
}