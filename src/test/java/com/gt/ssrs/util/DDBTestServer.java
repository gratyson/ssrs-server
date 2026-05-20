package com.gt.ssrs.util;

import com.amazonaws.services.dynamodbv2.local.main.ServerRunner;
import com.amazonaws.services.dynamodbv2.local.server.DynamoDBProxyServer;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import java.net.URI;

import static org.assertj.core.api.Fail.fail;

public class DDBTestServer<T> implements AutoCloseable {

    private static final int DEFAULT_PORT = 8000;
    private static final String DEFAULT_URI = "http://localhost:" + DEFAULT_PORT;
    private static final Region DEFAULT_REGION = Region.US_WEST_2;
    private static final int STARTUP_WAIT_MILLIS = 100;

    private DynamoDBProxyServer dynamoDBProxyServer;
    private boolean isRunning;

    private DynamoDbClient dynamoDbClient;
    private DynamoDbEnhancedClient dynamoDbEnhancedClient;
    private DynamoDbTable<T> dynamoDbTable;

    private DDBTestServer() {
        isRunning = false;
    }

    public static <T> DDBTestServer<T> withTable(String tableName, Class<T> tableClass) {
        DDBTestServer ddbTestUtil = new DDBTestServer();
        ddbTestUtil.init(DEFAULT_PORT, DEFAULT_URI, DEFAULT_REGION);
        ddbTestUtil.createTable(tableName, TableSchema.fromImmutableClass(tableClass));

        return ddbTestUtil;
    }

    public DynamoDbClient dynamoDbClient() {
        return dynamoDbClient;
    }

    public DynamoDbEnhancedClient dynamoDbEnhancedClient() {
        return dynamoDbEnhancedClient;
    }

    public DynamoDbTable<T> dynamoDbTable() {
        return dynamoDbTable;
    }

    @Override
    public void close() throws Exception {
        if (isRunning) {
            dynamoDbEnhancedClient.dynamoDbClient().close();
            dynamoDBProxyServer.stop();
            isRunning = false;
        }
    }

    private void init(int port, String uri, Region region) {
        try {
            dynamoDBProxyServer = ServerRunner.createServerFromCommandLineArgs(new String[]{ "-inMemory", "-port", Integer.toString(port) });
            dynamoDBProxyServer.start();
            isRunning = true;

            Thread.sleep(STARTUP_WAIT_MILLIS);
        } catch (Exception e) {
            fail("Failed to create DynamoDB proxy server");
        }

        dynamoDbClient = DynamoDbClient.builder()
                .endpointOverride(URI.create(uri))
                .httpClient(UrlConnectionHttpClient.builder().build())
                .region(region)
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("dummyKey", "dummySecret")))
                .build();

        dynamoDbEnhancedClient = DynamoDbEnhancedClient.builder()
                .dynamoDbClient(dynamoDbClient)
                .build();
    }

    private void createTable(String tableName, TableSchema<T> tableSchema) {
        dynamoDbTable = dynamoDbEnhancedClient.table(tableName, tableSchema);
        dynamoDbTable.createTable();
    }
}
