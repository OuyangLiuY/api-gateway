package com.citi.tts.api.gateway.loadbalancer;

import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.loadbalancer.core.ReactorServiceInstanceLoadBalancer;
import org.springframework.cloud.client.loadbalancer.Request;
import org.springframework.cloud.client.loadbalancer.Response;
import org.springframework.cloud.client.loadbalancer.DefaultResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * 基于策略的Reactor负载均衡器，委托AdvancedLoadBalancer实现多策略选择
 */
@Component
public class AdvancedReactorLoadBalancer implements ReactorServiceInstanceLoadBalancer {
    private final AdvancedLoadBalancer advancedLoadBalancer;

    @Autowired
    public AdvancedReactorLoadBalancer(AdvancedLoadBalancer advancedLoadBalancer) {
        this.advancedLoadBalancer = advancedLoadBalancer;
    }

    @Override
    public Mono<Response<ServiceInstance>> choose(Request request) {
        // 假设请求的serviceId通过request.getContext()传递
        String serviceId = (request.getContext() != null) ? request.getContext().toString() : "default";
        String requestId = java.util.UUID.randomUUID().toString();
        return advancedLoadBalancer.chooseInstance(serviceId, requestId)
                .map(instance -> (instance != null) ? new DefaultResponse(instance) : new EmptyResponse());
    }

    static class EmptyResponse implements Response<ServiceInstance> {
        @Override
        public ServiceInstance getServer() { return null; }
        @Override
        public boolean hasServer() { return false; }
    }
} 