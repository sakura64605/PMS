package com.hongjie.pms.registry;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
@SpringBootApplication
@org.springframework.cloud.netflix.eureka.server.EnableEurekaServer
public class RegistryApplication {public static void main(String[] args){SpringApplication.run(RegistryApplication.class,args);}}
