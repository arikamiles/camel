/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.sjms.tx;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.sjms.SjmsComponent;
import org.apache.camel.component.sjms.jms.ConnectionFactoryResource;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class TransactedTopicProducerTest extends CamelTestSupport {

    @Produce
    protected ProducerTemplate template;
    protected ActiveMQConnectionFactory connectionFactory = new ActiveMQConnectionFactory("vm://broker?broker.persistent=false&broker.useJmx=true");

    public TransactedTopicProducerTest() {
    }

    @Override
    protected boolean useJmx() {
        return false;
    }

    @Test
    public void testTransactedProducer() throws Exception {
        
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World 2");

        template.sendBodyAndHeader("direct:start", "Hello World 1", "isfailed", true);
        template.sendBodyAndHeader("direct:start", "Hello World 2", "isfailed", false);

        mock.assertIsSatisfied();
    }


    /*
     * @see org.apache.camel.test.junit4.CamelTestSupport#createCamelContext()
     * @return
     * @throws Exception
     */
    @Override
    protected CamelContext createCamelContext() throws Exception {
        ActiveMQConnectionFactory connectionFactory = new ActiveMQConnectionFactory("vm://broker?broker.persistent=false&broker.useJmx=true");
        ConnectionFactoryResource connectionResource = new ConnectionFactoryResource();
        connectionResource.setConnectionFactory(connectionFactory);
        connectionResource.setClientId("test-connection-1");
        CamelContext camelContext = super.createCamelContext();
        SjmsComponent component = new SjmsComponent();
        component.setConnectionResource(connectionResource);
        component.setMaxConnections(1);
        camelContext.addComponent("sjms", component);
        return camelContext;
    }

    /*
     * @see org.apache.camel.test.junit4.CamelTestSupport#createRouteBuilder()
     * @return
     * @throws Exception
     */
    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() {

                from("direct:start")
                    .to("sjms:topic:test.topic?transacted=true")
                    .process(
                         new Processor() {
                            @Override
                            public void process(Exchange exchange) throws Exception {
                                if (exchange.getIn().getHeader("isfailed", Boolean.class)) {
                                    log.info("We failed.  Should roll back.");
                                    exchange.getOut().setFault(true);
                                } else {
                                    log.info("We passed.  Should commit.");
                                }
                            }
                        });
                
                from("sjms:topic:test.topic?durableSubscriptionId=bar&transacted=true")
                    .to("mock:result");
                
            }
        };
    }
}