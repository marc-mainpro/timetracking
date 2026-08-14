package com.tfp.timetracking.notification.infrastructure;

import com.tfp.timetracking.notification.application.NotificationDeliveryProperties;
import com.tfp.timetracking.notification.application.NotificationEmailProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Activa la configuracion del modulo notification (config/notification.yml). */
@Configuration
@EnableConfigurationProperties({NotificationDeliveryProperties.class, NotificationEmailProperties.class})
public class NotificationConfig {}
