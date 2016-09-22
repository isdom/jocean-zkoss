/**
 * 
 */
package org.jocean.zkoss.annotation;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Comparator;

import org.zkoss.zul.Row;

/**
 * @author isdom
 *
 */
@Retention(RetentionPolicy.RUNTIME) 
public @interface RowSource {
    public static class DUMMY implements Comparator<Row> {
        @Override
        public int compare(Row o1, Row o2) {
            return 0;
        }
    }
	public abstract String name();
    public abstract int index() default -1;
    public abstract Class<? extends Comparator<Row>> asc() default DUMMY.class;
    public abstract Class<? extends Comparator<Row>> dsc() default DUMMY.class;
}
