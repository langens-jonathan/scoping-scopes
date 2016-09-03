package scopes_service.query_pre_processor.config;

import scopes_service.query_pre_processor.web.RootController;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurationSupport;

@Configuration
@EnableWebMvc
@ComponentScan(basePackageClasses = RootController.class)
public class WebConfiguration extends WebMvcConfigurationSupport {


}
