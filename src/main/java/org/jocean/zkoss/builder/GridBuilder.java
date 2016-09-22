package org.jocean.zkoss.builder;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.jocean.idiom.ExceptionUtils;
import org.jocean.idiom.ReflectUtils;
import org.jocean.zkoss.annotation.RowSource;
import org.jocean.zkoss.annotation.RowSource.DUMMY;
import org.jocean.zkoss.builder.impl.BeanGridRendererImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zkoss.zk.ui.Component;
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
    
    private static final Logger LOG = 
            LoggerFactory.getLogger(GridBuilder.class);
    
    public static void buildColumns(final Columns columns, final Class<?> cls) {
        final Field[] fields = ReflectUtils.getAnnotationFieldsOf(cls, RowSource.class);
        
        for (Field field : fields) {
            final RowSource ui = field.getAnnotation(RowSource.class);
            final Column col = new Column(ui.name());
            if (DUMMY.class != ui.asc()) {
                try {
                    col.setSortAscending((Comparator<?>)ui.asc().newInstance());
                } catch (Exception e) {
                    LOG.warn("exception when {}.setSortAscending using {}, detail: {}",
                            col, ui.asc(), ExceptionUtils.exception2detail(e));
                }
            }
            if (DUMMY.class != ui.dsc()) {
                try {
                    col.setSortDescending((Comparator<?>)ui.dsc().newInstance());
                } catch (Exception e) {
                    LOG.warn("exception when {}.setSortDescending using {}, detail: {}",
                            col, ui.dsc(), ExceptionUtils.exception2detail(e));
                }
            }
            columns.appendChild(col);
        }
    }
    
    public static <T> RowRenderer<T> buildRowRenderer(final Class<T> cls) {
        return new RowRenderer<T>() {
        @Override
        public void render(final Row row, final T data, int index)
                throws Exception {
            final Field[] fields = ReflectUtils.getAnnotationFieldsOf(cls, RowSource.class);
            for (Field field : fields) {
                final Object value = field.get(data);
                if (null!=value) {
                    row.setValue(value);
                    if (value instanceof Component) {
                        row.appendChild((Component)value);
                    } else {
                        row.appendChild(new Label(value.toString()));
                    }
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
    
    public static <T> BeanGridRenderer<T> buildBeanRowRenderer(final T bean, final String style) {
        return new BeanGridRendererImpl<T>(bean, style);
    }
}
