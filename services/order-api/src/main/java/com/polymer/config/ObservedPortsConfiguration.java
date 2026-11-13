package com.polymer.config;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Observability of the hexagon without touching it: every bean that implements a port of
 * com.polymer.application.port.in (use cases) or .port.out (repositories, risk engine, cache,
 * publisher, SMTP…) is wrapped in a JDK proxy that opens an Observation per call. That produces a
 * span per use case and per port call in Tempo (when tracing is enabled) and a latency timer
 * polymer_port_calls_seconds{kind,port,method} in Prometheus. The core stays framework-free.
 */
@Configuration(proxyBeanMethods = false)
public class ObservedPortsConfiguration {
    private static final String IN = "com.polymer.application.port.in.";
    private static final String OUT = "com.polymer.application.port.out.";

    @Bean
    static BeanPostProcessor observedPorts(ObjectProvider<ObservationRegistry> registry) {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                Class<?>[] ports = Arrays.stream(bean.getClass().getInterfaces())
                        .filter(i -> i.getName().startsWith(IN) || i.getName().startsWith(OUT)).toArray(Class<?>[]::new);
                if (ports.length == 0 || Proxy.isProxyClass(bean.getClass())) return bean;
                String kind = ports[0].getName().startsWith(IN) ? "use_case" : "output_port";
                String port = ports[0].getSimpleName();
                return Proxy.newProxyInstance(bean.getClass().getClassLoader(), ports, (proxy, method, args) -> {
                    if (method.getDeclaringClass() == Object.class) return method.invoke(bean, args);
                    ObservationRegistry observations = registry.getIfAvailable(() -> ObservationRegistry.NOOP);
                    Observation observation = Observation.createNotStarted("polymer.port.calls", observations)
                            .contextualName(port + "." + method.getName())
                            .lowCardinalityKeyValue("kind", kind)
                            .lowCardinalityKeyValue("port", port)
                            .lowCardinalityKeyValue("method", method.getName());
                    return observation.observeChecked(() -> {
                        try {
                            return method.invoke(bean, args);
                        } catch (InvocationTargetException wrapped) {
                            throw wrapped.getCause(); // the original exception, not the reflection wrapper
                        }
                    });
                });
            }
        };
    }
}
