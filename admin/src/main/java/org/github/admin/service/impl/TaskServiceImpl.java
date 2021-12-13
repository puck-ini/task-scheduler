package org.github.admin.service.impl;

import org.github.admin.entity.TaskInfo;
import org.github.admin.entity.TaskGroup;
import org.github.admin.repo.TaskGroupRepo;
import org.github.admin.repo.TaskInfoRepo;
import org.github.admin.req.AddTaskInfoReq;
import org.github.admin.service.TaskService;
import org.github.common.TaskDesc;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.function.Consumer;

/**
 * @author zengchzh
 * @date 2021/12/10
 */
@Service
public class TaskServiceImpl implements TaskService {

    @Autowired
    private TaskInfoRepo taskInfoRepo;

    @Autowired
    private TaskGroupRepo taskGroupRepo;

    @Override
    public Page<TaskInfo> list() {
        return taskInfoRepo.findAll(PageRequest.of(0, 10));
    }

    @Override
    public void addTask(AddTaskInfoReq req) {
        taskGroupRepo.findById(req.getTaskGroupId()).ifPresent(new Consumer<TaskGroup>() {
            @Override
            public void accept(TaskGroup taskGroup) {
                TaskInfo taskInfo = new TaskInfo();
                TaskDesc taskDesc = taskInfo.getTaskDesc();
                taskDesc.setTaskName(req.getTaskName());
                taskDesc.setClassName(req.getClassName());
                taskDesc.setMethodName(req.getMethodName());
                taskDesc.setParameterTypes(req.getParameterTypes());
                taskInfo.setTaskGroup(taskGroup);
                taskGroup.getTaskInfoList().add(taskInfo);
                taskGroupRepo.save(taskGroup);
            }
        });
    }
}