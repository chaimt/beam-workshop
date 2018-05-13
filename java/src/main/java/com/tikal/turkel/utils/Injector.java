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
 * This is a generator that simulates usage data from a mobile game, and either publishes the data
 * to a pubsub topic or writes it to a file.
 *
 * <p>The general model used by the generator is the following. There is a set of teams with team
 * members. Each member is scoring points for their team. After some period, a team will dissolve
 * and a new one will be created in its place. There is also a set of 'Robots', or spammer users.
 * They hop from team to team. The robots are set to have a higher 'click rate' (generate more
 * events) than the regular team members.
 *
 * <p>Each generated line of data has the following form:
 * username,teamname,score,timestamp_in_ms,readable_time
 * e.g.:
 * user2_AsparagusPig,AsparagusPig,10,1445230923951,2015-11-02 09:09:28.224
 *
 * <p>The Injector writes either to a PubSub topic, or a file. It will use the PubSub topic if
 * specified. It takes the following arguments:
 * {@code Injector project-name (topic-name|none) (filename|none)}.
 *
 * <p>To run the Injector in the mode where it publishes to PubSub, you will need to authenticate
 * locally using project-based service account credentials to avoid running over PubSub
 * quota.
 * See https://developers.google.com/identity/protocols/application-default-credentials
 * for more information on using service account credentials. Set the GOOGLE_APPLICATION_CREDENTIALS
 * environment variable to point to your downloaded service account credentials before starting the
 * program, e.g.:
 * {@code export GOOGLE_APPLICATION_CREDENTIALS=/path/to/your/credentials-key.json}.
 * If you do not do this, then your injector will only run for a few minutes on your
 * 'user account' credentials before you will start to see quota error messages like:
 * "Request throttled due to user QPS limit being reached", and see this exception:
 * ".com.google.api.client.googleapis.json.GoogleJsonResponseException: 429 Too Many Requests".
 * Once you've set up your credentials, run the Injector like this":
 * <pre>{@code
 * Injector <project-name> <topic-name> none
 * }
 * </pre>
 * The pubsub topic will be created if it does not exist.
 *
 * <p>To run the injector in write-to-file-mode, set the topic name to "none" and specify the
 * filename:
 * <pre>{@code
 * Injector <project-name> none <filename>
 * }
 * </pre>
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

        // The Injector writes either to a PubSub topic, or a file. It will use the PubSub topic if
        // specified; otherwise, it will try to write to a file.
        // Create the PubSub client.
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
