package com.eunoia.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * @EnableAsync·@EnableScheduling을 SpringBootApplication 클래스가 아닌 별도 클래스로 분리(JpaAuditingConfig와 같은 이유).
 * @EnableAsync는 지금까지 Spring Modulith 자동 설정에 암묵적으로 기대고 있어서 여기서 명시한다.
 */
@Configuration
@EnableAsync
@EnableScheduling
public class AsyncSchedulingConfig {
}
