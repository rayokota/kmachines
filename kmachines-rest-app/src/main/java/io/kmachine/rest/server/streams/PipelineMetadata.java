package io.kmachine.rest.server.streams;

import java.util.Set;

public class PipelineMetadata {

    private final String host;
    private final Set<String> partitions;

    public PipelineMetadata(String host, Set<String> partitions) {
        this.host = host;
        this.partitions = partitions;
    }
}
