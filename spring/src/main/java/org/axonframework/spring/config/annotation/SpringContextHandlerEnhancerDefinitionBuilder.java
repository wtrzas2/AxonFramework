/*
 * Copyright (c) 2010-2017. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.spring.config.annotation;

import org.axonframework.messaging.annotation.ClasspathHandlerEnhancerDefinition;
import org.axonframework.messaging.annotation.HandlerEnhancerDefinition;
import org.axonframework.messaging.annotation.MultiHandlerEnhancerDefinition;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.RuntimeBeanReference;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.ManagedList;
import org.springframework.util.ClassUtils;

/**
 * Creates and registers a bean definition for a Spring Context aware HandlerEnhancerDefinition. It ensures that only
 * one such instance exists for each ApplicationContext.
 *
 * @author Allard Buijze
 * @since 2.1
 */
public final class SpringContextHandlerEnhancerDefinitionBuilder {

    private static final String HANDLER_ENHANCER_DEFINITION_BEAN_NAME = "__axon-handler-enhancer-definition";

    private SpringContextHandlerEnhancerDefinitionBuilder() {
    }

    /**
     * Create, if necessary, a bean definition for a HandlerEnhancerDefinition and returns the reference to bean for use
     * in other Bean Definitions.
     *
     * @param registry The registry in which to look for an already existing instance
     * @return a BeanReference to the BeanDefinition for the HandlerEnhancerDefinition
     */
    public static RuntimeBeanReference getBeanReference(BeanDefinitionRegistry registry) {
        if (!registry.containsBeanDefinition(HANDLER_ENHANCER_DEFINITION_BEAN_NAME)) {
            final ManagedList<BeanDefinition> factories = new ManagedList<>();
            factories.add(BeanDefinitionBuilder.genericBeanDefinition(ClasspathHandlerEnhancerDefinitionBean.class)
                                               .getBeanDefinition());
            factories.add(BeanDefinitionBuilder.genericBeanDefinition(SpringHandlerEnhancerFactoryBean.class)
                                               .getBeanDefinition());
            AbstractBeanDefinition def =
                    BeanDefinitionBuilder.genericBeanDefinition(MultiHandlerEnhancerDefinition.class)
                                         .addConstructorArgValue(factories)
                                         .getBeanDefinition();
            def.setPrimary(true);
            registry.registerBeanDefinition(HANDLER_ENHANCER_DEFINITION_BEAN_NAME, def);
        }
        return new RuntimeBeanReference(HANDLER_ENHANCER_DEFINITION_BEAN_NAME);
    }

    private static class ClasspathHandlerEnhancerDefinitionBean implements BeanClassLoaderAware, InitializingBean,
                                                                          FactoryBean<HandlerEnhancerDefinition> {

        private ClassLoader classLoader;
        private HandlerEnhancerDefinition factory;

        @Override
        public HandlerEnhancerDefinition getObject() throws Exception {
            return factory;
        }

        @Override
        public Class<?> getObjectType() {
            return HandlerEnhancerDefinition.class;
        }

        @Override
        public boolean isSingleton() {
            return true;
        }

        @Override
        public void afterPropertiesSet() throws Exception {
            this.factory = new ClasspathHandlerEnhancerDefinition(classLoader);
        }

        @Override
        public void setBeanClassLoader(ClassLoader classLoader) {
            this.classLoader = classLoader == null ? ClassUtils.getDefaultClassLoader() : classLoader;
        }
    }
}
