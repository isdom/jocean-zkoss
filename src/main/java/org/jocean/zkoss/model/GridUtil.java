package org.jocean.zkoss.model;

import java.lang.reflect.Field;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.jocean.idiom.ReflectUtils;
import org.jocean.zkoss.annotation.Cell;
import org.zkoss.zk.ui.Component;
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

import rx.functions.Func0;
import rx.functions.Func2;

public class GridUtil {
    public static void buildColumns(final Columns columns, final Class<?> cls) {
        final Field[] fields = ReflectUtils.getAnnotationFieldsOf(cls, Cell.class);
        
        for (Field field : fields) {
            final Cell cell = field.getAnnotation(Cell.class);
            columns.appendChild(new Column(cell.value()));
        }
    }
    
    public static <T> RowRenderer<T> buildRowRenderer(final Class<T> cls) {
        return new RowRenderer<T>() {
        @Override
        public void render(final Row row, final T data, int index)
                throws Exception {
            final Field[] fields = ReflectUtils.getAnnotationFieldsOf(cls, Cell.class);
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
        final Field[] fields = ReflectUtils.getAnnotationFieldsOf(bean.getClass(), Cell.class);
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
            final Cell cell = field.getAnnotation(Cell.class);
            if (null!=cell) {
                if (cell.row() > rows) {
                    rows = cell.row();
                }
            }
        }
        return rows + 1;
    }
    
    private static int calcColCount(final Field[] fields) {
        int cols = -1;
        for (Field field : fields) {
            final Cell cell = field.getAnnotation(Cell.class);
            if (null!=cell) {
                if (cell.col() > cols) {
                    cols = cell.col();
                }
            }
        }
        return cols + 1;
    }
    
    public static <T> RowRenderer<T> buildBeanRowRenderer(final Class<T> cls,
            final Map<String, Func0<Object>> models) {
        return new RowRenderer<T>() {
        @Override
        public void render(final Row row, final T data, int index)
                throws Exception {
            final Field[] fields = buildFieldsOfRow(cls, index);
            for (Field f : fields) {
                final Field field = f;
                if (null!=field) {
                    final Cell cell = field.getAnnotation(Cell.class);
                    if (null!=cell) {
                        final Object value = field.get(data);
                        row.appendChild(new Hbox() {
                            private static final long serialVersionUID = 1L;
                        {
                            this.appendChild(new Label(cell.value()));
                            if (null!=value) {
                                this.appendChild(buildInput(models, field, cell, value));
                            } else {
                                this.appendChild(new Label("<null>"));
                            }
                        }});
                    } else {
                        row.appendChild(new Label("<unknown field>"));
                    }
                } else {
                    row.appendChild(new Label(""));
                }
            }
        }};
    }

    private static Field[] buildFieldsOfRow(final Class<?> cls, final int row) {
        final Field[] fields = ReflectUtils.getAnnotationFieldsOf(cls, Cell.class);
        final int colCount = calcColCount(fields);
        final Field[] rowfields = new Field[colCount];
        for (Field field : fields) {
            final Cell cell = field.getAnnotation(Cell.class);
            if (null != cell) {
                if ( row == cell.row() ) {
                    rowfields[cell.col()] = field;
                }
            }
        }
        
        return rowfields;
    }

    private static Component buildInput(final Map<String, Func0<Object>> models, final Field field, final Cell cell,final Object value) 
            throws Exception {
        if (!cell.inputType().equals(InputElement.class)) {
            if (cell.inputType().equals(Combobox.class)) {
                final Combobox combobox = new Combobox();
                final Func0<Object> func = models.get(field.getName());
                if (null!=func) {
                    combobox.setModel((ListModel<?>) func.call());
                }
                return combobox;
            } else {
                return cell.inputType().newInstance();
            }
        }
        
        if (field.getType().equals(Timestamp.class)) {
            return new Datebox() {
                private static final long serialVersionUID = 1L;
            {
                this.setText(value.toString());
            }};
        } else {
            return new Textbox() {
                private static final long serialVersionUID = 1L;
            {
                this.setText(value.toString());
            }};
        }
    }
}
