package cn.edu.academic.support;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@MapperScan("cn.edu.academic.support.**.mapper")
public class AcademicSupportApplication {
    public static void main(String[] args) {
        SpringApplication.run(AcademicSupportApplication.class, args);
    }
}
