package com.lsh.demo.controller;

import com.lsh.demo.service.DemoService;
import com.lsh.demo.service.IDemoService;
import com.lsh.mySpring.annotation.GPAutowired;
import com.lsh.mySpring.annotation.GPController;
import com.lsh.mySpring.annotation.GPRequestMapping;
import com.lsh.mySpring.annotation.GPRequestParam;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Created by houbank on 2019/2/20.
 */
@GPController
@GPRequestMapping("/demo")
public class DemoController {

    @GPAutowired
    private IDemoService demoService;

    @GPRequestMapping("/query.json")
    public void query(HttpServletRequest request, HttpServletResponse response, @GPRequestParam String name){
        try {
            String result = demoService.getName(name);
            response.getWriter().write(result);
        }catch (Exception e){
            e.printStackTrace();
        }

    }
}
