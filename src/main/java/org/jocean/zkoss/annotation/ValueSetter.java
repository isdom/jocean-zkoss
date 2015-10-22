/**
 * 
 */
package org.jocean.zkoss.annotation;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * @author isdom
 *
 */
@Retention(RetentionPolicy.RUNTIME) 
public @interface ValueSetter {
	public abstract String name();
}
