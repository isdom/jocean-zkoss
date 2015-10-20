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
public @interface UIGrid {
	public abstract String name();
    public abstract int row();
    public abstract int col();
    public abstract Class<? extends InputElement> inputType() default InputElement.class;
}
