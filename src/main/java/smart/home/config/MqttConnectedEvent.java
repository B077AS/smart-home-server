package smart.home.config;

import org.springframework.context.ApplicationEvent;

public class MqttConnectedEvent extends ApplicationEvent {
    public MqttConnectedEvent(Object source) {
        super(source);
    }
}
