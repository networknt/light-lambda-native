package com.networknt.aws.lambda.cache;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.document.*;
import com.amazonaws.services.dynamodbv2.model.*;
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
    private static final String JWK = "jwk";
    private static final String JWT = "jwt";
    private static final String HASH_ID_KEY = "Id";
    private static final int TABLE_LIST_LIMIT = 100;

    private static final Map<String, Table> tables = new HashMap<>();
    private final AmazonDynamoDB dynamoClient;
    private final DynamoDB dynamoDB;
    boolean tableInitiated;

    @Override
    public void addCache(String cacheName, long maxSize, long expiryInMinutes) {
        if(!doesTableExist(cacheName)) {
            LOG.debug("Table does not exist so we need to create it....");
            createCacheTable(cacheName);
        }
        Table table = this.dynamoDB.getTable(cacheName);
        tables.put(cacheName, table);
    }

    @Override
    public void put(String cacheName, String key, Object value) {

        // cacheName is serviceId + ":" + jwt or jwk
        String applicationId = cacheName.split(":")[0];
        String tableName = cacheName.split(":")[1];

        LOG.debug("Updating table entry of applicationId: {}, table name: {}, attribute key: {} and value: {}", applicationId, tableName, key, value);

        Table table = tables.get(tableName);

        var item = table.getItem(HASH_ID_KEY, applicationId);

        if (item != null && item.getString(key) != null) {
            LOG.debug("Update spec....");

            // TODO - Update string value

        } else {
            /* primary key for item */
            var itemKey = new HashMap<String, AttributeValue>();
            itemKey.put(HASH_ID_KEY, new AttributeValue().withS(applicationId));

            /* attribute we are adding to item */
            var attributeUpdates = new HashMap<String, AttributeValueUpdate>();
            var update = new AttributeValueUpdate().withAction(AttributeAction.PUT).withValue(new AttributeValue().withS((String)value));
            attributeUpdates.put(key, update);

            /* send update request */
            var updateItemRequest = new UpdateItemRequest().withTableName(tableName).withKey(itemKey).withAttributeUpdates(attributeUpdates);
            var res = this.dynamoClient.updateItem(updateItemRequest);
            LOG.debug("RESULT: {}", res.toString());
        }

    }

    @Override
    public Object get(String cacheName, String key) {
        // cacheName is serviceId + ":" + jwt or jwk
        String applicationId = cacheName.split(":")[0];
        String tableName = cacheName.split(":")[1];

        /* see if the table contains our application id. */
        /* If not found, return null because we don't have a cache yet! */
        Item entry;
        try {
            Table table = tables.get(tableName);
            entry = table.getItem(HASH_ID_KEY, applicationId);
            if (entry == null)
                return null;
        } catch (NullPointerException e) {
            return null;
        }
        return entry.getString(key);
    }

    @Override
    public void delete(String cacheName, String key) {
        // cacheName is serviceId + ":" + jwt or jwk
        String applicationId = cacheName.split(":")[0];
        String tableName = cacheName.split(":")[1];

    }

    @Override
    public void removeCache(String cacheName) {
        Table table = tables.get(cacheName);
        if(table != null) {
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
        if(logger.isInfoEnabled())
            logger.info("DynamoDbCacheManager is constructed.");

        this.tableInitiated = false;
        this.dynamoClient = AmazonDynamoDBClientBuilder
                .standard()
                .withRegion(System.getenv(LambdaEnvVariables.AWS_REGION))
                .build();

        this.dynamoDB = new DynamoDB(dynamoClient);
    }


    /**
     * Creates dynamo db table. We check if the table exists before creating one.
     */
    private void createCacheTable(String tableName) {
        LOG.debug("Attempting to create new cache table '{}'", tableName);
        var attributeDefinitions = new ArrayList<AttributeDefinition>();
        attributeDefinitions.add(new AttributeDefinition()
                .withAttributeName(HASH_ID_KEY)
                .withAttributeType("S")
        );

        var keySchema = new ArrayList<KeySchemaElement>();
        keySchema.add(new KeySchemaElement()
                .withAttributeName(HASH_ID_KEY)
                .withKeyType(KeyType.HASH)
        );

        var createTableRequest = new CreateTableRequest()
                .withTableName(tableName)
                .withKeySchema(keySchema)
                .withAttributeDefinitions(attributeDefinitions)
                .withProvisionedThroughput(new ProvisionedThroughput(0L,0L));

        Table table = this.dynamoDB.createTable(createTableRequest);
        try {
            LOG.debug("Waiting for table status to be active...");
            table.waitForActive();
        } catch (InterruptedException e) {
           // nothing
        }

        //
    }

    /**
     * DEBUG FUNCTION - will be changed or deprecated in the future.
     */
    public void deleteTable(String tableName) throws InterruptedException {

        if (!this.doesTableExist(tableName)) {
            LOG.debug("Table does not exist so we do not need to delete it....");
            return;
        }

        var table = dynamoDB.getTable(tableName);
        table.delete();
        table.waitForDelete();
    }

    /**
     * Checks to see if the table exists.
     *
     * @return - returns true if the table exists
     */
    public boolean doesTableExist(String tableName) {
        var tables = this.dynamoClient.listTables(TABLE_LIST_LIMIT);
        return tables.getTableNames().contains(tableName);
    }


}
