package smart.home.config;

import com.hivemq.client.mqtt.MqttClient;
import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient;
import com.hivemq.client.mqtt.mqtt5.exceptions.Mqtt5ConnAckException;
import com.hivemq.client.mqtt.mqtt5.message.connect.connack.Mqtt5ConnAckReasonCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

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

    @Autowired
    private Mqtt5AsyncClient mqttClient;

    @Bean
    public Mqtt5AsyncClient mqttClient() {
        log.info("Initializing MQTT client for {}:{}", brokerHost, brokerPort);

        AtomicBoolean connectedBefore = new AtomicBoolean(false);

        Mqtt5AsyncClient client = MqttClient.builder()
                .useMqttVersion5()
                .identifier("smart-home-" + UUID.randomUUID().toString().substring(0, 8))
                .serverHost(brokerHost)
                .serverPort(brokerPort)
                .simpleAuth()
                    .username(username)
                    .password(password.getBytes(StandardCharsets.UTF_8))
                    .applySimpleAuth()
                .automaticReconnect()
                    .initialDelay(1, TimeUnit.SECONDS)
                    .maxDelay(30, TimeUnit.SECONDS)
                    .applyAutomaticReconnect()
                .addConnectedListener(ctx -> {
                    connectedBefore.set(true);
                    log.info("MQTT connected to {}:{}", brokerHost, brokerPort);
                    eventPublisher.publishEvent(new MqttConnectedEvent(this));
                })
                .addDisconnectedListener(ctx -> {
                    Throwable cause = ctx.getCause();
                    if (cause instanceof Mqtt5ConnAckException connAckEx) {
                        Mqtt5ConnAckReasonCode reasonCode = connAckEx.getMqttMessage().getReasonCode();
                        if (reasonCode == Mqtt5ConnAckReasonCode.NOT_AUTHORIZED
                                || reasonCode == Mqtt5ConnAckReasonCode.BAD_USER_NAME_OR_PASSWORD) {
                            if (!connectedBefore.get()) {
                                log.error("MQTT broker rejected authentication — verify mqtt.username and mqtt.password in local.properties");
                            } else {
                                log.warn("MQTT reconnect rejected by broker ({}). Broker may be restarting, retrying...", reasonCode);
                            }
                        } else {
                            log.warn("MQTT connection refused by broker: {}", reasonCode);
                        }
                    } else {
                        log.warn("MQTT disconnected. Cause: {}", cause != null ? cause.getMessage() : "unknown");
                    }
                })
                .buildAsync();

        return client;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void connect() {
        mqttClient.connectWith()
                .keepAlive(60)
                .cleanStart(true)
                .sessionExpiryInterval(0)
                .send()
                .whenComplete((connAck, throwable) -> {
                    if (throwable != null) {
                        log.warn("Initial MQTT connection failed, will retry automatically: {}", throwable.getMessage());
                    }
                });
    }
}
