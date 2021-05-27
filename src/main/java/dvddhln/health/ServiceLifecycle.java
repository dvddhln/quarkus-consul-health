package dvddhln.health;

import com.orbitz.consul.Consul;
import com.orbitz.consul.model.agent.ImmutableRegistration;
import com.orbitz.consul.model.agent.Registration;
import com.orbitz.consul.model.health.ServiceHealth;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;


@ApplicationScoped
public class ServiceLifecycle {

    private static final Logger LOGGER = LoggerFactory
            .getLogger(ServiceLifecycle.class);
    private String instanceId;

    Consul consulClient;
    @ConfigProperty(name = "quarkus.application.name")
    String appName;
    @ConfigProperty(name = "quarkus.application.version")
    String appVersion;
    @ConfigProperty(name = "quarkus.http.port", defaultValue = "8080")
    Integer port;

    void onStart(@Observes StartupEvent ev) {

        ScheduledExecutorService executorService = Executors
                .newSingleThreadScheduledExecutor();
        executorService.schedule(() -> {
            consulClient = Consul.newClient();
            List<ServiceHealth> instances = consulClient.healthClient()
                    .getHealthyServiceInstances(appName).getResponse();

            instanceId = appName + "-" + instances.size();

            ImmutableRegistration registration = ImmutableRegistration.builder()
                    .id(instanceId)
                    .name(appName)
                    .addChecks(Registration.RegCheck.http("http://localhost" + ":" + port + "/q/health/live", 5),
                            Registration.RegCheck.http("http://localhost" + ":" + port + "/q/health/ready", 5))
                    .port(port)
                    .putMeta("version", appVersion)
                    .build();
            consulClient.agentClient().register(registration);
            LOGGER.info("Instance registered: id={}", registration.getId());
        }, 5000, TimeUnit.MILLISECONDS);
    }

    void onStop(@Observes ShutdownEvent ev) {
        consulClient.agentClient().deregister(instanceId);
        LOGGER.info("Instance de-registered: id={}", instanceId);
    }

}