/*
 * Copyright 2002-2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.context.support;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.*;
import org.springframework.core.OrderComparator;
import org.springframework.core.Ordered;
import org.springframework.core.PriorityOrdered;
import org.springframework.core.metrics.ApplicationStartup;
import org.springframework.core.metrics.StartupStep;
import org.springframework.lang.Nullable;

import java.util.*;

/**
 * Delegate for AbstractApplicationContext's post-processor handling.
 *
 * @author Juergen Hoeller
 * @author Sam Brannen
 * @since 4.0
 */
final class PostProcessorRegistrationDelegate {

	private PostProcessorRegistrationDelegate() {
	}


	public static void invokeBeanFactoryPostProcessors(
			ConfigurableListableBeanFactory beanFactory, List<BeanFactoryPostProcessor> beanFactoryPostProcessors) {

		// Invoke BeanDefinitionRegistryPostProcessors first, if any.
		/**
		 * 步骤1：
		 *     定义一个set叫processedBeans 装载BeanName 后面会根据这个set判断后置处理器是否已经被执行过
		 */
		Set<String> processedBeans = new HashSet<>();

		/**
		 * 初始化：
		 *     AnnotationConfigApplicationContext初始化的时候为走到这
		 *         beanFactory是DefaultListableBeanFactory，是BeanDefinitionRegistry的实现类 if条件分支满足
		 */
		if (beanFactory instanceof BeanDefinitionRegistry) {
			BeanDefinitionRegistry registry = (BeanDefinitionRegistry) beanFactory;
			/**
			 * 步骤2：
			 *     定义两个list
			 *         regularPostProcessors 常规类型容器
			 *             装载BeanFactoryPostProcessor
			 *         registryProcessors 扩展类型容器
			 *             装载BeanDefinitionRegistryPostProcessor
			 *                 BeanDefinitionRegistryPostProcessor扩展了装载BeanFactoryPostProcessor
			 *                 它有两个方法，一个是独有的postProcessBeanDefinitionRegistry，一个是父类的postProcessBeanFactory
			 */
			// 容器 存储BeanFactoryPostProcessor 只要外面没有手动传进来后置处理器 这个容器在spring执行的时候就一直是空的
			List<BeanFactoryPostProcessor> regularPostProcessors = new ArrayList<>();
			// 容器 存储BeanDefinitionRegistryPostProcessor 它扩展了BeanFactoryPostProcessor
			List<BeanDefinitionRegistryPostProcessor> registryProcessors = new ArrayList<>();

			/**
			 * 只要不是在外部new AnnotationConfigApplicationContext的时候手动传进来后置处理器 这个beanFactoryPostProcessors一定是空的
			 *
			 * 初始化：
			 *     for循环不进去
			 *
			 * 步骤3：
			 *     假设手动传进来了后置处理器，那这边for循环就要走进去
			 *         因为BeanDefinitionRegistryPostProcessor是扩展了BeanFactoryPostProcessor的 所以先判断类型
			 *             如果类型是扩展类型，就先执行扩展类型独有的方法：postProcessBeanDefinitionRegistry 然后添加到扩展类型容器registryProcessors
			 *             如果是常规类型 就直接添加到常规类型容器中
			 */
			for (BeanFactoryPostProcessor postProcessor : beanFactoryPostProcessors) {
				if (postProcessor instanceof BeanDefinitionRegistryPostProcessor) {
					BeanDefinitionRegistryPostProcessor registryProcessor =
							(BeanDefinitionRegistryPostProcessor) postProcessor;
					registryProcessor.postProcessBeanDefinitionRegistry(registry);
					registryProcessors.add(registryProcessor);
				}
				else {
					regularPostProcessors.add(postProcessor);
				}
			}

			// Do not initialize FactoryBeans here: We need to leave all regular beans
			// uninitialized to let the bean factory post-processors apply to them!
			// Separate between BeanDefinitionRegistryPostProcessors that implement
			// PriorityOrdered, Ordered, and the rest.
			/**
			 * 声明一个临时变量 用来装载BeanDefinitionRegistryPostProcessor BeanDefinitionRegistry继承了PostProcessorBeanFactoryPostProcessor
			 *
			 * 步骤4：
			 *     定义一个临时变量容器 存储扩展类型后置处理器
			 */
			List<BeanDefinitionRegistryPostProcessor> currentRegistryProcessors = new ArrayList<>();

			// First, invoke the BeanDefinitionRegistryPostProcessors that implement PriorityOrdered.
			/**
			 * 初始化：
			 *     这个地方只能找到一个internalConfigurationAnnotationProcessor的名字
			 *
			 * 步骤5：
			 *     根据类型查找到BeanNames，查找扩展类型的后置处理器名称 通常只能找到一个 就是内置的internalConfigurationAnnotationProcessor
			 */
			String[] postProcessorNames =
					beanFactory.getBeanNamesForType(BeanDefinitionRegistryPostProcessor.class, true, false);

			/**
			 * 步骤6：
			 *     遍历步骤5的bean名称 其实就是internalConfigurationAnnotationProcessor
			 *     判断是否实现了PriorityOrdered接口（事实上ConfigurationAnnotationProcessor是实现了这个接口的）
			 *         如果实现了该接口
			 *             加到临时变量currentRegistryProcessors中
			 *             再加到processedBeans中（表示已经被处理结束了，当然事实上目前还没处理 在后面代码中真正执行处理逻辑）
			 */
			for (String ppName : postProcessorNames) {
				if (beanFactory.isTypeMatch(ppName, PriorityOrdered.class)) {
					/**
					 * 获得ConfigurationClassPostProcessor类，放到currentRegistryProcessors这个列表中
					 * ConfigurationClassPostProcessor是很重要的类，它实现了BeanDefinitionRegistryPostProcessor接口
					 * BeanDefinitionRegistryPostProcessor接口又继承了BeanFactoryPostProcessor接口
					 *
					 * ConfigurationClassPostProcessor是一个极其重要的类，里面执行了扫描Bean、Import、ImportResource等各种操作
					 * 用来处理配置类的各种逻辑（这个配置类包括了Full类和普通Bean）
					 */
					currentRegistryProcessors.add(beanFactory.getBean(ppName, BeanDefinitionRegistryPostProcessor.class));
					// 把名字放到set集合中 后续根据这个集合来判断处理器是否已经被执行过了
					processedBeans.add(ppName);
				}
			}

			/**
			 * 处理排序
			 *
			 * 步骤7：
			 *     进行排序
			 *         现在临时变量currentRegistryProcessors里面放着的是实现了PriorityOrdered接口的扩展类型后置处理器
			 *         既然实现了排序接口PriorityOrdered，就说明后置处理器是有顺序的
			 *         （当然了 事实上现在只有一个内置处理器）
			 */
			sortPostProcessors(currentRegistryProcessors, beanFactory);

			/**
			 * 步骤8：
			 *     临时变量里面的扩展类型的后置处理器全部合并到扩展类型容器中
			 *
			 * 合并Processors，为什么要合并呢
			 *     因为registryProcessors是装载BeanDefinitionRegistryPostProcessor的
			 *     一开始的时候，spring只会执行BeanDefinitionRegistryPostProcessor独有的方法 而不会执行BeanDefinitionRegistryPostProcessor父类的方法（也就是BeanFactoryProcessor的方法）
			 *     所以这里需要把处理器放入一个集合中，后续统一执行父类的方法
			 */
			registryProcessors.addAll(currentRegistryProcessors);

			/**
			 * 第一次执行
			 *
			 * 执行完这一步 beanDefinitionMap中添加了新的bean 然后后面再执行getBeanNamesForType方法时就会有新注册的bean
			 *
			 * 执行了ConfigurationClassPostProcessor的postProcessBeanDefinitionRegistry方法
			 *     这儿就体现了spring的热插拔，这个ConfigurationClassPostProcessor就相当于一个组件，spring很多的事情就是交给组件去管理 如果不想使用这个组件了 直接把注册组件的那一步去掉就行了
			 *
			 * 步骤9：
			 *     临时变量容器里面的扩展型后置处理器执行独有方法：postProcessBeanDefinitionRegistry
			 */
			invokeBeanDefinitionRegistryPostProcessors(currentRegistryProcessors, registry, beanFactory.getApplicationStartup());

			/**
			 * currentRegistryProcessors这是一个临时变量 需要清除
			 *
			 * 步骤10：
			 *     完成一轮invokeBeanDefinitionRegistryPostProcessors执行 清空临时变量容器
			 */
			currentRegistryProcessors.clear();

			// Next, invoke the BeanDefinitionRegistryPostProcessors that implement Ordered.
			/**
			 * 再次根据BeanDefinitionRegistryPostProcessor获得BeanName，看这个BeanName是否已经被执行过，有没有实现Order接口
			 *     如果没有被执行过，也实现了Order接口的话，把对象加到容器currentRegistryProcessors，名称加到容器processedBeans中
			 *     如果没有实现Order接口的话，这里不把数据加到容器中，后续再做处理
			 *
			 * 这个才可以获取我们定义的实现了BeanDefinitionRegistryPostProcessor的Bean
			 *     没有手动传后置处理器的情况下，这儿只能获得一个内置的后置处理器：internalConfigurationAnnotationProcessor
			 *
			 * 步骤11：
			 *     再次根据getBeanNamesForType获取扩展型后置处理器名字（代码走到这说明上面的invokeBeanDefinitionRegistryPostProcessors方法已经执行过 如果自己写了个类实现了BeanDefinitionRegistryPostProcessor接口并通过@Component标注 那么方法执行完就会注入到容器中）
			 *     也就是说这时候getBeanNamesForType获取的名称就是spring扫描一轮过后的结果 会有新增 这些新增的扩展型后置处理器就是我们自定义的
			 *     遍历这些bean名称
			 *         如果已经被执行过
			 *             就不用管了
			 *         如果没有被执行过 并且这个扩展型后置处理器还实现了Ordered接口
			 *             就添加到临时容器中currentRegistryProcessors
			 *             再添加到已经处理容器中processedBeans（事实上现在还没轮到处理 延后处理）
			 */
			postProcessorNames = beanFactory.getBeanNamesForType(BeanDefinitionRegistryPostProcessor.class, true, false);
			for (String ppName : postProcessorNames) {
				if (!processedBeans.contains(ppName) && beanFactory.isTypeMatch(ppName, Ordered.class)) {
					currentRegistryProcessors.add(beanFactory.getBean(ppName, BeanDefinitionRegistryPostProcessor.class));
					processedBeans.add(ppName);
				}
			}

			/**
			 * 处理排序
			 *
			 * 步骤12：
			 *     跟步骤7一样 现在存放在临时容器中的这些后置处理器都是满足了实现Ordered接口条件的 所以一定是有序的 需要排序
			 */
			sortPostProcessors(currentRegistryProcessors, beanFactory);

			/**
			 * 合并processors
			 *
			 * 步骤13：
			 *     跟步骤8一样 现在临时容器中要处理的都是扩展型后置处理器 先执行他们的独有方法
			 */
			registryProcessors.addAll(currentRegistryProcessors);

			/**
			 * 第二次执行
			 *
			 * 执行自定义的BeanDefinitionRegistryPostProcessor
			 *
			 *
			 * 步骤14：
			 *     执行我们自定义的扩展型后置处理器的独有方法
			 */
			invokeBeanDefinitionRegistryPostProcessors(currentRegistryProcessors, registry, beanFactory.getApplicationStartup());

			/**
			 * 清空临时变量
			 *
			 * 步骤15：
			 *     第二轮已经完成 执行完了我们自定义的扩展型后置处理器的独有方法 清空临时容器 准备下一轮
			 */
			currentRegistryProcessors.clear();

			// Finally, invoke all other BeanDefinitionRegistryPostProcessors until no further ones appear.
			/**
			 * 上面的代码是执行了实现了Ordered接口的BeanDefinitionRegistryPostProcessor
			 * 下面的代码是执行没有实现Ordered接口的BeanDefinitionRegistryPostProcessor
			 *
			 * 步骤16：
			 *     再次getBeanNamesForType获取扩展型后置处理器的名字
			 *     遍历这些名字 找到没有被处理过的扩展型后置处理器
			 *     （这是第三轮  第二轮中的筛选条件是没被第一轮处理 又实现了Ordered接口的 所以现在是第三轮 能筛选出来需要处理的一定就是没被第二轮筛选的 也就是自定义的扩展型后置处理器 并且没有实现Ordered接口）
			 *         添加到临时容器中currentRegistryProcessors
			 *         添加到已经处理容器中processedBeans
			 */
			boolean reiterate = true;
			while (reiterate) {
				reiterate = false;
				postProcessorNames = beanFactory.getBeanNamesForType(BeanDefinitionRegistryPostProcessor.class, true, false);
				for (String ppName : postProcessorNames) {
					if (!processedBeans.contains(ppName)) {
						currentRegistryProcessors.add(beanFactory.getBean(ppName, BeanDefinitionRegistryPostProcessor.class));
						processedBeans.add(ppName);
						reiterate = true;
					}
				}
				/**
				 * todo 这儿有点疑惑 依然第三轮需要处理的是自定义扩展型处理器却没有实现Ordered接口 为啥还要排序
				 */
				sortPostProcessors(currentRegistryProcessors, beanFactory);
				registryProcessors.addAll(currentRegistryProcessors);
				/**
				 * 第三次执行
				 */
				invokeBeanDefinitionRegistryPostProcessors(currentRegistryProcessors, registry, beanFactory.getApplicationStartup());
				currentRegistryProcessors.clear();
			}

			// Now, invoke the postProcessBeanFactory callback of all processors handled so far.

			/**
			 * 第四次执行
			 *
			 * 上面的代码执行的是子类独有的方法，这里需要再把父类的方法也执行一次
			 *
			 * 步骤17：
			 *     代码走到这 前面已经执行过三轮扩展型处理器 但是都是执行他们的独有方法：postProcessBeanDefinitionRegistry 现在把他们一次性一起执行父类方法：postProcessBeanFactory
			 */
			invokeBeanFactoryPostProcessors(registryProcessors, beanFactory);
			/**
			 * 第五次执行
			 * 只要外面没有手动传后置处理器进来 regularPostProcessors就是空的
			 *
			 * 步骤18：
			 *     前面4轮处理的都是扩展型后置处理器（内置的+自定义的） 现在开始处理常规后置处理器
			 */
			invokeBeanFactoryPostProcessors(regularPostProcessors, beanFactory);
		}

		/**
		 * 初始化：
		 *     这个分支就不会进去了
		 */
		else {
			// Invoke factory processors registered with the context instance.
			/**
			 * 第六次执行
			 */
			invokeBeanFactoryPostProcessors(beanFactoryPostProcessors, beanFactory);
		}

		// Do not initialize FactoryBeans here: We need to leave all regular beans
		// uninitialized to let the bean factory post-processors apply to them!
		/**
		 * 找到BeanFactoryPostProcessor实现类的BeanName数组
		 *
		 * 步骤19：
		 *     BeanDefinitionRegistryPostProcessor继承了BeanFactoryPostProcessor
		 *     现在按照基类BeanFactoryPostProcessor再查找一次 找到所有实现了BeanFactoryPostProcessor的后置处理器
		 *     找出所有名称后遍历
		 *         已经被处理过
		 *             pass不管它
		 *         没有被处理过
		 *             实现了PriorityOrdered接口的放一个容器priorityOrderedPostProcessors
		 *                 排序
		 *                 执行invokeBeanFactoryPostProcessors
		 *             实现了Ordered接口的放一个容器orderedPostProcessorNames
		 *                 排序
		 *                 执行invokeBeanFactoryPostProcessors
		 *             没有实现上面两个接口的放一个容器nonOrderedPostProcessorNames
		 *                 执行invokeBeanFactoryPostProcessors
		 */
		String[] postProcessorNames =
				beanFactory.getBeanNamesForType(BeanFactoryPostProcessor.class, true, false);

		// Separate between BeanFactoryPostProcessors that implement PriorityOrdered,
		// Ordered, and the rest.
		List<BeanFactoryPostProcessor> priorityOrderedPostProcessors = new ArrayList<>();
		List<String> orderedPostProcessorNames = new ArrayList<>();
		List<String> nonOrderedPostProcessorNames = new ArrayList<>();
		// 遍历BeanName数组
		for (String ppName : postProcessorNames) {
			// 如果这个Bean被执行过 跳过
			if (processedBeans.contains(ppName)) {
				// skip - already processed in first phase above
			}
			// 如果实现了PriorityOrdered接口 加入到priorityOrderedPostProcessors
			else if (beanFactory.isTypeMatch(ppName, PriorityOrdered.class)) {
				priorityOrderedPostProcessors.add(beanFactory.getBean(ppName, BeanFactoryPostProcessor.class));
			}
			// 如果实现了Ordered接口 加入到orderedPostProcessorNames
			else if (beanFactory.isTypeMatch(ppName, Ordered.class)) {
				orderedPostProcessorNames.add(ppName);
			}
			// 如果既没有实现PriorityOrdered接口 也没有实现Ordered接口 加入到nonOrderedPostProcessorNames
			else {
				nonOrderedPostProcessorNames.add(ppName);
			}
		}

		// First, invoke the BeanFactoryPostProcessors that implement PriorityOrdered.
		// 排序处理
		sortPostProcessors(priorityOrderedPostProcessors, beanFactory);

		/**
		 * 第七次执行
		 *
		 * 执行实现了priorityOrdered接口的BeanFactoryPostProcessors
		 */
		invokeBeanFactoryPostProcessors(priorityOrderedPostProcessors, beanFactory);

		// Next, invoke the BeanFactoryPostProcessors that implement Ordered.
		List<BeanFactoryPostProcessor> orderedPostProcessors = new ArrayList<>(orderedPostProcessorNames.size());
		for (String postProcessorName : orderedPostProcessorNames) {
			orderedPostProcessors.add(beanFactory.getBean(postProcessorName, BeanFactoryPostProcessor.class));
		}
		sortPostProcessors(orderedPostProcessors, beanFactory);
		/**
		 * 第八次执行
		 *
		 * 执行实现了Ordered接口的BeanFactoryPostProcessors
		 */
		invokeBeanFactoryPostProcessors(orderedPostProcessors, beanFactory);

		// Finally, invoke all other BeanFactoryPostProcessors.
		List<BeanFactoryPostProcessor> nonOrderedPostProcessors = new ArrayList<>(nonOrderedPostProcessorNames.size());
		for (String postProcessorName : nonOrderedPostProcessorNames) {
			nonOrderedPostProcessors.add(beanFactory.getBean(postProcessorName, BeanFactoryPostProcessor.class));
		}
		/**
		 * 第九次执行
		 *
		 * 执行接没有实现PriorityOrdered接口也没有实现Ordered接口的BeanFactoryPostProcessors
		 */
		invokeBeanFactoryPostProcessors(nonOrderedPostProcessors, beanFactory);

		// Clear cached merged bean definitions since the post-processors might have
		// modified the original metadata, e.g. replacing placeholders in values...
		beanFactory.clearMetadataCache();
	}

	public static void registerBeanPostProcessors(
			ConfigurableListableBeanFactory beanFactory, AbstractApplicationContext applicationContext) {

		String[] postProcessorNames = beanFactory.getBeanNamesForType(BeanPostProcessor.class, true, false);

		// Register BeanPostProcessorChecker that logs an info message when
		// a bean is created during BeanPostProcessor instantiation, i.e. when
		// a bean is not eligible for getting processed by all BeanPostProcessors.
		int beanProcessorTargetCount = beanFactory.getBeanPostProcessorCount() + 1 + postProcessorNames.length;
		beanFactory.addBeanPostProcessor(new BeanPostProcessorChecker(beanFactory, beanProcessorTargetCount));

		// Separate between BeanPostProcessors that implement PriorityOrdered,
		// Ordered, and the rest.
		List<BeanPostProcessor> priorityOrderedPostProcessors = new ArrayList<>();
		List<BeanPostProcessor> internalPostProcessors = new ArrayList<>();
		List<String> orderedPostProcessorNames = new ArrayList<>();
		List<String> nonOrderedPostProcessorNames = new ArrayList<>();
		for (String ppName : postProcessorNames) {
			if (beanFactory.isTypeMatch(ppName, PriorityOrdered.class)) {
				BeanPostProcessor pp = beanFactory.getBean(ppName, BeanPostProcessor.class);
				priorityOrderedPostProcessors.add(pp);
				if (pp instanceof MergedBeanDefinitionPostProcessor) {
					internalPostProcessors.add(pp);
				}
			}
			else if (beanFactory.isTypeMatch(ppName, Ordered.class)) {
				orderedPostProcessorNames.add(ppName);
			}
			else {
				nonOrderedPostProcessorNames.add(ppName);
			}
		}

		// First, register the BeanPostProcessors that implement PriorityOrdered.
		sortPostProcessors(priorityOrderedPostProcessors, beanFactory);
		registerBeanPostProcessors(beanFactory, priorityOrderedPostProcessors);

		// Next, register the BeanPostProcessors that implement Ordered.
		List<BeanPostProcessor> orderedPostProcessors = new ArrayList<>(orderedPostProcessorNames.size());
		for (String ppName : orderedPostProcessorNames) {
			BeanPostProcessor pp = beanFactory.getBean(ppName, BeanPostProcessor.class);
			orderedPostProcessors.add(pp);
			if (pp instanceof MergedBeanDefinitionPostProcessor) {
				internalPostProcessors.add(pp);
			}
		}
		sortPostProcessors(orderedPostProcessors, beanFactory);
		registerBeanPostProcessors(beanFactory, orderedPostProcessors);

		// Now, register all regular BeanPostProcessors.
		List<BeanPostProcessor> nonOrderedPostProcessors = new ArrayList<>(nonOrderedPostProcessorNames.size());
		for (String ppName : nonOrderedPostProcessorNames) {
			BeanPostProcessor pp = beanFactory.getBean(ppName, BeanPostProcessor.class);
			nonOrderedPostProcessors.add(pp);
			if (pp instanceof MergedBeanDefinitionPostProcessor) {
				internalPostProcessors.add(pp);
			}
		}
		registerBeanPostProcessors(beanFactory, nonOrderedPostProcessors);

		// Finally, re-register all internal BeanPostProcessors.
		sortPostProcessors(internalPostProcessors, beanFactory);
		registerBeanPostProcessors(beanFactory, internalPostProcessors);

		// Re-register post-processor for detecting inner beans as ApplicationListeners,
		// moving it to the end of the processor chain (for picking up proxies etc).
		beanFactory.addBeanPostProcessor(new ApplicationListenerDetector(applicationContext));
	}

	private static void sortPostProcessors(List<?> postProcessors, ConfigurableListableBeanFactory beanFactory) {
		// Nothing to sort?
		if (postProcessors.size() <= 1) {
			return;
		}
		Comparator<Object> comparatorToUse = null;
		if (beanFactory instanceof DefaultListableBeanFactory) {
			comparatorToUse = ((DefaultListableBeanFactory) beanFactory).getDependencyComparator();
		}
		if (comparatorToUse == null) {
			comparatorToUse = OrderComparator.INSTANCE;
		}
		postProcessors.sort(comparatorToUse);
	}

	/**
	 * Invoke the given BeanDefinitionRegistryPostProcessor beans.
	 */
	private static void invokeBeanDefinitionRegistryPostProcessors(
			Collection<? extends BeanDefinitionRegistryPostProcessor> postProcessors, BeanDefinitionRegistry registry, ApplicationStartup applicationStartup) {

		for (BeanDefinitionRegistryPostProcessor postProcessor : postProcessors) {
			StartupStep postProcessBeanDefRegistry = applicationStartup.start("spring.context.beandef-registry.post-process")
					.tag("postProcessor", postProcessor::toString);
			postProcessor.postProcessBeanDefinitionRegistry(registry);
			postProcessBeanDefRegistry.end();
		}
	}

	/**
	 * Invoke the given BeanFactoryPostProcessor beans.
	 */
	private static void invokeBeanFactoryPostProcessors(
			Collection<? extends BeanFactoryPostProcessor> postProcessors, ConfigurableListableBeanFactory beanFactory) {

		for (BeanFactoryPostProcessor postProcessor : postProcessors) {
			StartupStep postProcessBeanFactory = beanFactory.getApplicationStartup().start("spring.context.bean-factory.post-process")
					.tag("postProcessor", postProcessor::toString);
			postProcessor.postProcessBeanFactory(beanFactory);
			postProcessBeanFactory.end();
		}
	}

	/**
	 * Register the given BeanPostProcessor beans.
	 */
	private static void registerBeanPostProcessors(
			ConfigurableListableBeanFactory beanFactory, List<BeanPostProcessor> postProcessors) {

		if (beanFactory instanceof AbstractBeanFactory) {
			// Bulk addition is more efficient against our CopyOnWriteArrayList there
			((AbstractBeanFactory) beanFactory).addBeanPostProcessors(postProcessors);
		}
		else {
			for (BeanPostProcessor postProcessor : postProcessors) {
				beanFactory.addBeanPostProcessor(postProcessor);
			}
		}
	}


	/**
	 * BeanPostProcessor that logs an info message when a bean is created during
	 * BeanPostProcessor instantiation, i.e. when a bean is not eligible for
	 * getting processed by all BeanPostProcessors.
	 */
	private static final class BeanPostProcessorChecker implements BeanPostProcessor {

		private static final Log logger = LogFactory.getLog(BeanPostProcessorChecker.class);

		private final ConfigurableListableBeanFactory beanFactory;

		private final int beanPostProcessorTargetCount;

		public BeanPostProcessorChecker(ConfigurableListableBeanFactory beanFactory, int beanPostProcessorTargetCount) {
			this.beanFactory = beanFactory;
			this.beanPostProcessorTargetCount = beanPostProcessorTargetCount;
		}

		@Override
		public Object postProcessBeforeInitialization(Object bean, String beanName) {
			return bean;
		}

		@Override
		public Object postProcessAfterInitialization(Object bean, String beanName) {
			if (!(bean instanceof BeanPostProcessor) && !isInfrastructureBean(beanName) &&
					this.beanFactory.getBeanPostProcessorCount() < this.beanPostProcessorTargetCount) {
				if (logger.isInfoEnabled()) {
					logger.info("Bean '" + beanName + "' of type [" + bean.getClass().getName() +
							"] is not eligible for getting processed by all BeanPostProcessors " +
							"(for example: not eligible for auto-proxying)");
				}
			}
			return bean;
		}

		private boolean isInfrastructureBean(@Nullable String beanName) {
			if (beanName != null && this.beanFactory.containsBeanDefinition(beanName)) {
				BeanDefinition bd = this.beanFactory.getBeanDefinition(beanName);
				return (bd.getRole() == RootBeanDefinition.ROLE_INFRASTRUCTURE);
			}
			return false;
		}
	}

}
