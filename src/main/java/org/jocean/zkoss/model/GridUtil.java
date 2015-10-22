package org.jocean.zkoss.model;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import org.jocean.idiom.ReflectUtils;
import org.jocean.zkoss.annotation.UIRow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zkoss.zul.AbstractListModel;
import org.zkoss.zul.Column;
import org.zkoss.zul.Columns;
import org.zkoss.zul.Label;
import org.zkoss.zul.ListModel;
import org.zkoss.zul.Row;
import org.zkoss.zul.RowRenderer;

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

    public static <T> BeanGridRenderer<T> buildBeanRowRenderer(final Class<T> cls) {
        return new BeanGridRendererImpl2<T>(cls);
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
}
