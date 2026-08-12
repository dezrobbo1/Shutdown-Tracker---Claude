package com.shutdowntracker.api.actor;

import jakarta.servlet.http.HttpServletRequest;
import java.util.function.Supplier;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * Injects the resolved {@link Actor} into controller methods that declare it as a parameter.
 *
 * <p>Declaring {@code Actor} on a handler is what marks an endpoint as requiring an authenticated user.
 *
 * <p>The {@link ActorResolver} is looked up lazily so that this resolver can be registered in web slices
 * that do not contribute one. Without a resolver the request is rejected rather than treated as anonymous.
 */
public class ActorArgumentResolver implements HandlerMethodArgumentResolver {

    private final Supplier<ActorResolver> actorResolverSupplier;

    public ActorArgumentResolver(Supplier<ActorResolver> actorResolverSupplier) {
        this.actorResolverSupplier = actorResolverSupplier;
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return Actor.class.equals(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(
            MethodParameter parameter,
            ModelAndViewContainer modelAndViewContainer,
            NativeWebRequest webRequest,
            WebDataBinderFactory binderFactory
    ) {
        ActorResolver actorResolver = actorResolverSupplier.get();
        if (actorResolver == null) {
            throw new UnauthenticatedRequestException(
                    "No actor resolver is configured. This operation must be attributed to a user."
            );
        }

        HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
        if (request == null) {
            throw new UnauthenticatedRequestException("Request context is unavailable for actor resolution.");
        }
        return actorResolver.resolve(request);
    }
}
