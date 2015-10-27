package org.jocean.zkoss.builder;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import org.jocean.idiom.ReflectUtils;
import org.jocean.zkoss.annotation.UIRow;
import org.jocean.zkoss.builder.impl.BeanGridRendererImpl;
import org.zkoss.zul.AbstractListModel;
import org.zkoss.zul.Column;
import org.zkoss.zul.Columns;
import org.zkoss.zul.Label;
import org.zkoss.zul.ListModel;
import org.zkoss.zul.Row;
import org.zkoss.zul.RowRenderer;

import rx.functions.Func0;
import rx.functions.Func2;

public class GridBuilder {
    
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

    public static <T> BeanGridRenderer<T> buildBeanRowRenderer(final T bean) {
        return new BeanGridRendererImpl<T>(bean);
    }
}
