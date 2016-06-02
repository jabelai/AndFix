package com.alipay.euler.andfix.hook;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Hook Method Backup Annotation
 * Created by jabe on 6/1/16.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Hook {
    String value() default "";
}
