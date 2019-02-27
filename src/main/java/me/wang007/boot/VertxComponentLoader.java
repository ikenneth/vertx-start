package me.wang007.boot;

import io.vertx.core.*;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import me.wang007.annotation.Deploy;
import me.wang007.container.Component;
import me.wang007.container.Container;
import me.wang007.container.LoadContainer;
import me.wang007.exception.ErrorUsedAnnotationException;
import me.wang007.annotation.Route;
import me.wang007.exception.VertxStartException;
import me.wang007.verticle.VerticleConfig;

import java.util.List;

import static me.wang007.verticle.StartVerticleFactory.Start_Prefix;

/**
 * created by wang007 on 2019/2/26
 */
public class VertxComponentLoader {

    private static final Logger logger = LoggerFactory.getLogger(VertxComponentLoader.class);


    public VertxComponentLoader(LoadContainer container) {
        if (container.started()) throw new IllegalStateException("Load container must be not started");
        container.registerLoadBy(Deploy.class).registerLoadBy(Route.class);
    }

    public void executeLoad(Container container, Vertx vertx) {
        List<Component> components = container.getComponentsByAnnotation(Deploy.class);
        components.stream()
                .filter(c -> {
                    Deploy deploy = c.getAnnotation(Deploy.class);
                    if (deploy == null) {
                        logger.warn("component: {} not found @Deploy Annotation");
                        return false;
                    }
                    if (!(Verticle.class.isAssignableFrom(c.clazz))) {
                        throw new ErrorUsedAnnotationException("@Deploy can only be used on Verticle, component:" + c.clazz.getName());
                    }
                    return true;
                })
                .sorted((c1, c2) -> {
                    Deploy d1 = c1.getAnnotation(Deploy.class);
                    Deploy d2 = c2.getAnnotation(Deploy.class);
                    int order1 = d1.order();
                    int order2 = d2.order();
                    if (order1 >= order2) return 1;
                    else return -1;
                })
                .forEach(component -> {
                    String verticleName = component.clazz.getName();
                    logger.info("deploy verticle -> {}", verticleName);

                    Deploy deploy = component.getAnnotation(Deploy.class);
                    Verticle instance ;
                    try {
                        instance = (Verticle) component.clazz.newInstance();
                    } catch (Exception e) {
                        throw new VertxStartException("create verticle instance failed, verticle: " + component.clazz.getName(), e);
                    }

                    VerticleConfig config = instance instanceof VerticleConfig ? (VerticleConfig) instance: null;
                    DeploymentOptions options = config != null ? config.options(): new DeploymentOptions();
                    if(options == null) throw new VertxStartException(component.clazz.getName() + " #options() returned null");

                    boolean requireSingle = config != null && config.requireSingle();
                    int instanceCount = deploy.instances() == Integer.MAX_VALUE ?
                            VertxOptions.DEFAULT_EVENT_LOOP_POOL_SIZE : deploy.instances();
                    boolean worker = deploy.worker();
                    boolean multiWork = deploy.multiThreaded();

                    if(instanceCount != DeploymentOptions.DEFAULT_INSTANCES) options.setInstances(instanceCount);
                    if(worker != DeploymentOptions.DEFAULT_WORKER) options.setWorker(worker);
                    if(multiWork != DeploymentOptions.DEFAULT_MULTI_THREADED) options.setMultiThreaded(multiWork);
                    if(requireSingle && options.getInstances() != 1) throw new IllegalStateException("verticleName must be single instance");

                    Handler<AsyncResult<String>> deployedHandler = config != null ? config.deployedHandler(): null;
                    vertx.deployVerticle(Start_Prefix + ':' + verticleName, options, deployedHandler);
                });

    }


}