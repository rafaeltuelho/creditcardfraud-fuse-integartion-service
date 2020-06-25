package com.redhat.demo.dm.ccfraud.integration;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.redhat.demo.dm.ccfraud.CreditCardTransactionHelper;
import com.redhat.demo.dm.ccfraud.domain.CreditCardTransaction;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.kafka.KafkaConstants;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.apache.camel.model.rest.RestBindingMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * A simple Camel REST DSL route that implements the greetings service.
 * 
 */
@Component
public class CamelRouter extends RouteBuilder {

    private static final Logger LOG = LoggerFactory.getLogger(CamelRouter.class);

    @Value("${kie.decision.container.id}") 
    String decisionContainerId;
    @Value("${kie.decision.session.name}") 
	String decisionSessionName;
    @Value("${kie.process.container.id}") 
    String processContainerId;
    @Value("${kie.process.definition.id}") 
	String processDefinitionId;
    
    @Value("${kafka.topic:test}") 
	String kafkaTopic;
    @Value("${kafka.host:localhost}") 
	String kafkaHost;
    @Value("${kafka.port:9092}") 
    String kafkaPort;

    @Value("${camel.seda.consumers}")
    String sedaConsumers;

    private static final String KAFKA_SERIALIZER_CLASS_CONFIG = 
        "org.apache.kafka.common.serialization.ByteArraySerializer";
    private static final String KAFKA_DESERIALIZER_CLASS_CONFIG = 
        "org.apache.kafka.common.serialization.ByteArrayDeserializer";
    
    @Override
    public void configure() throws Exception {

        // @formatter:off
        restConfiguration()
                .apiContextPath("/api-doc")
                .apiProperty("api.title", "Integration Service REST API")
                .apiProperty("api.version", "1.0")
                .apiProperty("cors", "true")
                .apiProperty("base.path", "camel/")
                .apiProperty("api.path", "/")
                .apiProperty("host", "")
                .apiContextRouteId("doc-api")
            .component("servlet")
            .bindingMode(RestBindingMode.json);
        
        rest("/transaction").description("Create a new Transaction and send it to Kafka Topic")
            .consumes("application/json")
            .produces("application/json")
            .post().type(CreditCardTransaction.class)
                .route().routeId("transaction-api")
                .to("direct:publishToKafka");

        // Direct routes
        from("direct:publishToKafka")
            .routeId("kafkaPublisher")
            .marshal().json(JsonLibrary.Jackson, CreditCardTransaction.class)
            .log("publishing [ ${body} ] to kafka topic}")
            .setHeader(KafkaConstants.KEY, constant("cct")) // Key of the message
            .toF("kafka:%s?brokers=%s:%s&serializerClass=%s", kafkaTopic, kafkaHost, kafkaPort, KAFKA_SERIALIZER_CLASS_CONFIG);
            
        fromF("kafka:%s?brokers=%s:%s&valueDeserializer=%s", kafkaTopic, kafkaHost, kafkaPort, KAFKA_DESERIALIZER_CLASS_CONFIG)
            .routeId("kafkaSubscriber")
            .unmarshal().json(JsonLibrary.Jackson, CreditCardTransaction.class)
            .log("Message received from Kafka : ${body}")
            .log("    on the topic ${headers[kafka.TOPIC]}")
            .log("    on the partition ${headers[kafka.PARTITION]}")
            .log("    with the offset ${headers[kafka.OFFSET]}")
            .log("    with the key ${headers[kafka.KEY]}")  
            .log("\n Call the decision server")
            .to("seda:makeDecision");

        fromF("seda:makeDecision?concurrentConsumers=%s", sedaConsumers)
            .routeId("makeDecision")
            // .process(e -> {
            //     LOG.debug("Decision request Body: " + e.getIn().getBody());
            //     CreditCardTransaction ccTransaction = e.getIn().getBody(CreditCardTransaction.class);

            //     Map<String, Object> decisionFacts = new HashMap<>();
            //     //decisionFacts.put(Integer.toString(trigger.getTriggerId()), ccTransaction);
            //     e.getIn().setBody(decisionFacts);
            // }) // call decision service
            .to("bean:creditCardTransactionHelper?method=processTransaction(${body})")
            .log("Decision Results: [ ${body} ]");
            
        // @formatter:on
    }

}