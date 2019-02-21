package com.lsh.demo.service;

import com.lsh.mySpring.annotation.GPService;

/**
 * Created by houbank on 2019/2/20.
 */
@GPService
public class DemoService {

    public String getName(String name){
        return "mySpring:"+name;
    }
}
