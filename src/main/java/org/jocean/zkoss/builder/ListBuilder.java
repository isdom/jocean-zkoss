package org.jocean.zkoss.builder;

import java.lang.reflect.Field;
import java.util.Comparator;

import org.jocean.idiom.ExceptionUtils;
import org.jocean.idiom.ReflectUtils;
import org.jocean.zkoss.annotation.RowSource;
import org.jocean.zkoss.annotation.RowSource.DUMMY;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zkoss.zk.ui.Component;
import org.zkoss.zul.Listcell;
import org.zkoss.zul.Listhead;
import org.zkoss.zul.Listheader;
import org.zkoss.zul.Listitem;
import org.zkoss.zul.ListitemRenderer;

public class ListBuilder {
    
    private static final Logger LOG = 
            LoggerFactory.getLogger(ListBuilder.class);
    
    public static void buildHead(final Listhead head, final Class<?> cls) {
        final Field[] fields = ReflectUtils.getAnnotationFieldsOf(cls, RowSource.class);
        
        for (Field field : fields) {
            final RowSource ui = field.getAnnotation(RowSource.class);
            final Listheader header = new Listheader(ui.name());
            if (DUMMY.class != ui.asc()) {
                try {
                    header.setSortAscending((Comparator<?>)ui.asc().newInstance());
                } catch (Exception e) {
                    LOG.warn("exception when {}.setSortAscending using {}, detail: {}",
                            header, ui.asc(), ExceptionUtils.exception2detail(e));
                }
            }
            if (DUMMY.class != ui.dsc()) {
                try {
                    header.setSortDescending((Comparator<?>)ui.dsc().newInstance());
                } catch (Exception e) {
                    LOG.warn("exception when {}.setSortDescending using {}, detail: {}",
                            header, ui.dsc(), ExceptionUtils.exception2detail(e));
                }
            }
            head.appendChild(header);
        }
    }
    
    public static <T> ListitemRenderer<T> buildItemRenderer(final Class<T> cls) {
        return new ListitemRenderer<T>() {
        @Override
        public void render(final Listitem item, final T data, int index)
                throws Exception {
            final Field[] fields = ReflectUtils.getAnnotationFieldsOf(cls, RowSource.class);
            for (Field field : fields) {
                final Object value = field.get(data);
                if (null!=value) {
                    if (value instanceof Component) {
                        if (value instanceof Listcell) {
                            item.appendChild((Component)value);
                        } else {
                            item.appendChild(new Listcell() {
                                private static final long serialVersionUID = 1L;
                                {
                                    this.appendChild((Component)value);
                                }});
                        }
                    } else {
                        item.appendChild(new Listcell(value.toString()));
                    }
                } else {
                    item.appendChild(new Listcell("<null>"));
                }
            }
        }};
    }
}
