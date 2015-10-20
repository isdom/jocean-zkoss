package org.jocean.zkoss.model;

import java.beans.PropertyEditor;
import java.beans.PropertyEditorManager;
import java.lang.reflect.Field;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jocean.idiom.ExceptionUtils;
import org.jocean.idiom.Pair;
import org.jocean.idiom.ReflectUtils;
import org.jocean.zkoss.annotation.UIGrid;
import org.jocean.zkoss.annotation.UIRow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zkoss.zk.ui.Component;
import org.zkoss.zk.ui.event.Event;
import org.zkoss.zk.ui.event.EventListener;
import org.zkoss.zk.ui.event.Events;
import org.zkoss.zk.ui.event.InputEvent;
import org.zkoss.zul.AbstractListModel;
import org.zkoss.zul.Column;
import org.zkoss.zul.Columns;
import org.zkoss.zul.Combobox;
import org.zkoss.zul.Datebox;
import org.zkoss.zul.Hbox;
import org.zkoss.zul.Label;
import org.zkoss.zul.ListModel;
import org.zkoss.zul.Row;
import org.zkoss.zul.RowRenderer;
import org.zkoss.zul.Textbox;
import org.zkoss.zul.impl.InputElement;
import org.zkoss.zul.impl.LabelElement;

import rx.functions.Func0;
import rx.functions.Func2;

public class GridUtil {
    
    private static final Logger LOG = 
            LoggerFactory.getLogger(GridUtil.class);
    
    public static void buildColumns(final Columns columns, final Class<?> cls) {
        final Field[] fields = ReflectUtils.getAnnotationFieldsOf(cls, UIRow.class);
        
        for (Field field : fields) {
            final UIRow ui = field.getAnnotation(UIRow.class);
            columns.appendChild(new Column(ui.name()));
        }
    }
    
    public static <T> RowRenderer<T> buildRowRenderer(final Class<T> cls) {
        return new RowRenderer<T>() {
        @Override
        public void render(final Row row, final T data, int index)
                throws Exception {
            final Field[] fields = ReflectUtils.getAnnotationFieldsOf(cls, UIRow.class);
            for (Field field : fields) {
                final Object value = field.get(data);
                if (null!=value) {
                    row.appendChild(new Label(value.toString()));
                } else {
                    row.appendChild(new Label("<null>"));
                }
            }
        }};
    }
    
    public static <T> ListModel<T> buildListModel(
            final Class<T> cls,
            final int countPerPage,
            final Func2<Integer, Integer, List<T>> fetchPage,
            final Func0<Integer> fetchTotalSize
            ) {
        return new AbstractListModel<T>() {
            private final List<T> _cache = new ArrayList<>();
            private int _currentOffset = -countPerPage;
            private int _totalSize = -1;
            private static final long serialVersionUID = 1L;

            @Override
            public T getElementAt(final int index) {
                if ((index < _currentOffset)
                  || (index >= _currentOffset + countPerPage)) {
                    _currentOffset = (index / countPerPage) * countPerPage;
                    _cache.clear();
                    _cache.addAll(fetchPage.call(_currentOffset, countPerPage));
                }
                return _cache.get(index % countPerPage);
            }

            @Override
            public int getSize() {
                if (_totalSize < 0) {
                    _totalSize = fetchTotalSize.call();
                }
                return _totalSize;
            }};
    }

    public static <T> ListModel<T> buildBeanListModel(final T bean) {
        final Field[] fields = ReflectUtils.getAnnotationFieldsOf(bean.getClass(), UIGrid.class);
        final int rowCount = calcRowCount(fields);
        
        return new AbstractListModel<T>() {
            private static final long serialVersionUID = 1L;

            @Override
            public T getElementAt(final int index) {
                return bean;
            }

            @Override
            public int getSize() {
                return rowCount;
            }};
    }

    private static int calcRowCount(final Field[] fields) {
        int rows = -1;
        for (Field field : fields) {
            final UIGrid ui = field.getAnnotation(UIGrid.class);
            if (null!=ui) {
                if (ui.row() > rows) {
                    rows = ui.row();
                }
            }
        }
        return rows + 1;
    }
    
    private static int calcColCount(final Field[] fields) {
        int cols = -1;
        for (Field field : fields) {
            final UIGrid ui = field.getAnnotation(UIGrid.class);
            if (null!=ui) {
                if (ui.col() > cols) {
                    cols = ui.col();
                }
            }
        }
        return cols + 1;
    }
    
    public static <T> RowRenderer<T> buildBeanRowRenderer(final Class<T> cls) {
        class RowRender implements RowRenderer<T>, ComponentHub {
            
            RowRender() {
                final Field[] fields = ReflectUtils.getAnnotationFieldsOf(cls, UIGrid.class);
                this._cols = calcColCount(fields);
                for (Field field : fields) {
                    final UIGrid ui = field.getAnnotation(UIGrid.class);
                    this._components.put(Pair.of(ui.row(), ui.col()), 
                            Pair.of(field, buildComponent(field, ui)));
                }
            }
            
            private Component buildComponent(final Field field, final UIGrid ui) {
                return new Hbox() {
                    private static final long serialVersionUID = 1L;
                {
                    if (!"".equals(ui.name())) {
                        this.appendChild(new Label(ui.name()));
                    }
                    final Component element = buildElement(field, ui);
                    _elements.put(field.getName(), element);
                    this.appendChild(element);
                }};
            }
            @Override
            public void render(final Row row, final T data, int rowidx)
                    throws Exception {
                for (int col = 0; col < this._cols; col++) {
                    row.appendChild(renderField(this._components.get(Pair.of(rowidx, col)), data));
                }
            }
    
            private Component renderField(final Pair<Field, Component> pair,final T data) {
                if (null == pair) {
                    return new Label("");
                } else {
                    final Field field = pair.first;
                    final Component element = this._elements.get(field.getName());
                    if (null!=element) {
                        attachFieldToElement(data, field, element);
                        try {
                            final Object value = field.get(data);
                            if (null!=value) {
                                assignValueToElement(value, element);
                            }
                        } catch (Exception e) {
                            LOG.warn("exception when get value for {}, detail:{}",
                                    field, ExceptionUtils.exception2detail(e));
                        }
                    }
                    return pair.second;
                }
            }

            private void attachFieldToElement(final T bean, final Field field, final Component element) {
                if (element instanceof Datebox) {
                    final Datebox box = (Datebox)element;
                    box.addEventListener(Events.ON_CHANGE, new EventListener<InputEvent>() {
                        @Override
                        public void onEvent(final InputEvent event) throws Exception {
                            setTextToField(bean, field, event.getValue());
                        }});
                } else if (element instanceof Textbox) {
                    final Textbox box = (Textbox)element;
                    box.addEventListener(Events.ON_CHANGE, new EventListener<InputEvent>() {
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
                if (element instanceof InputElement) {
                    ((InputElement)element).setText(value.toString());
                } else if (element instanceof LabelElement) {
                    ((LabelElement)element).setLabel(value.toString());
                }
            }

            @SuppressWarnings("unchecked")
            @Override
            public <C extends Component> C getComponent(final String name) {
                return (C)this._elements.get(name);
            }
            
            private final Map<Pair<Integer,Integer>, Pair<Field, Component>> _components = new HashMap<>();
            private final Map<String, Component> _elements = new HashMap<>();
            private final int _cols;
        }
        
        return new RowRender();
    }

    private static Component buildElement(final Field field, final UIGrid ui) {
        if (!ui.uitype().equals(Component.class)) {
            if (ui.uitype().equals(Combobox.class)) {
                final Combobox combobox = new Combobox();
                combobox.addEventListener(Events.ON_SELECT, NOP_EVENTLISTENER);
                return combobox;
            } else {
                try {
                    return ui.uitype().newInstance();
                } catch (Exception e) {
                    LOG.warn("exception when newInstance for {}, detail:{}",
                            ui.uitype(), ExceptionUtils.exception2detail(e));
                }
            }
        }
        
        if (field.getType().equals(Timestamp.class)
           || field.getType().equals(Date.class)) {
            return new Datebox();
        } else {
            return new Textbox();
        }
    }
    
//    @SuppressWarnings("unused")
//    private static Field[] buildFieldsOfRow(final Class<?> cls, final int row) {
//        final Field[] fields = ReflectUtils.getAnnotationFieldsOf(cls, Cell.class);
//        final int colCount = calcColCount(fields);
//        final Field[] rowfields = new Field[colCount];
//        for (Field field : fields) {
//            final Cell cell = field.getAnnotation(Cell.class);
//            if (null != cell) {
//                if ( row == cell.row() ) {
//                    rowfields[cell.col()] = field;
//                }
//            }
//        }
//        
//        return rowfields;
//    }
    
    private static final EventListener<Event> NOP_EVENTLISTENER = new EventListener<Event>() {
        @Override
        public void onEvent(Event event)
                throws Exception {
        }};
}
