package learn.demo.app;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * @author dingrui
 * @create 2020-08-11 10:20 下午
 * @Description
 */
@Configuration
@ComponentScan("learn.demo")
public class AppConfig {

	// @Autowired
	// private Person person;

	// @Bean(initMethod = "initPerson")
	// public Person person() {
	// 	Person person = new Person();
	// 	person.setName("丁锐");
	// 	person.setAge(20);
	// 	return person;
	// }

	// @Bean
	// public Person getPerson() {
	// 	// return this.person;
	// 	return null;
	// }
}
