package io.kmachine.rest.server.streams;

import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

public class DataResult {

    private static DataResult NOT_FOUND = new DataResult(null, null, null);

    private final Map<String, Object> data;
    private final String host;
    private final Integer port;

    private DataResult(Map<String, Object> data, String host, Integer port) {
        this.data = data;
        this.host = host;
        this.port = port;
    }

    public static DataResult found(Map<String, Object> data) {
        return new DataResult(data, null, null);
    }

    public static DataResult foundRemotely(String host, int port) {
        return new DataResult(null, host, port);
    }

    public static DataResult notFound() {
        return NOT_FOUND;
    }

    public Optional<Map<String, Object>> getData() {
        return Optional.ofNullable(data);
    }

    public Optional<String> getHost() {
        return Optional.ofNullable(host);
    }

    public OptionalInt getPort() {
        return port != null ? OptionalInt.of(port) : OptionalInt.empty();
    }
}
