package kynicond.bp.maven.versioning.entity.dto;

import lombok.Data;

@Data
public class UpdateModuleRequest {
    private String groupId;
    private String moduleName;
    private String newVersion;
}


//TODO zde pridano na update modulu