package com.eunoia.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * @EnableJpaAuditing을 SpringBootApplication 클래스가 아닌 별도 클래스로 분리.
 * 메인 클래스에 직접 넣으면 @WebMvcTest 슬라이스가 이 클래스를 설정 소스로 쓰면서
 * EntityManagerFactory 없이 JpaMetamodelMappingContext를 만들려다 죽어버림. ("JPA metamodel must not be empty")
 * @WebMvcTest는 일반 @Configuration 빈은 스캔하지 않으므로
 * 분리해두면 슬라이스 테스트에 영향을 주지 않는다.
 */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
}
