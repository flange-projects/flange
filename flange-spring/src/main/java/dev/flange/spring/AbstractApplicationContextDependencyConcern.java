/*
 * Copyright © 2023 GlobalMentor, Inc. <https://www.globalmentor.com/>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.flange.spring;

import static java.util.Objects.*;

import java.io.IOException;

import javax.annotation.*;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.support.*;
import org.springframework.context.*;

import dev.flange.*;

/**
 * Abstract base class for implementing a dependency concern backed by a spring {@link ApplicationContext}.
 * @param <AC> The type of Spring application context backing this dependency concern implementation.
 * @author Garret Wilson
 */
public abstract class AbstractApplicationContextDependencyConcern<AC extends ApplicationContext> extends AbstractDependencyConcern<AC> {

	/**
	 * Container constructor.
	 * @param applicationContext The Spring application context.
	 */
	public AbstractApplicationContextDependencyConcern(@Nonnull final AC applicationContext) {
		super(applicationContext);
	}

	/**
	 * Determines how to register beans in Spring.
	 * @implNote In many cases the bean definition registry is the application context itself.
	 * @return The Spring registry for bean definitions.
	 */
	protected abstract BeanDefinitionRegistry getBeanDefinitionRegistry();

	/**
	 * {@inheritDoc}
	 * @implSpec This implementation uses the class simple name as the Spring bean name.
	 */
	@Override
	public void registerDependency(final Class<?> dependencyClass) {
		final GenericBeanDefinition beanDefinition = new GenericBeanDefinition();
		beanDefinition.setBeanClass(dependencyClass);
		beanDefinition.setAutowireCandidate(true);
		try {
			getBeanDefinitionRegistry().registerBeanDefinition(dependencyClass.getSimpleName(), beanDefinition);
		} catch(final BeansException beansException) {
			throw new DependencyException(beansException.getMessage(), beansException);
		}
	}

	@Override
	public <T> T getDependencyInstanceByType(final Class<T> dependencyType) throws MissingDependencyException {
		try {
			return getContainerInitialized().getBean(requireNonNull(dependencyType));
		} catch(final NoSuchBeanDefinitionException noSuchBeanDefinitionException) {
			throw new MissingDependencyException(noSuchBeanDefinitionException.getMessage(), dependencyType, noSuchBeanDefinitionException);
		} catch(final BeansException beansException) {
			throw new DependencyException(beansException.getMessage(), beansException);
		}
	}

	/**
	 * {@inheritDoc}
	 * @implSpec This implementation refreshes the application context after initialization if supported.
	 */
	@Override
	protected void initialize(final AC container) throws IOException {
		super.initialize(container);
		if(container instanceof ConfigurableApplicationContext configurableApplicationContext) {
			try {
				configurableApplicationContext.refresh();//TODO catch and translate beans exception
			} catch(final BeansException beansException) {
				throw new DependencyException(beansException.getMessage(), beansException);
			}
		}
	}

}
