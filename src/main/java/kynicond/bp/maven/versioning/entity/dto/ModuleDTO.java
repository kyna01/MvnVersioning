package kynicond.bp.maven.versioning.entity.dto;

import lombok.Data;

import java.util.List;

@Data
public class ModuleDTO {
    private String name;
    private String groupId;
    private String artifactId;
    private String version;

    private String parentGroupId;
    private String parentArtifactId;
    private String parentVersion;

    private String pomPath;
    private List<ModuleDTO> submodules;
    private List<DependencyDTO> dependencies;
    private List<DependencyDTO> dependencyManagement;
}