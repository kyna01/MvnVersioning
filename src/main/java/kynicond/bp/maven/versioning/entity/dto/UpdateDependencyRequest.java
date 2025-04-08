package kynicond.bp.maven.versioning.entity.dto;

import lombok.Data;

@Data
public class UpdateDependencyRequest {
    private String moduleName;
    private String groupId;
    private String artifactId;
    private String newVersion;
}
