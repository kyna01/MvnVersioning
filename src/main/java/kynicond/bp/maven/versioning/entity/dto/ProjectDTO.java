package kynicond.bp.maven.versioning.entity.dto;

import lombok.Data;

import java.util.List;

@Data
public class ProjectDTO {
    private String groupId;
    private String artifactId;
    private String version;
    private List<ModuleDTO> modules;
    private List<DependencyDTO> dependencies;
}
