/**
 * 
 */
package org.jocean.zkoss.annotation;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import org.zkoss.zul.impl.InputElement;

/**
 * @author isdom
 *
 */
@Retention(RetentionPolicy.RUNTIME) 
public @interface Cell {
	public abstract String value();
    public abstract int index() default -1;
    public abstract int row() default -1;
    public abstract int col() default -1;
    public abstract Class<? extends InputElement> inputType() default InputElement.class;
}
