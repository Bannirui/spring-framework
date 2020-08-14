package learn.demo;

import learn.demo.app.AppConfig;
import learn.demo.service.impl.Person;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/**
 * @author dingrui
 * @create 2020-08-11 9:47 下午
 * @Description
 */

public class DemoApplication {
	public static void main(String[] args) {
		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(AppConfig.class);
		Person person = ((Person) context.getBean("person"));
		System.out.println("person: " + person);
	}

}
