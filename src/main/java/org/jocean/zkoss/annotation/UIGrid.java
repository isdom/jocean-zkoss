/**
 * 
 */
package org.jocean.zkoss.annotation;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import org.zkoss.zk.ui.Component;
import org.zkoss.zul.Textbox;

/**
 * @author isdom
 *
 */
@Retention(RetentionPolicy.RUNTIME) 
public @interface UIGrid {
	public abstract String name() default "";
    public abstract int row();
    public abstract int col();
    public abstract Class<? extends Component> uitype() default Textbox.class;
}
