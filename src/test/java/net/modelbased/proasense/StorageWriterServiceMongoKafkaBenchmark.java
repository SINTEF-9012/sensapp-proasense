/**
 * Copyright 2015 Brian Elves�ter <brian.elvesater@sintef.no>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.modelbased.proasense;

import eu.proasense.internal.AnomalyEvent;
import eu.proasense.internal.DerivedEvent;
import eu.proasense.internal.FeedbackEvent;
import eu.proasense.internal.PredictedEvent;
import eu.proasense.internal.RecommendationEvent;
import eu.proasense.internal.SimpleEvent;

import net.modelbased.proasense.storage.EventHeartbeat;
import net.modelbased.proasense.storage.EventDocument;
import net.modelbased.proasense.storage.EventListenerKafkaFilter;
import net.modelbased.proasense.storage.EventListenerKafkaTopic;
import net.modelbased.proasense.storage.EventWriterMongoAsync;
import net.modelbased.proasense.storage.EventWriterMongoSync;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class StorageWriterServiceMongoKafkaBenchmark {
    private Properties clientProperties;


    public StorageWriterServiceMongoKafkaBenchmark() {
    }


    private Properties loadClientProperties() {
        clientProperties = new Properties();
        String propFilename = "client.properties";
        InputStream inputStream = getClass().getClassLoader().getResourceAsStream(propFilename);

        try {
            if (inputStream != null) {
                clientProperties.load(inputStream);
            } else
                throw new FileNotFoundException("Property file: '" + propFilename + "' not found in classpath.");
        }
        catch (IOException e) {
            System.out.println("Exception:" + e.getMessage());
        }

        return clientProperties;
    }


    public static void main(String[] args) {
        // Get benchmark properties
        StorageWriterServiceMongoKafkaBenchmark benchmark = new StorageWriterServiceMongoKafkaBenchmark();
        benchmark.loadClientProperties();

        // Mongo properties
//        String mongoURL = "mongodb://127.0.0.1:27017";
//        String mongoURL = "mongodb://89.216.116.44:27017";
//        String mongoURL = "mongodb://192.168.11.25:27017";

        // Kafka broker configuration properties
        String zooKeeper = benchmark.clientProperties.getProperty("zookeeper.connect");
        String groupId = "StorageWriterServiceMongoKafkaBenchmark";

        // Kafka event generators configuration properties
        String SIMPLEEVENT_TOPICFILTER = benchmark.clientProperties.getProperty("proasense.benchmark.event.simple.topicfilter");
        String DERIVEDEVENT_TOPIC = benchmark.clientProperties.getProperty("proasense.benchmark.event.derived.topic");
        String PREDICTEDEVENT_TOPIC = benchmark.clientProperties.getProperty("proasense.benchmark.event.predicted.topic");
        String ANOMALYEVENT_TOPIC = benchmark.clientProperties.getProperty("proasense.benchmark.event.anomaly.topic");
        String RECOMMENDATIONEVENT_TOPIC = benchmark.clientProperties.getProperty("proasense.benchmark.event.recommendation.topic");
        String FEEDBACKEVENT_TOPIC = benchmark.clientProperties.getProperty("proasense.benchmark.event.feedback.topic");

        int NO_SIMPLEEVENT_GENERATORS = new Integer(benchmark.clientProperties.getProperty("proasense.benchmark.event.simple.generators")).intValue();
        int NO_SIMPLEEVENT_RATE = new Integer(benchmark.clientProperties.getProperty("proasense.benchmark.event.simple.rate")).intValue();
        int NO_SIMPLEEVENT_MESSAGES = new Integer(benchmark.clientProperties.getProperty("proasense.benchmark.event.simple.messages")).intValue();

        int NO_DERIVEDEVENT_GENERATORS = new Integer(benchmark.clientProperties.getProperty("proasense.benchmark.event.derived.generators")).intValue();
        int NO_DERIVEDEVENT_RATE = new Integer(benchmark.clientProperties.getProperty("proasense.benchmark.event.derived.rate")).intValue();
        int NO_DERIVEDEVENT_MESSAGES = new Integer(benchmark.clientProperties.getProperty("proasense.benchmark.event.derived.messages")).intValue();

        int NO_PREDICTEDEVENT_GENERATORS = new Integer(benchmark.clientProperties.getProperty("proasense.benchmark.event.predicted.generators")).intValue();
        int NO_PREDICTEDEVENT_RATE = new Integer(benchmark.clientProperties.getProperty("proasense.benchmark.event.predicted.rate")).intValue();
        int NO_PREDICTEDEVENT_MESSAGES = new Integer(benchmark.clientProperties.getProperty("proasense.benchmark.event.predicted.messages")).intValue();

        int NO_ANOMALYEVENT_GENERATORS = new Integer(benchmark.clientProperties.getProperty("proasense.benchmark.event.anomaly.generators")).intValue();
        int NO_ANOMALYEVENT_RATE = new Integer(benchmark.clientProperties.getProperty("proasense.benchmark.event.anomaly.rate")).intValue();
        int NO_ANOMALYEVENT_MESSAGES = new Integer(benchmark.clientProperties.getProperty("proasense.benchmark.event.anomaly.messages")).intValue();

        int NO_RECOMMENDATIONEVENT_GENERATORS = new Integer(benchmark.clientProperties.getProperty("proasense.benchmark.event.recommendation.generators")).intValue();
        int NO_RECOMMENDATIONEVENT_RATE = new Integer(benchmark.clientProperties.getProperty("proasense.benchmark.event.recommendation.rate")).intValue();
        int NO_RECOMMENDATIONEVENT_MESSAGES = new Integer(benchmark.clientProperties.getProperty("proasense.benchmark.event.recommendation.messages")).intValue();

        int NO_FEEDBACKEVENT_GENERATORS = new Integer(benchmark.clientProperties.getProperty("proasense.benchmark.event.feedback.generators")).intValue();
        int NO_FEEDBACKEVENT_RATE = new Integer(benchmark.clientProperties.getProperty("proasense.benchmark.event.feedback.rate")).intValue();
        int NO_FEEDBACKEVENT_MESSAGES = new Integer(benchmark.clientProperties.getProperty("proasense.benchmark.event.feedback.messages")).intValue();

        // Total number of threads
        int NO_TOTAL_THREADS = NO_SIMPLEEVENT_GENERATORS + NO_DERIVEDEVENT_GENERATORS
                + NO_PREDICTEDEVENT_GENERATORS + NO_ANOMALYEVENT_GENERATORS + NO_RECOMMENDATIONEVENT_GENERATORS + NO_FEEDBACKEVENT_GENERATORS;

        // Create executor environment for threads
        ArrayList<Runnable> workers = new ArrayList<Runnable>(NO_TOTAL_THREADS);
        ExecutorService executor = Executors.newFixedThreadPool(NO_TOTAL_THREADS);

        // Create threads for random simple event generators
        Map<String, Integer> topicMap = new HashMap<String, Integer>();
        for (int i = 0; i < NO_SIMPLEEVENT_GENERATORS; i++) {
            workers.add(new RandomEventKafkaGenerator<SimpleEvent>(SimpleEvent.class, zooKeeper, groupId, SIMPLEEVENT_TOPICFILTER + i, "mhwirth." + i, NO_SIMPLEEVENT_RATE, NO_SIMPLEEVENT_MESSAGES));
        }

        // Create threads for random derived event generators
        for (int i = 0; i < NO_DERIVEDEVENT_GENERATORS; i++) {
            workers.add(new RandomEventKafkaGenerator<DerivedEvent>(DerivedEvent.class, zooKeeper, groupId, DERIVEDEVENT_TOPIC, "mhwirth." + i, NO_DERIVEDEVENT_RATE, NO_DERIVEDEVENT_MESSAGES));
        }

        // Create threads for random predicted event generators
        for (int i = 0; i < NO_PREDICTEDEVENT_GENERATORS; i++) {
            workers.add(new RandomEventKafkaGenerator<PredictedEvent>(PredictedEvent.class, zooKeeper, groupId, PREDICTEDEVENT_TOPIC, "mhwirth." + i, NO_PREDICTEDEVENT_RATE, NO_PREDICTEDEVENT_MESSAGES));
        }

        // Create threads for random anomaly event generators
        for (int i = 0; i < NO_ANOMALYEVENT_GENERATORS; i++) {
            workers.add(new RandomEventKafkaGenerator<AnomalyEvent>(AnomalyEvent.class, zooKeeper, groupId, ANOMALYEVENT_TOPIC, "mhwirth." + i, NO_ANOMALYEVENT_RATE, NO_ANOMALYEVENT_MESSAGES));
        }

        // Create threads for random recommendation event generators
        for (int i = 0; i < NO_RECOMMENDATIONEVENT_GENERATORS; i++) {
            workers.add(new RandomEventKafkaGenerator<RecommendationEvent>(RecommendationEvent.class, zooKeeper, groupId, RECOMMENDATIONEVENT_TOPIC, "mhwirth." + i, NO_RECOMMENDATIONEVENT_RATE, NO_RECOMMENDATIONEVENT_MESSAGES));
        }

        // Create threads for random feedback event generators
        for (int i = 0; i < NO_FEEDBACKEVENT_GENERATORS; i++) {
            workers.add(new RandomEventKafkaGenerator<FeedbackEvent>(FeedbackEvent.class, zooKeeper, groupId, FEEDBACKEVENT_TOPIC, "mhwirth." + i, NO_FEEDBACKEVENT_RATE, NO_FEEDBACKEVENT_MESSAGES));
        }

        // Execute all threads
        for (int i = 0; i < NO_TOTAL_THREADS; i++) {
            executor.execute(workers.get(i));
        }

        // Shut down executor
        executor.shutdown();
    }

}
