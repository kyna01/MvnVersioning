package kynicond.bp.maven.versioning.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import kynicond.bp.maven.versioning.entity.dto.LoadProjectRequest;
import kynicond.bp.maven.versioning.entity.dto.ProjectDTO;
import kynicond.bp.maven.versioning.entity.dto.UpdateDependencyRequest;
import kynicond.bp.maven.versioning.service.ProjectService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/project")
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @Operation(summary = "Načte informace o Maven projektu")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Projekt úspěšně načten"),
            @ApiResponse(responseCode = "400", description = "Chyba při načítání projektu")
    })
    @PostMapping("/load-structure")
    public ResponseEntity<ProjectDTO> loadProjectStructure(@RequestBody LoadProjectRequest request) {
        try {
            ProjectDTO dto = projectService.loadStructureOnly(request.getPomPath());
            return ResponseEntity.ok(dto);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }



    @Operation(summary = "Načte dané dependencies projektu")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Dependencies úspěšně načteny"),
            @ApiResponse(responseCode = "400", description = "Chyba při načítání projektu")
    })
    @PostMapping("/load-with-dependencies")
    public ResponseEntity<ProjectDTO> loadProjectWithDependencies(@RequestBody LoadProjectRequest request) {
        try {
            ProjectDTO dto = projectService.loadWithDependencies(request.getPomPath());
            return ResponseEntity.ok(dto);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().build();
        }
    }

    @Operation(summary = "Aktualizuje verzi konkrétní dependency v modulu")
    @PostMapping("/update-dependency-version")
    public ResponseEntity<Void> updateDependencyRequest(@RequestBody UpdateDependencyRequest updateDependencyRequest){
        try {
            projectService.updateDependencyVersion(updateDependencyRequest);
            return ResponseEntity.ok().build();
        }catch (Exception e){
            e.printStackTrace();
            return ResponseEntity.badRequest().build();
        }
    }

    @Operation(summary = "Vrátí seznam dostupných verzí pro závislost z Maven Central")
    @GetMapping("/dependency-versions")
    public ResponseEntity<List<String>> getAvailableDependencyVersions(@RequestParam String groupId, @RequestParam String artifactId)
    {
        try {
            List<String> versions = projectService.getAvailableVersions(groupId, artifactId);
            return ResponseEntity.ok(versions);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().build();
        }
    }


    @Operation(summary = "Zkontroluje konflikty závislostí v projektu")
    @GetMapping("/check-dependency-conflicts")
    public ResponseEntity<List<String>> checkConflicts() {
        try {
            List<String> conflicts = projectService.checkDependencyConflicts();
            return ResponseEntity.ok(conflicts);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().build();
        }
    }

}
