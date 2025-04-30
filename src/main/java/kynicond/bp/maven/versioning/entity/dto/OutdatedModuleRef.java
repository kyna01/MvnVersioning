package kynicond.bp.maven.versioning.entity.dto;

import lombok.Data;

@Data
public class OutdatedModuleRef {
    private String module;
    private String groupId;
    private String artifactId;
    private String declared;
    private String latest;
    private Location location;
    public enum Location { DEPENDENCIES, DEP_MGMT, PARENT }
}
