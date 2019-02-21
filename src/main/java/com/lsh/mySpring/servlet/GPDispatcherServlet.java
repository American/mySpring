package com.lsh.mySpring.servlet;

import com.lsh.mySpring.annotation.GPController;
import com.lsh.mySpring.annotation.GPService;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by houbank on 2019/2/20.
 */
public class GPDispatcherServlet extends HttpServlet {

    private Properties properties = new Properties();

    private List<String> classNames = new ArrayList<String>();

    private Map<String,Object> ioc = new ConcurrentHashMap<String, Object>();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {


    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

    }

    @Override
    public void init(ServletConfig config) throws ServletException {
        System.out.println("%%%%%%%%%%%%init%%%%%%%%%%%%%%");

        //加载 配置文件
        doLoadConfig(config.getInitParameter("contextConfigLocation"));
        //扫描所有的类
        doScaner(properties.getProperty("scanPackage"));
        //初始化所有的类的实例，并且将其放到ioc容器map中

        //自动实现依赖注入

        //初始化HandlerMapping

        //等待，处理请求


    }

    private void doLoadConfig(String location){
        InputStream is = this.getClass().getClassLoader().getResourceAsStream(location);
        try {
            properties.load(is);
        } catch (IOException e) {
            e.printStackTrace();
        }finally {
            if(null != is){
                try {
                    is.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void doScaner(String packageName){
        //递归扫描
        URL url = this.getClass().getClassLoader().getResource("/"+packageName.replaceAll("\\.","/"));
        File classDir = new File(url.getFile());
        for(File file : classDir.listFiles()){
            if(file.isDirectory()){
                doScaner(packageName+"."+file.getName());
            }else {
                String className = packageName+"."+file.getName().replaceAll(".class","");
                classNames.add(className);
            }
        }
    }

    private void doInstanse(){
        if(classNames.isEmpty()){
            return;
        }
        try {
            //反射初始化类
            for(String className :classNames){
                Class<?> clazz = Class.forName(className);
                //实例化bean 初始化ioc容器
                //只 初始化注解的
                if(clazz.isAnnotationPresent(GPController.class)){
                    String beanName =this.lowerFirstCase(clazz.getSimpleName());
                    ioc.put(beanName,clazz.newInstance());
                }else if(clazz.isAnnotationPresent(GPService.class)){
                    GPService gpService = clazz.getAnnotation(GPService.class);
                    String beanName = gpService.value();
                    if("".equals(beanName)){
                        beanName = this.lowerFirstCase(clazz.getSimpleName());
                    }
                    Object instance = clazz.newInstance();
                    ioc.put(beanName,instance);
                    //如果有接口，
                    Class<?>[] interfaces = clazz.getInterfaces();
                    for (Class<?> i :interfaces){
                        ioc.put(i.getName(),instance);
                    }

                }else {
                    continue;
                }
            }
        }catch (Exception e){
            e.printStackTrace();
        }

    }

    private void doAutowired(){

    }

    private void doInitHanderMapping(){

    }

    private String lowerFirstCase(String str){
        char[] chars = str.toCharArray();
        chars[0] +=32;
        return String.valueOf(chars);
    }
}
