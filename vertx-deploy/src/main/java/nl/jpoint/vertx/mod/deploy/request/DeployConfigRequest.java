package nl.jpoint.vertx.mod.deploy.request;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class DeployConfigRequest extends ModuleRequest {

    private final String context;

    @JsonCreator
    private DeployConfigRequest(@JsonProperty("group_id") final String groupId, @JsonProperty("artifact_id") final String artifactId,
                                @JsonProperty("version") final String version, @JsonProperty("classifier") final String classifier,
                                @JsonProperty("context") final String context, @JsonProperty("type") final String type) {
        super(groupId, artifactId, version, classifier, type);
        this.context = context;
    }

    public String getContext() {
        return context;
    }

    @Override
    public boolean restart() {
        return true;
    }
}