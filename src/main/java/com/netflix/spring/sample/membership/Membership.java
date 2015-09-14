/*
 * Copyright 2015 Netflix, Inc.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.netflix.spring.sample.membership;

import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.cloud.netflix.eureka.EnableEurekaClient;
import org.springframework.cloud.netflix.eureka.EurekaStatusChangedEvent;
import org.springframework.cloud.netflix.metrics.spectator.EnableSpectator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.integration.annotation.IntegrationComponentScan;
import org.springframework.integration.annotation.MessagingGateway;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.config.EnableIntegrationManagement;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.IntegrationFlows;
import org.springframework.integration.dsl.channel.MessageChannels;
import org.springframework.integration.dsl.core.Pollers;
import org.springframework.integration.endpoint.MethodInvokingMessageSource;
import org.springframework.integration.support.management.DefaultMetricsFactory;
import org.springframework.integration.support.management.MetricsFactory;
import org.springframework.messaging.MessageChannel;
import org.springframework.web.bind.annotation.*;

import com.google.common.collect.ImmutableMap;
import com.netflix.appinfo.InstanceInfo.InstanceStatus;

@SpringBootApplication
@EnableEurekaClient
@EnableSpectator
public class Membership {
    public static void main(String[] args) {
        new SpringApplicationBuilder(Membership.class, IntegrationConfig.class).web(true).run(args);
    }
}

@Data
@NoArgsConstructor
@AllArgsConstructor
class Member {
    String user;
    Integer age;
}

@RestController
@RequestMapping("/api/member")
class MembershipController {
    final Map<String, Member> memberStore = ImmutableMap.of(
        "jschneider", new Member("jschneider", 10),
        "twicksell", new Member("twicksell", 30)
    );

    @RequestMapping(method = RequestMethod.POST)
    public Member register(@RequestBody Member member) {
        memberStore.put(member.getUser(), member);
        return member;
    }

    @RequestMapping("/{user}")
    Member login(@PathVariable String user) {
    	delay();
        return memberStore.get(user);
    }
    
	private Random rand = new Random();
	private void delay()
	{
		try {
			Thread.sleep((int)((Math.abs(2 + rand.nextGaussian()*15))*100));
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}
	}

}


@Configuration
@IntegrationComponentScan(basePackageClasses=ControlBusGateway.class)
@EnableIntegrationManagement(countsEnabled = "*", statsEnabled = "*", metricsFactory = "metricsFactory")
class IntegrationConfig {
	
	@Autowired
	ControlBusGateway controlChannel;
	
	@EventListener
	public void onEurekaStatusDown(EurekaStatusChangedEvent event)
	{
		if(event.getStatus() == InstanceStatus.DOWN || event.getStatus() == InstanceStatus.OUT_OF_SERVICE)
		{
			System.out.println("Stopping adapters...");
			controlChannel.send("@*ChannelAdapter.stop()");
		}
	}
	
	@EventListener(classes=EurekaStatusChangedEvent.class, condition="#root.event.status.toString() == 'UP'")
	public void onEurekaStatusUp()
	{
		System.out.println("Starting adapters...");
		controlChannel.send("@*ChannelAdapter.start()");
	}
	
	@Bean
	public MetricsFactory metricsFactory() {
		return new DefaultMetricsFactory();
	}
	
	@Bean
	public MethodInvokingMessageSource integerMessageSource() {
		MethodInvokingMessageSource source = new MethodInvokingMessageSource();
		source.setObject(new AtomicInteger());
		source.setMethodName("getAndIncrement");
		return source;
	}

	@Bean
	public MessageChannel control() {
		return MessageChannels.direct().get();
	}

	@Bean
	public IntegrationFlow controlBusFlow() {
		return IntegrationFlows.from("control")
				.controlBus((config) -> config.autoStartup(true).id("controlBus"))
				.get();
	}

	@Bean
	public DirectChannel inputChannel() {
		return new DirectChannel();
	}
	
	@Bean
	public IntegrationFlow myFlow() {
		return IntegrationFlows.from(this.integerMessageSource(), c -> c.poller(Pollers.fixedRate(100)))
				.channel(this.inputChannel())
				.filter((Integer p) -> p > 0)
				.transform(Object::toString)
				.channel(MessageChannels.queue("sampleQueue"))
//				.handle(System.out::println)
				.get();
	}
	
  
}

@MessagingGateway(defaultRequestChannel = "control")
interface ControlBusGateway {
    void send(String command);
}