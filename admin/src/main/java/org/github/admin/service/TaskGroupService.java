package org.github.admin.service;

import org.github.admin.model.entity.TaskGroup;
import org.github.common.req.CreateGroupReq;
import org.github.common.req.TaskAppInfo;
import org.springframework.data.domain.Page;

/**
 * @author zengchzh
 * @date 2021/12/10
 */
public interface TaskGroupService {

    Page<TaskGroup> list();

    void createGroup(CreateGroupReq req);

    void addGroup(TaskAppInfo info);
}
