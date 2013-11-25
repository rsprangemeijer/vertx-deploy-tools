package nl.jpoint.vertx.mod.cluster.request;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class DeployArtifactRequest extends ModuleRequest {

    private String context;

    @JsonCreator
    private DeployArtifactRequest(@JsonProperty("group_id") final String groupId,@JsonProperty("artifact_id") final String artifactId,
                                  @JsonProperty("version") final String version, @JsonProperty("classifier")final String classifier,
                                  @JsonProperty("context") final String context) {
        super(groupId, artifactId, version, classifier);
        this.context = context;
    }

    public String getContext() {
        return context;
    }
}