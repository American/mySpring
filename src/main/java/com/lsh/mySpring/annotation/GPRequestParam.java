package com.lsh.mySpring.annotation;

import java.lang.annotation.*;

/**
 * Created by houbank on 2019/2/20.
 */
@Target({ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface GPRequestParam {
    
    String value() default "";
}
