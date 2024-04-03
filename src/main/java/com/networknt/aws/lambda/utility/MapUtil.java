package com.networknt.aws.lambda.utility;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class MapUtil {
    // Method to get value from HashMap with case-insensitive key lookup
    public static <V> Optional<V> getValueIgnoreCase(Map<String, V> map, String key) {
        for (Map.Entry<String, V> entry : map.entrySet()) {
            if (Objects.equals(entry.getKey().toLowerCase(), key.toLowerCase())) {
                return Optional.of(entry.getValue());
            }
        }
        return Optional.empty();
    }
}
