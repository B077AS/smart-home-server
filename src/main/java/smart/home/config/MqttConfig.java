package smart.home.config;

import com.hivemq.client.mqtt.MqttClient;
import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
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

    @Value("${mqtt.connection.timeout:10}")
    private int connectionTimeoutSeconds;

    @Bean
    public Mqtt3AsyncClient mqttClient() {
        log.info("Initializing MQTT client for {}:{}", brokerHost, brokerPort);

        Mqtt3AsyncClient client = MqttClient.builder()
                .useMqttVersion3()
                .identifier(UUID.randomUUID().toString())
                .serverHost(brokerHost)
                .serverPort(brokerPort)
                .automaticReconnect()
                .initialDelay(1, TimeUnit.SECONDS)
                .maxDelay(30, TimeUnit.SECONDS)
                .applyAutomaticReconnect()
                .addConnectedListener(ctx -> log.info("MQTT reconnected successfully"))
                .addDisconnectedListener(ctx -> log.warn(
                        "MQTT disconnected. Cause: {}, Reconnect: {}",
                        ctx.getCause().getMessage(),
                        ctx.getReconnector().isReconnect()
                ))
                .buildAsync();

        try {
            client.connectWith()
                    .keepAlive(60)
                    .cleanSession(true)
                    .simpleAuth()
                    .username(username)
                    .password(password.getBytes(StandardCharsets.UTF_8))
                    .applySimpleAuth()
                    .send()
                    .get(connectionTimeoutSeconds, TimeUnit.SECONDS);

            log.info("MQTT client connected successfully to {}:{}", brokerHost, brokerPort);
            log.info("MQTT client state: {}", client.getState());

        } catch (Exception e) {
            log.error("Failed to connect MQTT client within {} seconds", connectionTimeoutSeconds, e);
            throw new IllegalStateException("MQTT client connection failed", e);
        }

        return client;
    }
}