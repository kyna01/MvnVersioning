package kynicond.bp.maven.versioning.entity.dto;

import lombok.Data;

import java.util.List;

@Data
public class ModuleDTO {
    private String name;
    private String groupId;
    private String artifactId;
    private String version;
    private List<ModuleDTO> submodules;
    private List<DependencyDTO> dependencies;
    private String pomPath;
}