/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.tikal.turkel.utils;

import com.google.api.services.pubsub.Pubsub;
import com.google.api.services.pubsub.model.PublishRequest;
import com.google.api.services.pubsub.model.PubsubMessage;
import reactor.core.publisher.Flux;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.BaseStream;

/**
 * a simple injector that reads a file, and injects each line as a separate message
 */
class Injector {
    private static Pubsub pubsub;
    private static String topic;
    private static String project;


    private static Flux<String> fromPath(Path path) {
        return Flux.using(() -> Files.lines(path),
                Flux::fromStream,
                BaseStream::close
        );
    }

    public static void main(String[] args) throws IOException {
        if (args.length < 4) {
            System.out.println("Usage: Injector project-name (topic-name|none) (filename|none)");
            System.exit(1);
        }
        project = args[0];
        String topicName = args[1];
        String fileName = args[2];
        String googleCredentials = args[3];

        System.out.println("************************");
        System.out.println("project\t\t\t " + project);
        System.out.println("topicName\t\t " + topicName);
        System.out.println("fileName\t\t " + fileName);
        System.out.println("googleCredentials\t " + googleCredentials);
        System.out.println("************************");

        System.getProperties().setProperty("GOOGLE_APPLICATION_CREDENTIALS", googleCredentials);

        pubsub = InjectorUtils.getClient();
        // Create the PubSub topic as necessary.
        topic = InjectorUtils.getFullyQualifiedTopicName(project, topicName);
        InjectorUtils.createTopic(pubsub, topic);
        System.out.println("Injecting to topic: " + topic);

        AtomicInteger sentCount = new AtomicInteger();
        Flux<String> lines = fromPath((new File(fileName)).toPath());
        lines.map(line -> {
            PubsubMessage pubsubMessage = null;
            try {
                pubsubMessage = new PubsubMessage()
                        .encodeData(line.getBytes("UTF-8"));
            } catch (UnsupportedEncodingException e) {
            }
            return pubsubMessage;
        })
                .buffer(1000)
                .subscribe(pubsubMessages -> {
                    PublishRequest publishRequest = new PublishRequest();
                    publishRequest.setMessages(pubsubMessages);
                    try {
                        pubsub.projects().topics().publish(topic, publishRequest).execute();
                        System.out.println(String.format("sending %d/%d",pubsubMessages.size(),sentCount.addAndGet(pubsubMessages.size())));
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                });
    }
}
