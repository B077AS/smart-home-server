<div align="center">

# 🏠 Smart Home Controller

**Zigbee Device Management & Garage Door Automation**

A Spring Boot-based smart home controller for managing Zigbee devices, with a focus on garage door automation and monitoring.

[![Java Version](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.java.net/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.2-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)

[Features](#-features) • [Tech Stack](#️-tech-stack) • [Getting Started](#-getting-started) • [Configuration](#️-configuration)

</div>

---

## ✨ Features

- **🚪 Garage Door Control & Monitoring**
    - Real-time state tracking (opening, closing, idle, stuck detection)
    - Power consumption analysis to detect door movement and issues
    - Tilt sensor integration for open/closed detection
    - Vibration sensor for movement confirmation
    - Progress estimation during operations

- **📱 Device Management**
    - Zigbee smart plug monitoring
    - Vibration sensor integration
    - Tilt sensor integration
    - Cached device state for fast responses

- **👨‍💼 Admin Dashboard**
    - User management
    - Activity logs (garage access, plug activity, door state changes)
    - 24-hour statistics
    - Super admin login notifications via email

## 🛠️ Tech Stack

- Java 21
- Spring Boot 4.0.2
- Spring Security
- Lombok
- JPA/Hibernate
- HiveMQ MQTT Client
- Zigbee2MQTT (device broker)

## 🔍 Garage Door State Detection

The system uses multiple sensors to accurately track garage door state:

1. **⚡ Power Monitoring** - Analyzes smart plug power consumption

2. **📐 Tilt Sensor** - Detects open/closed position
    - Contact closed = door closed
    - Contact open = door open

3. **📳 Vibration Sensor** - Confirms door movement
    - Detects vibration during operation
    - Angle tracking for movement validation

### ⚠️ Stuck Detection

The system can detect when the door is stuck:
- **No movement**: Motor runs but sensors show no change
- **Minimal movement**: Brief power spike then drop, no sensor change
- Provides clear warnings for manual intervention

## 🚀 Getting Started

1. Clone the repository
2. Create `local.properties` file (see [Configuration](#️-configuration))
3. Build the project

```bash
mvn clean package
```

4. Run the application

```bash
mvn spring-boot:run
```

## ⚙️ Configuration

Create a `local.properties` file in `src/main/resources/` (this file is automatically imported via `spring.config.import=optional:classpath:local.properties`):

```properties
# Garage RfCat Service Configuration
garage.rfcat-service-url=
garage.secret-token=

# MQTT Configuration (Zigbee2MQTT)
mqtt.broker.host=
mqtt.broker.port=
mqtt.username=
mqtt.password=
mqtt.connection.timeout=

# Mail Configuration
spring.mail.host=
spring.mail.port=
spring.mail.username=
spring.mail.password=
spring.mail.properties.mail.smtp.auth=
spring.mail.properties.mail.smtp.starttls.enable=

# Admin Notification Email (where alerts will be sent)
admin.notification.email=

# Error Redirect
error.redirect=
```

## 🔒 Security

- Spring Security for authentication
- Role-based access (SUPER_ADMIN, regular users)
- Session management
- IP tracking for super admin logins