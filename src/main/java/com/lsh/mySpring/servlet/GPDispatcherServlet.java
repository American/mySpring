package com.lsh.mySpring.servlet;

import com.lsh.mySpring.annotation.*;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by houbank on 2019/2/20.
 */
public class GPDispatcherServlet extends HttpServlet {

    private Properties properties = new Properties();

    private List<String> classNames = new ArrayList<String>();

    private Map<String,Object> ioc = new ConcurrentHashMap<String, Object>();

    //private Map<String,Method> handerMapping = new ConcurrentHashMap<String, Method>();
    private List<Hander> handerMapping = new ArrayList<Hander>();
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

        doPost(req,resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try {
            this.doDispatch(req,resp);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void init(ServletConfig config) throws ServletException {
        System.out.println("%%%%%%%%%%%%init%%%%%%%%%%%%%%");

        //加载 配置文件
        doLoadConfig(config.getInitParameter("contextConfigLocation"));
        //扫描所有的类
        doScaner(properties.getProperty("scanPackage"));
        //初始化所有的类的实例，并且将其放到ioc容器map中
        doInstanse();
        //自动实现依赖注入
        doAutowired();
        //初始化HandlerMapping
        doInitHanderMapping();
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
        if(ioc.isEmpty()){return;}

        for(Map.Entry<String,Object> entry :ioc.entrySet()){
            //获取所有的字段
            //private public protected都注入
            Field[] fields = entry.getValue().getClass().getDeclaredFields();

            for(Field field :fields){
                if(!field.isAnnotationPresent(GPAutowired.class)){continue;}
                GPAutowired autowired = field.getAnnotation(GPAutowired.class);
                String beanName = autowired.value().trim();
                if("".equals(beanName)){
                    beanName = field.getType().getName();
                }
                //访问私有方法，需要授权
                field.setAccessible(true);

                try {
                    field.set(entry.getValue(),ioc.get(beanName));
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                    continue;
                }


            }
        }

    }

    private void doInitHanderMapping(){
        if(ioc.isEmpty()){return;}

        for(Map.Entry<String,Object> entry:ioc.entrySet()){
            Class<?> clazz = entry.getValue().getClass();
            if(!clazz.isAnnotationPresent(GPController.class)){continue;}
            String baseUrl = "";
            if(clazz.isAnnotationPresent(GPRequestMapping.class)){
                GPRequestMapping requestMapping = clazz.getAnnotation(GPRequestMapping.class);
                baseUrl = requestMapping.value();
            }

            /*Method[] methods = clazz.getMethods();
            for(Method method : methods){
                if(!method.isAnnotationPresent(GPRequestMapping.class)){continue;}
                GPRequestMapping requestMapping = method.getAnnotation(GPRequestMapping.class);
                String url = (baseUrl+requestMapping.value()).replaceAll("/+","/");
                handerMapping.put(url,method);
                System.out.println("%%%%%%%%%%%%Mapping init ok%%%%%%%%%%%%%%%" + url+","+method);

            }*/

            Method[] methods = clazz.getMethods();
            for(Method method : methods){
                if(!method.isAnnotationPresent(GPRequestMapping.class)){continue;}
                GPRequestMapping requestMapping = method.getAnnotation(GPRequestMapping.class);
                String regex = ("/" + baseUrl+requestMapping.value()).replaceAll("/+","/");
                Pattern pattern = Pattern.compile(regex);
                handerMapping.add(new Hander(pattern,entry.getValue(),method));
                System.out.println("mapping %%%%%%%%"+regex+","+method);
            }

        }

    }

    private void doDispatch(HttpServletRequest request,HttpServletResponse response) throws Exception{
        try {
            Hander hander = getHander(request);
            if(hander == null){
                response.getWriter().write("404!");
                return;
            }

            //获取方法的参数列表

            Class<?> [] paramTypes = hander.method.getParameterTypes();

            //保存所有需要自动赋值的参数值
            Object[] paramValues = new Object[paramTypes.length];

            Map<String,String[]> params = request.getParameterMap();

            for(Map.Entry<String,String[]> param:params.entrySet()){
                String value = Arrays.toString(param.getValue()).replaceAll("\\[|\\]","");
                //填充数据
                if(!hander.paramIndexMapping.containsKey(param.getKey())){continue;}
                int index = hander.paramIndexMapping.get(param.getKey());
                paramValues[index]  = convert(paramTypes[index],value);
            }

            //设置request response
            int reqIndex = hander.paramIndexMapping.get(HttpServletRequest.class.getName());
            paramValues[reqIndex]  = request;
            int repIndex = hander.paramIndexMapping.get(HttpServletResponse.class.getName());
            paramValues[repIndex] = response;

            hander.method.invoke(hander.controller,paramValues);
        }catch (Exception e){
            e.printStackTrace();
        }
    }


    private Object convert(Class<?> type,String value){
        if(Integer.class == type){
            return Integer.valueOf(value);
        }
        return value;
    }

    private Hander getHander(HttpServletRequest request) throws Exception{
        if(handerMapping.isEmpty()){return null;}
        String url = request.getRequestURI();
        String contextPath = request.getContextPath();
        url = url.replace(contextPath,"").replaceAll("/+","/");
        for(Hander hander:handerMapping){
            try {
                Matcher matcher = hander.pattern.matcher(url);
                if(!matcher.matches()){continue;}
                return hander;
            }catch (Exception e){
                throw  e;
            }
        }

        return null;
    }
    private String lowerFirstCase(String str){
        char[] chars = str.toCharArray();
        chars[0] +=32;
        return String.valueOf(chars);
    }

    private class Hander{
        protected Object controller;//保存方法对象的实例
        protected Method method; //
        protected Pattern pattern;
        protected Map<String,Integer> paramIndexMapping;//参数顺序

        protected Hander(Pattern pattern,Object controller,Method method){
            this.pattern=pattern;
            this.controller=controller;
            this.method = method;
            paramIndexMapping = new HashMap<String, Integer>();
            putParamIndexMapping(method);
        }


        private void putParamIndexMapping(Method method){

            Annotation[] [] pa = method.getParameterAnnotations();
            for(int i=0;i<pa.length;i++){
                for(Annotation a :pa[i]){
                    if(a instanceof GPRequestParam){
                        String paramName = ((GPRequestParam) a).value();
                        if(!"".equals(paramName.trim())){
                            paramIndexMapping.put(paramName,i);
                        }
                    }
                }
            }

            //提取request response
            Class<?> [] paramTypes = method.getParameterTypes();
            for(int i=0;i<paramTypes.length;i++){
                Class<?> type = paramTypes[i];
                if((type == HttpServletRequest.class) || type == HttpServletResponse.class){
                    paramIndexMapping.put(type.getName(),i);
                }
            }


        }


    }
}
