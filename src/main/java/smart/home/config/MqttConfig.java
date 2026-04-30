package smart.home.config;

import com.hivemq.client.mqtt.MqttClient;
import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Configuration
public class MqttConfig {

    @Value("${mqtt.broker.host:localhost}")
    private String brokerHost;

    @Value("${mqtt.broker.port:1883}")
    private int brokerPort;

    @Value("${mqtt.username}")
    private String username;

    @Value("${mqtt.password}")
    private String password;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Bean
    public Mqtt3AsyncClient mqttClient() {
        log.info("Initializing MQTT client for {}:{}", brokerHost, brokerPort);

        Mqtt3AsyncClient client = MqttClient.builder()
                .useMqttVersion3()
                .identifier("smart-home-" + UUID.randomUUID().toString().substring(0, 8))
                .serverHost(brokerHost)
                .serverPort(brokerPort)
                .automaticReconnect()
                .initialDelay(1, TimeUnit.SECONDS)
                .maxDelay(30, TimeUnit.SECONDS)
                .applyAutomaticReconnect()
                .addConnectedListener(ctx -> {
                    log.info("MQTT connected to {}:{}", brokerHost, brokerPort);
                    eventPublisher.publishEvent(new MqttConnectedEvent(this));
                })
                .addDisconnectedListener(ctx -> {
                    String cause = ctx.getCause().getMessage();
                    if (cause != null && cause.contains("NOT_AUTHORIZED")) {
                        log.error("MQTT broker rejected authentication — verify mqtt.username and mqtt.password in local.properties. Reconnect: {}", ctx.getReconnector().isReconnect());
                    } else {
                        log.warn("MQTT disconnected. Cause: {}, Reconnect: {}", cause, ctx.getReconnector().isReconnect());
                    }
                })
                .buildAsync();

        client.connectWith()
                .keepAlive(60)
                .cleanSession(true)
                .simpleAuth()
                .username(username)
                .password(password.getBytes(StandardCharsets.UTF_8))
                .applySimpleAuth()
                .send()
                .whenComplete((connAck, throwable) -> {
                    if (throwable != null) {
                        log.warn("Initial MQTT connection failed, will retry automatically: {}", throwable.getMessage());
                    }
                });

        return client;
    }
}
