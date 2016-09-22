/**
 * 
 */
package org.jocean.zkoss.annotation;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Comparator;

/**
 * @author isdom
 *
 */
@Retention(RetentionPolicy.RUNTIME) 
public @interface RowSource {
    public static class DUMMY implements Comparator<Object> {
        @Override
        public int compare(Object o1, Object o2) {
            return 0;
        }
    }
	public abstract String name();
    public abstract int index() default -1;
    public abstract Class<? extends Comparator<?>> asc() default DUMMY.class;
    public abstract Class<? extends Comparator<?>> dsc() default DUMMY.class;
}
