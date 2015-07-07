/**
 * Copyright (C) 2014-2015 SINTEF
 *
 *     Brian Elvesæter <brian.elvesater@sintef.no>
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
package net.modelbased.proasense.storage;

import com.mongodb.MongoClient;
import com.mongodb.MongoClientURI;
import com.mongodb.async.client.MongoIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;

import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.BlockingQueue;


public class EventWriterMongoSync implements Runnable {
    private Properties mongoProperties;
    private MongoClient mongoClient;
    private BlockingQueue<EventDocument> queue;
    private String mongoURL;
    private int bulkSize;
    private int maxWait;
    private boolean isBenchmarkLogfile;
    private int logSize = 10000;
    private Writer logfileWriter;
    private int threadNumber;


    public EventWriterMongoSync(BlockingQueue<EventDocument> queue, String mongoURL, int bulkSize, int maxWait) {
        this.queue = queue;
        this.mongoURL = mongoURL;
        this.bulkSize = bulkSize;
        this.maxWait = maxWait;
    }


    public EventWriterMongoSync(BlockingQueue<EventDocument> queue, String mongoURL, int bulkSize, int maxWait, boolean isBenchmarkLogfile, int threadNumber) {
        this.queue = queue;
        this.mongoURL = mongoURL;
        this.bulkSize = bulkSize;
        this.maxWait = maxWait;
        this.isBenchmarkLogfile = isBenchmarkLogfile;
        this.threadNumber = threadNumber;
    }


    public void run() {
        // Connect to MongoDB database
        MongoClient mongoClient = new MongoClient(new MongoClientURI(mongoURL));
        MongoDatabase database = mongoClient.getDatabase(EventProperties.STORAGE_DATABASE_NAME);

        // Create hash map of collections
        Map<String, MongoCollection<Document>> collectionMap = new HashMap<String, MongoCollection<Document>>();

        int cnt = 0;
        long timer1 = System.currentTimeMillis();
        long timer2 = System.currentTimeMillis();
        boolean skipLog = false;

        Map<String, List<Document>> documentMap = new HashMap<String, List<Document>>();
        try {
            if (isBenchmarkLogfile)
                logfileWriter = new BufferedWriter(new OutputStreamWriter(new FileOutputStream("EventWriterMongoSync_benchmark_" + this.threadNumber + ".txt"), "ISO-8859-1"));

            while (true) {
                long timeoutExpired = System.currentTimeMillis() + this.maxWait;
                EventDocument eventDocument = queue.take();

                String collectionId = eventDocument.getCollectionId();

                if (!collectionId.matches(EventProperties.STORAGE_HEARTBEAT)) {
                    cnt++;
                    skipLog = false;
                }
                else
                    skipLog = true;

                // Add data for bulk write
                if (!collectionId.matches(EventProperties.STORAGE_HEARTBEAT)) {
                    Document document = eventDocument.getDocument();

                    if (!collectionMap.containsKey(collectionId)) {
                        collectionMap.put(collectionId, database.getCollection(collectionId));
                        List<Document> documentList = new ArrayList<Document>();
                        documentMap.put(collectionId, documentList);
                    }

                    documentMap.get(collectionId).add(document);
                }
                // Write data if heartbeat timeout is received
                else {
                    for (Map.Entry<String, MongoCollection<Document>> entry : collectionMap.entrySet()) {
                        String key = entry.getKey();
                        if (!documentMap.get(key).isEmpty()) {
                            collectionMap.get(key).insertMany(documentMap.get(key));
                            documentMap.get(key).clear();
                        }
                    }
                }

                // Write data if bulk size or max wait (ms) is reached
                if ((cnt % this.bulkSize == 0) || (System.currentTimeMillis() >= timeoutExpired)) {
                    for (Map.Entry<String, MongoCollection<Document>> entry : collectionMap.entrySet()) {
                        String key = entry.getKey();
                        if (!documentMap.get(key).isEmpty()) {
                            collectionMap.get(key).insertMany(documentMap.get(key));
                            documentMap.get(key).clear();
                        }
                    }
                }

                // Benchmark output
                if ((!skipLog) && (cnt % this.logSize == 0)) {
                    timer2 = System.currentTimeMillis();
                    long difference = timer2 - timer1;

                    if (difference != 0) {
                        long average = (this.logSize * 1000) / (timer2 - timer1);

                        System.out.println("Benchmark: ");
                        System.out.println("  Total records written: " + cnt);
                        System.out.println("  Average records/s: " + average);
                        timer1 = timer2;

                        if (isBenchmarkLogfile) {
                            logfileWriter.write(cnt + "," + average + System.getProperty("line.separator"));
                            logfileWriter.flush();
                        }
                    }
                }
            }
        } catch (InterruptedException e) {
            System.out.println(e.getClass().getName() + ": " + e.getMessage());
        } catch (IOException e) {
            System.out.println(e.getClass().getName() + ": " + e.getMessage());
        } finally {
            if (isBenchmarkLogfile)
                try {
                    logfileWriter.close();
                }
                catch (IOException e) {
                    System.out.println(e.getClass().getName() + ": " + e.getMessage());
                }
        }

        mongoClient.close();
    }

}
