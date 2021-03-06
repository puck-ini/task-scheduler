package org.github.admin;

import org.github.common.register.ZkRegister;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class AdminApplication {

    public static void main(String[] args) {
        SpringApplication.run(AdminApplication.class, args);
    }

    @Value("${zk.address:127.0.0.1:2181}")
    private String zkAddress;

    @Bean
    @ConditionalOnProperty(prefix = "zk", name = "enable", havingValue = "true")
    public ZkRegister zkRegister() {
        return new ZkRegister(zkAddress);
    }

}
