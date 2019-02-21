package com.lsh.mySpring.annotation;

import java.lang.annotation.*;

/**
 * Created by houbank on 2019/2/20.
 */
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface GPAutowired {
    
    String value() default "";
}
