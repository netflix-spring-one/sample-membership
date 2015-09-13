package com.netflix.spring.sample.membership;

import com.google.common.collect.ImmutableMap;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.test.ImportAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.cloud.netflix.eureka.EnableEurekaClient;
import org.springframework.cloud.netflix.metrics.atlas.AtlasAutoConfiguration;
import org.springframework.cloud.netflix.metrics.atlas.AtlasExporter;
import org.springframework.cloud.netflix.metrics.spectator.SpectatorAutoConfiguration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@SpringBootApplication
@EnableEurekaClient
//@EnableSpectator
//@EnableAtlas
@EnableScheduling
@ImportAutoConfiguration({SpectatorAutoConfiguration.class, AtlasAutoConfiguration.class})
public class Membership {
    public static void main(String[] args) {
        new SpringApplicationBuilder(Membership.class).web(true).run(args);
    }

    @Autowired
    AtlasExporter exporter;

    @Scheduled(fixedRate = 5000L)
    void pushMetricsToAtlas() {
        exporter.export();
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
        return memberStore.get(user);
    }
}