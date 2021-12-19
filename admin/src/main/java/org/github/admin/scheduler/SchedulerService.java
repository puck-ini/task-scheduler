package org.github.admin.scheduler;

import lombok.extern.slf4j.Slf4j;
import org.github.admin.model.entity.Point;
import org.github.admin.model.task.LocalTask;
import org.github.admin.model.task.TimerTask;
import org.github.admin.service.TaskTriggerService;
import org.github.common.ServiceObject;
import org.github.common.ZkRegister;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;


import java.util.*;


/**
 * @author zengchzh
 * @date 2021/12/16
 */

@Slf4j
@Component
public class SchedulerService {

    @Autowired
    private TaskTriggerService taskTriggerService;

    @Autowired
    private ZkRegister zkRegister;

    private int size = 10;


    private Map<String, CheckTimeoutThread> threadMap = new HashMap<>();


    public void addCheckThread() {
        if (threadMap.values().size() < size) {
            TaskScheduler scheduler = new TaskScheduler();
            preGetTaskInfo(scheduler);
            CheckTimeoutThread timeoutThread = new CheckTimeoutThread(taskTriggerService, scheduler);
            timeoutThread.start();
            threadMap.put(timeoutThread.getName(), timeoutThread);
        }
    }

    private void preGetTaskInfo(TaskScheduler scheduler) {
        LocalTask task = new LocalTask(() -> {
            List<ServiceObject> soList = zkRegister.getAll();
            soList.forEach(so -> {
                Point point = new Point(so.getIp(), so.getPort());
                TaskInvocation invocation
                        = (TaskInvocation) scheduler.registerInvocation(point, new TaskInvocation(point, scheduler));
                invocation.preRead();
            });
        }, "0/30 * * * * ? ");
        scheduler.addTask(task);
    }

    public void addTask(TimerTask task) {
        CheckTimeoutThread timeoutThread = randomGet();
        timeoutThread.getScheduler().addTask(task);
    }


    private CheckTimeoutThread randomGet() {
        return (CheckTimeoutThread) threadMap.values().toArray()[new Random().nextInt(threadMap.values().size())];
    }


    public void stop() {
        Iterator<Map.Entry<String, CheckTimeoutThread>> iterator = threadMap.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, CheckTimeoutThread> entry = iterator.next();
            CheckTimeoutThread timeoutThread = entry.getValue();
            timeoutThread.toStop();
            iterator.remove();
        }
    }



}