package com.shutdowntracker.api.actor;

import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class ActorWebMvcConfiguration implements WebMvcConfigurer {

    private final ObjectProvider<ActorResolver> actorResolverProvider;

    public ActorWebMvcConfiguration(ObjectProvider<ActorResolver> actorResolverProvider) {
        this.actorResolverProvider = actorResolverProvider;
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new ActorArgumentResolver(actorResolverProvider::getIfAvailable));
    }
}
