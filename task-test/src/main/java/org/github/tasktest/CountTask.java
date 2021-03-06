package org.github.tasktest;

import lombok.extern.slf4j.Slf4j;
import org.github.taskstarter.Task;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author zengchzh
 * @date 2021/12/29
 */

@Slf4j
//@Task(cron = "0/1 * * * * ? ")
@Component
public class CountTask {

    private static final AtomicInteger COUNT = new AtomicInteger(0);

    public void count() {
        log.info("" + COUNT.getAndIncrement());
    }

}
