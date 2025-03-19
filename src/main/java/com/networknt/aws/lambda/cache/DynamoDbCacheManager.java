package com.networknt.aws.lambda.cache;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.regions.Region;
import com.networknt.aws.lambda.utility.LambdaEnvVariables;
import com.networknt.cache.CacheManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * This is the cache manager that can survive the cold start of the lambda function.
 */
public class DynamoDbCacheManager implements CacheManager {
    private static final Logger LOG = LoggerFactory.getLogger(DynamoDbCacheManager.class);
    private static final String HASH_ID_KEY = "Id";
    private static final int TABLE_LIST_LIMIT = 100;

    private static final Map<String, String> tables = new HashMap<>();
    private final DynamoDbClient dynamoClient;
    boolean tableInitiated;

    @Override
    public void addCache(String cacheName, long maxSize, long expiryInMinutes) {
        if(!doesTableExist(cacheName)) {
            LOG.debug("Table does not exist so we need to create it....");
            createCacheTable(cacheName);
        }
        tables.put(cacheName, cacheName);
    }

    @Override
    public Map<Object, Object> getCache(String cacheName) {
        String applicationId = cacheName.split(":")[0];
        String tableName = cacheName.split(":")[1];
        Map<String, AttributeValue> entry;
        try {
            entry = dynamoClient.getItem(GetItemRequest.builder()
                    .tableName(tableName)
                    .key(Collections.singletonMap(HASH_ID_KEY, AttributeValue.builder().s(applicationId).build()))
                    .build()).item();
            if (entry == null)
                return null;
        } catch (NullPointerException e) {
            return null;
        }
        return convertMap(entry);
    }

    public static Map<Object, Object> convertMap(Map<String, AttributeValue> originalMap) {
        Map<Object, Object> convertedMap = new HashMap<>();
        originalMap.forEach((k, v) -> convertedMap.put(k, v.s()));
        return convertedMap;
    }

    @Override
    public void put(String cacheName, String key, Object value) {
        String applicationId = cacheName.split(":")[0];
        String tableName = cacheName.split(":")[1];

        LOG.debug("Updating table entry of applicationId: {}, table name: {}, attribute key: {} and value: {}", applicationId, tableName, key, value);

        Map<String, AttributeValue> itemKey = new HashMap<>();
        itemKey.put(HASH_ID_KEY, AttributeValue.builder().s(applicationId).build());

        Map<String, AttributeValueUpdate> attributeUpdates = new HashMap<>();
        attributeUpdates.put(key, AttributeValueUpdate.builder()
                .action(AttributeAction.PUT)
                .value(AttributeValue.builder().s((String) value).build())
                .build());

        dynamoClient.updateItem(UpdateItemRequest.builder()
                .tableName(tableName)
                .key(itemKey)
                .attributeUpdates(attributeUpdates)
                .build());
    }

    @Override
    public Object get(String cacheName, String key) {
        String applicationId = cacheName.split(":")[0];
        String tableName = cacheName.split(":")[1];

        Map<String, AttributeValue> entry;
        try {
            entry = dynamoClient.getItem(GetItemRequest.builder()
                    .tableName(tableName)
                    .key(Collections.singletonMap(HASH_ID_KEY, AttributeValue.builder().s(applicationId).build()))
                    .build()).item();
            if (entry == null)
                return null;
        } catch (NullPointerException e) {
            return null;
        }
        return entry.get(key).s();
    }

    @Override
    public void delete(String cacheName, String key) {
        // Implement delete logic if needed
    }

    @Override
    public void removeCache(String cacheName) {
        if(tables.containsKey(cacheName)) {
            tables.remove(cacheName);
            try {
                deleteTable(cacheName);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public int getSize(String cacheName) {
        return 0;
    }

    public DynamoDbCacheManager() {
        if(LOG.isInfoEnabled())
            LOG.info("DynamoDbCacheManager is constructed.");

        this.tableInitiated = false;
        this.dynamoClient = DynamoDbClient.builder()
                .region(Region.of(System.getenv(LambdaEnvVariables.AWS_REGION)))
                .build();
    }

    private void createCacheTable(String tableName) {
        LOG.debug("Attempting to create new cache table '{}'", tableName);
        CreateTableRequest createTableRequest = CreateTableRequest.builder()
                .tableName(tableName)
                .keySchema(KeySchemaElement.builder()
                        .attributeName(HASH_ID_KEY)
                        .keyType(KeyType.HASH)
                        .build())
                .attributeDefinitions(AttributeDefinition.builder()
                        .attributeName(HASH_ID_KEY)
                        .attributeType(ScalarAttributeType.S)
                        .build())
                .provisionedThroughput(ProvisionedThroughput.builder()
                        .readCapacityUnits(0L)
                        .writeCapacityUnits(0L)
                        .build())
                .build();

        dynamoClient.createTable(createTableRequest);
        try {
            LOG.debug("Waiting for table status to be active...");
            dynamoClient.waiter().waitUntilTableExists(r -> r.tableName(tableName));
        } catch (Exception e) {
            // Handle exception
        }
    }

    public void deleteTable(String tableName) throws InterruptedException {
        if (!doesTableExist(tableName)) {
            LOG.debug("Table does not exist so we do not need to delete it....");
            return;
        }

        dynamoClient.deleteTable(DeleteTableRequest.builder().tableName(tableName).build());
        dynamoClient.waiter().waitUntilTableNotExists(r -> r.tableName(tableName));
    }

    public boolean doesTableExist(String tableName) {
        ListTablesResponse tables = dynamoClient.listTables(ListTablesRequest.builder().limit(TABLE_LIST_LIMIT).build());
        return tables.tableNames().contains(tableName);
    }
}
