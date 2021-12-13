package org.github.admin.service;

import org.github.admin.entity.TaskInfo;
import org.github.admin.req.AddTaskInfoReq;
import org.springframework.data.domain.Page;

/**
 * @author zengchzh
 * @date 2021/12/10
 */
public interface TaskService {

    Page<TaskInfo> list();

    void addTask(AddTaskInfoReq req);

}