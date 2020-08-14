package learn.demo.service.impl;

import org.springframework.beans.factory.InitializingBean;

import javax.annotation.PostConstruct;

/**
 * @author dingrui
 * @create 2020-08-11 9:48 下午
 * @Description
 */
public class Person implements InitializingBean {

	private String name;
	private Integer age;

	public void initPerson() {
		System.out.println("init方法");
	}

	@PostConstruct
	public void init() {
		System.out.println("postConstruct init");
	}

	public Person() {
		System.out.println("构造方法");
	}

	public Person(String name, Integer age) {
		this.name = name;
		this.age = age;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public Integer getAge() {
		return age;
	}

	public void setAge(Integer age) {
		this.age = age;
	}

	@Override
	public void afterPropertiesSet() throws Exception {
		System.out.println("afterPropertiesSet。。。。方法");
	}

	@Override
	public String toString() {
		return "Person{" +
				"name='" + name + '\'' +
				", age=" + age +
				'}';
	}
}
