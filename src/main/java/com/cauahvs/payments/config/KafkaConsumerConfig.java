package com.cauahvs.payments.config;

import com.cauahvs.payments.adapter.out.messaging.PaymentCreatedEvent;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.util.backoff.FixedBackOff;
import org.apache.kafka.common.serialization.StringSerializer;

import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableKafka
@Profile({"worker", "default"})
public class KafkaConsumerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Bean
    public ConsumerFactory<String, PaymentCreatedEvent> consumerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        JsonDeserializer<PaymentCreatedEvent> deserializer =
                new JsonDeserializer<>(PaymentCreatedEvent.class);
        deserializer.addTrustedPackages("com.cauahvs.payments.*");

        return new DefaultKafkaConsumerFactory<>(
                config, new StringDeserializer(), deserializer);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, PaymentCreatedEvent> kafkaListenerContainerFactory(
            DefaultErrorHandler errorHandler) {
        ConcurrentKafkaListenerContainerFactory<String, PaymentCreatedEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        factory.setCommonErrorHandler(errorHandler);
        return factory;
    }

    @Bean
    public KafkaTemplate<Object, Object> dlqKafkaTemplate() {
        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
                org.springframework.kafka.support.serializer.JsonSerializer.class);
        ProducerFactory<Object, Object> pf = new DefaultKafkaProducerFactory<>(config);
        return new KafkaTemplate<>(pf);
    }

    @Bean
    public DefaultErrorHandler errorHandler(KafkaTemplate<Object, Object> dlqKafkaTemplate) {
        DeadLetterPublishingRecoverer recoverer =
                new DeadLetterPublishingRecoverer(dlqKafkaTemplate);

        FixedBackOff backOff = new FixedBackOff(1000L, 2L);

        return new DefaultErrorHandler(recoverer, backOff);
    }
}