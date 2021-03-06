package org.github.taskstarter;

import com.alibaba.fastjson.JSON;
import org.github.common.register.ServiceObject;
import org.github.common.register.ZkRegister;
import org.github.common.req.TaskAppInfo;
import org.github.common.req.TaskMethod;
import org.github.common.types.TaskDesc;
import org.github.common.util.ServerUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author zengchzh
 * @date 2021/12/11
 *
 */

public class TaskInfoHolder implements ApplicationListener<ContextRefreshedEvent> {

    @Autowired
    private TaskProp taskProp;

    @Autowired(required = false)
    private ZkRegister zkRegister;

    @Value("${spring.application.name:null}")
    private String appName;

    private ApplicationContext context;

    private static TaskAppInfo taskAppInfo;

    private static final Map<String, Object> CACHE = new ConcurrentHashMap<>();

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        if (Objects.isNull(event.getApplicationContext().getParent())) {
            context = event.getApplicationContext();
            init();
            registerGroup();
        }
    }

    private void init() {
        String[] names = context.getBeanDefinitionNames();
        for (String name : names) {
            Class<?> clazz = context.getType(name);
            if (Objects.isNull(clazz)) {
                continue;
            }
            Task task = clazz.getAnnotation(Task.class);
            Object obj = context.getBean(name);
            if (Objects.nonNull(task)) {
                CACHE.put(clazz.getName(), obj);
            } else {
                Method[] methods = clazz.getMethods();
                for (Method method : methods) {
                    Task task1 = method.getAnnotation(Task.class);
                    if (Objects.nonNull(task1)) {
                        CACHE.put(clazz.getName(), obj);
                    }
                }
            }

        }
    }

    private void registerGroup() {
        String[] names = context.getBeanDefinitionNames();
        TaskAppInfo taskAppInfo = TaskAppInfo.builder()
                .appName(appName)
                .ip(ServerUtil.getHost())
                .port(taskProp.getPort())
                .build();
        List<TaskMethod> taskMethodList = new ArrayList<>();
        for (String name : names) {
            Class<?> clazz = context.getType(name);
            if (Objects.isNull(clazz)) {
                continue;
            }
            Task task = clazz.getAnnotation(Task.class);
            Method[] methods = clazz.getDeclaredMethods();
            String className = clazz.getName();
            if (Objects.nonNull(task)) {
                for (Method method : methods) {
                    if (!method.isBridge()) {
                        TaskMethod taskMethod = new TaskMethod();
                        taskMethodList.add(taskMethod);
                        taskMethod.setTaskDesc(TaskDesc.builder()
                                .taskName(method.getName())
                                .className(className)
                                .methodName(method.getName())
                                .parameterTypes(JSON.toJSONString(method.getParameterTypes()))
                                .build());
                        if (!StringUtils.isEmpty(task.cron())) {
                            taskMethod.setCron(task.cron());
                        }
                    }
                }
            } else {
                for (Method method : methods) {
                    Task task1 = method.getAnnotation(Task.class);
                    if (Objects.nonNull(task1)) {
                        TaskMethod taskMethod = new TaskMethod();
                        taskMethodList.add(taskMethod);
                        taskMethod.setTaskDesc(TaskDesc.builder()
                                .taskName(StringUtils.isEmpty(task1.taskName()) ? method.getName() : task1.taskName())
                                .className(className)
                                .methodName(method.getName())
                                .parameterTypes(JSON.toJSONString(method.getParameterTypes()))
                                .build());
                        if (!StringUtils.isEmpty(task1.cron())) {
                            taskMethod.setCron(task1.cron());
                        }
                    }
                }
            }
        }
        taskAppInfo.setTaskMethodList(taskMethodList);
        TaskInfoHolder.taskAppInfo = taskAppInfo;
        if (taskMethodList.size() != 0 && taskProp.isZkEnable()) {
            zkRegister.register(ServiceObject.builder()
                    .groupName(taskAppInfo.getAppName())
                    .ip(taskAppInfo.getIp())
                    .port(taskAppInfo.getPort())
                    .build());
        }
    }

    public static Object get(String key) {
        return CACHE.get(key);
    }

    public static TaskAppInfo getTaskInfo() {
        return TaskInfoHolder.taskAppInfo;
    }

}
