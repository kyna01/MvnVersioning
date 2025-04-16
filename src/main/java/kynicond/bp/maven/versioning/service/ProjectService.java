package kynicond.bp.maven.versioning.service;

import kynicond.bp.maven.versioning.entity.dto.*;
import org.apache.maven.shared.invoker.*;
import org.apache.maven.shared.utils.cli.CommandLineException;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.json.JSONArray;
import org.json.JSONObject;

@Service
public class ProjectService {

    private String projectRootPomPath;
    private ProjectDTO loadedProject;


    public void resetProject() {
        this.projectRootPomPath = null;
    }

    public String getProjectPomPath() {
        return this.projectRootPomPath;
    }

    public ProjectDTO loadStructureOnly(String pomPath) throws Exception {
        File pomFile = new File(pomPath);
        if (!pomFile.exists()) {
            throw new FileNotFoundException("POM soubor nenalezen.");
        }

        this.projectRootPomPath = pomPath;

        ModuleDTO rootModule = loadModuleRecursively(pomFile, false); //not
        ModuleDTO cleaned = cleanForStructure(rootModule);

        ProjectDTO project = new ProjectDTO();
        project.setGroupId(cleaned.getGroupId());
        project.setArtifactId(cleaned.getArtifactId());
        project.setVersion(cleaned.getVersion());
        project.setModules(cleaned.getSubmodules());

        this.loadedProject = project;

        return project;
    }

    public ProjectDTO loadWithDependencies(String pomPath) throws Exception {
        File pomFile = new File(pomPath);
        if (!pomFile.exists()) {
            throw new FileNotFoundException("POM soubor nenalezen.");
        }

        this.projectRootPomPath = pomPath;

        ModuleDTO rootModule = loadModuleRecursively(pomFile, true); // included

        ProjectDTO project = new ProjectDTO();
        project.setGroupId(rootModule.getGroupId());
        project.setArtifactId(rootModule.getArtifactId());
        project.setVersion(rootModule.getVersion());
        project.setModules(rootModule.getSubmodules());

        project.setDependencies(rootModule.getDependencies());

        this.loadedProject = project;


        return project;
    }


    private ModuleDTO loadModuleRecursively(File pomFile, boolean includeDependencies) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(pomFile);
        Element root = doc.getDocumentElement();

        ModuleDTO module = new ModuleDTO();
        module.setName(pomFile.getParentFile().getName());
        module.setPomPath(pomFile.getAbsolutePath());

        NodeList parentNodes = root.getElementsByTagName("parent");
        if (parentNodes.getLength() > 0) {
            Element parentEl = (Element) parentNodes.item(0);
            module.setParentGroupId(getTagValue(parentEl, "groupId"));
            module.setParentArtifactId(getTagValue(parentEl, "artifactId"));
            module.setParentVersion(getTagValue(parentEl, "version"));
        }
//
//        // ArtifactId (musí být vždy uvedeno)
//        String artifactId = getTagValue(root, "artifactId");
//        module.setArtifactId(artifactId);
//
//        // GroupId (může se zdědit z parenta)
//        String groupId = getTagValue(root, "groupId");
//        if (groupId == null || groupId.isEmpty()) {
//            groupId = module.getParentGroupId();
//        }
//        module.setGroupId(groupId);
//
//        // Version (může se zdědit z parenta)
//        String version = getTagValue(root, "version");
//        if (version == null || version.isEmpty()) {
//            version = module.getParentVersion();
//        }
//        module.setVersion(version);

        String artifactId = getDirectChildTagValue(root, "artifactId");
        String groupId = getDirectChildTagValue(root, "groupId");
        String version = getDirectChildTagValue(root, "version");

        module.setArtifactId(artifactId);
        module.setGroupId(groupId);
        module.setVersion(version);


        if (includeDependencies) {
            module.setDependencies(loadDependencies(root));
            module.setDependencyManagement(loadDependencyManagement(root));
        } else {
            module.setDependencies(new ArrayList<>());
        }

        NodeList moduleNodes = root.getElementsByTagName("module");
        List<ModuleDTO> submodules = new ArrayList<>();

        for (int i = 0; i < moduleNodes.getLength(); i++) {
            String subModuleName = moduleNodes.item(i).getTextContent().trim();
            File subPom = new File(pomFile.getParent(), subModuleName + "/pom.xml");
            if (subPom.exists()) {
                ModuleDTO subModule = loadModuleRecursively(subPom, includeDependencies);
                subModule.setName(subModuleName);
                submodules.add(subModule);
            }
        }

        module.setSubmodules(submodules);
        return module;
    }

    private String getDirectChildTagValue(Element root, String tagName) {
        NodeList children = root.getChildNodes();
        for (int i=0; i<children.getLength(); i++) {
            Node n = children.item(i);
            if (n.getNodeType() == Node.ELEMENT_NODE && n.getNodeName().equals(tagName)) {
                return n.getTextContent().trim();
            }
        }
        return "";
    }


    private List<DependencyDTO> loadDependencies(Element root) {
        List<DependencyDTO> result = new ArrayList<>();

        NodeList dependenciesAll = root.getElementsByTagName("dependencies");

        for (int i = 0; i < dependenciesAll.getLength(); i++) {
            Element depsEl =  (Element) dependenciesAll.item(i);

            Node parent = depsEl.getParentNode();
            if (parent != null && "dependencyManagement".equals(parent.getNodeName())) {
                continue;
            }


            NodeList depNodes = depsEl.getElementsByTagName("dependency");
            for (int j = 0; j < depNodes.getLength(); j++) {
                Element depEl = (Element) depNodes.item(j);

                DependencyDTO dep = new DependencyDTO();
                dep.setGroupId(getTagValue(depEl, "groupId"));
                dep.setArtifactId(getTagValue(depEl, "artifactId"));
                dep.setVersion(getTagValue(depEl, "version"));
                result.add(dep);
            }
        }

        return result;
    }

    private List<DependencyDTO> loadDependencyManagement(Element root){
        List<DependencyDTO> dmDeps = new ArrayList<>();

        NodeList dmNodes = root.getElementsByTagName("dependencyManagement");
        if (dmNodes.getLength() == 0){
            return dmDeps;
        }

        Element dmElement = (Element) dmNodes.item(0);

        NodeList dependencies = dmElement.getElementsByTagName("dependency");

        for (int i = 0; i < dependencies.getLength(); i++){
            Node node = dependencies.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE){
                Element depElement = (Element) node;

                DependencyDTO dep = new DependencyDTO();
                dep.setGroupId(getTagValue(depElement, "groupId"));
                dep.setArtifactId(getTagValue(depElement, "artifactId"));
                dep.setVersion(getTagValue(depElement, "version"));

                dmDeps.add(dep);
            }
        }

        return dmDeps;
    }




    private ModuleDTO cleanForStructure(ModuleDTO original) {
        ModuleDTO cleaned = new ModuleDTO();
        cleaned.setName(original.getName());
        cleaned.setArtifactId(original.getArtifactId());
        cleaned.setVersion(original.getVersion());

        List<ModuleDTO> cleanedSubmodules = new ArrayList<>();
        if (original.getSubmodules() != null) {
            for (ModuleDTO sub : original.getSubmodules()) {
                cleanedSubmodules.add(cleanForStructure(sub));
            }
        }

        cleaned.setSubmodules(cleanedSubmodules);
        return cleaned;
    }



    private String getTagValue(Element element, String tag) {
        NodeList list = element.getElementsByTagName(tag);
        if (list.getLength() == 0) return "";
        return list.item(0).getTextContent().trim();
    }




    //-------------------------------Update-------------------------------
    public List<String> updateDependencyVersion(UpdateDependencyRequest request) throws Exception {
        if (projectRootPomPath == null || loadedProject == null) {
            throw new IllegalStateException("Projekt není načten nebo není dostupný strom modulů.");
        }

        ModuleDTO module = findModuleByName(request.getModuleName(), loadedProject.getModules());
        if (module == null || module.getPomPath() == null) {
            throw new FileNotFoundException("Nepodařilo se najít pom.xml pro modul: " + request.getModuleName());
        }

        File modulePomFile = new File(module.getPomPath());

        InvocationRequest invocationRequest = new DefaultInvocationRequest();
        invocationRequest.setPomFile(modulePomFile);

        invocationRequest.setGoals(List.of(
                "versions:use-dep-version",
                "-N",
                "-Dincludes=" + request.getGroupId() + ":" + request.getArtifactId(),
                "-DdepVersion=" + request.getNewVersion(),
                "-DforceVersion=true",
                "-DprocessDependencies=true",
                "-DprocessDependencyManagement=false"
        ));

        Invoker invoker = new DefaultInvoker();

        invoker.setMavenHome(new File("/usr/local/Cellar/maven/3.9.6/libexec"));
        invoker.setOutputHandler(System.out::println);
        invoker.setErrorHandler(System.err::println);

        InvocationResult result = invoker.execute(invocationRequest);
        if (result.getExitCode() != 0){
            throw new RuntimeException("Maven příkaz selhal." + result.getExecutionException());
        }





        List<String> versionConflictWarnings = detectGlobalConflicts(request);

        if (!versionConflictWarnings.isEmpty()) {
            throw new RuntimeException("Nekompatibilní verze:\n" + String.join("\n", versionConflictWarnings));
        }

        return versionConflictWarnings;

    }

    private ModuleDTO findModuleByName(String name, List<ModuleDTO> modules) {
        for (ModuleDTO module : modules) {
            if (module.getName().equals(name)) {
                return module;
            }
            if (module.getSubmodules() != null) {
                ModuleDTO sub = findModuleByName(name, module.getSubmodules());
                if (sub != null) return sub;
            }
        }
        return null;
    }





    public List<String> updateDependencyManagementVersion(UpdateDependencyRequest request) throws Exception {
        if (projectRootPomPath == null || loadedProject == null) {
            throw new IllegalStateException("Projekt není načten nebo není dostupný strom modulů.");
        }

        ModuleDTO module = findModuleByName(request.getModuleName(), loadedProject.getModules());
        if (module == null || module.getPomPath() == null) {
            throw new FileNotFoundException("Nepodařilo se najít pom.xml pro modul: " + request.getModuleName());
        }

        File modulePomFile = new File(module.getPomPath());

        InvocationRequest invocationRequest = new DefaultInvocationRequest();
        invocationRequest.setPomFile(modulePomFile);

        invocationRequest.setGoals(List.of(
                "versions:use-dep-version",
                "-N",
                "-Dincludes=" + request.getGroupId() + ":" + request.getArtifactId(),
                "-DdepVersion=" + request.getNewVersion(),
                "-DforceVersion=true",
                "-DprocessDependencies=false",
                "-DprocessDependencyManagement=true"
        ));

        Invoker invoker = new DefaultInvoker();

        invoker.setMavenHome(new File("/usr/local/Cellar/maven/3.9.6/libexec"));
        invoker.setOutputHandler(System.out::println);
        invoker.setErrorHandler(System.err::println);

        InvocationResult result = invoker.execute(invocationRequest);
        if (result.getExitCode() != 0){
            throw new RuntimeException("Maven příkaz selhal." + result.getExecutionException());
        }





        List<String> versionConflictWarnings = detectGlobalConflicts(request);

        if (!versionConflictWarnings.isEmpty()) {
            throw new RuntimeException("Nekompatibilní verze:\n" + String.join("\n", versionConflictWarnings));
        }

        return versionConflictWarnings;

    }




    //-----------------------------Conflicts----------------------------



    private List<ModuleDTO> flattenModules(List<ModuleDTO> modules) {
        List<ModuleDTO> result = new ArrayList<>();
        for (ModuleDTO m : modules) {
            result.add(m);
            if (m.getSubmodules() != null && !m.getSubmodules().isEmpty()) {
                result.addAll(flattenModules(m.getSubmodules()));
            }
        }
        return result;
    }


    /**
     * Projde všechny moduly (rekurzivně) a pro každý spustí dependency:tree -Dverbose.
     * Z každého výstupu vyparsuje omitnuté konflikty a sloučí je do jednoho seznamu.
     */
    public List<String> checkAllModulesConflicts() throws Exception {
        if (loadedProject == null || loadedProject.getModules() == null) {
            throw new IllegalStateException("Projekt nebo seznam modulů není načten.");
        }

        List<String> allConflicts = new ArrayList<>();

        // 1) Získáme plochý seznam všech modulů (včetně submodulů).
        List<ModuleDTO> allModules = flattenModules(loadedProject.getModules());

        // 2) Pro každý modul spustíme "dependency:tree -Dverbose"
        for (ModuleDTO module : allModules) {
            File pomFile = new File(module.getPomPath());
            if (!pomFile.exists()) {
                // Kdyby náhodou modul neměl POM
                continue;
            }

            // Vytvoříme unikátní jméno výstupního souboru pro tento modul
            File outFile = new File(pomFile.getParentFile(), "deps_verbose_" + module.getName() + ".txt");

            InvocationRequest request = new DefaultInvocationRequest();
            request.setPomFile(pomFile);
            request.setGoals(List.of(
                    "dependency:tree",
                    "-Dverbose=true",
                    "-DoutputFile=" + outFile.getAbsolutePath()
            ));

            Invoker invoker = new DefaultInvoker();
            invoker.setMavenHome(new File("/usr/local/Cellar/maven/3.9.6/libexec"));
            InvocationResult result = invoker.execute(request);
            if (result.getExitCode() != 0) {
                throw new RuntimeException("dependency:tree selhalo pro modul " + module.getName()
                        + ". Chyba: " + result.getExecutionException());
            }

            // 3) Každý modul analyzujeme a sloučíme do allConflicts
            List<String> modConflicts = analyzeAllVerboseConflicts(outFile);
            allConflicts.addAll(modConflicts);
        }

        List<String> filteredConflicts = allConflicts.stream()
                .distinct()
                .collect(Collectors.toList());

        return filteredConflicts;
    }




    private List<String> analyzeAllVerboseConflicts(File verboseFile) throws IOException {
        List<String> lines = Files.readAllLines(verboseFile.toPath());
        List<String> conflicts = new ArrayList<>();

        // Regex, co najde např.
        //   (org.junit.jupiter:junit-jupiter-params:jar:5.10.2:test - omitted for conflict with 5.0.3)
        Pattern pattern = Pattern.compile(
                "\\(([^:]+):([^:]+):jar:([^:]+):[^)]+\\s-\\s+omitted\\s+for\\s+conflict\\s+with\\s+([^\\)]+)\\)"
        );

        for (String line : lines) {
            Matcher matcher = pattern.matcher(line);
            if (matcher.find()) {
                String groupId        = matcher.group(1);
                String artifactId     = matcher.group(2);
                String omittedVersion = matcher.group(3);
                String forcedVersion  = matcher.group(4).trim();

                // Pokud je omittedVersion > forcedVersion => reálně zahazujeme novější
                if (isVersionHigher(omittedVersion, forcedVersion)) {
                    conflicts.add(String.format(
                            "Konflikt: %s:%s -> vyšší verze %s je vyřazena, " +
                                    "Maven ponechal nižší verzi %s.",
                            groupId, artifactId, omittedVersion, forcedVersion
                    ));
                }
            }
        }


        return conflicts;
    }











    public List<String> detectGlobalConflicts(UpdateDependencyRequest request) throws Exception {
        ModuleDTO module = findModuleByName(request.getModuleName(), loadedProject.getModules());
        File modulePomFile = new File(module.getPomPath());

        File outputFile = new File(modulePomFile.getParentFile(), "local_deps_verbose.txt");

        InvocationRequest requestVerbose = new DefaultInvocationRequest();
        requestVerbose.setPomFile(modulePomFile);
        requestVerbose.setGoals(List.of(
                "dependency:tree",
                "-Dverbose=true",
                "-DoutputFile=" + outputFile.getAbsolutePath()
        ));

        Invoker invoker = new DefaultInvoker();
        invoker.setMavenHome(new File("/usr/local/Cellar/maven/3.9.6/libexec"));
        InvocationResult result = invoker.execute(requestVerbose);

        if (result.getExitCode() != 0) {
            throw new RuntimeException("Nepodařilo se získat strom závislostí (verbose) v submodulu: " + module.getName());
        }

        return analyzeVerboseTree(outputFile, request);
    }




    private boolean isVersionHigher(String existing, String requested) {
        String[] existingParts = existing.split("\\.");
        String[] requestedParts = requested.split("\\.");

        int length = Math.max(existingParts.length, requestedParts.length);
        for (int i = 0; i < length; i++) {
            int existingNum = i < existingParts.length ? Integer.parseInt(existingParts[i]) : 0;
            int requestedNum = i < requestedParts.length ? Integer.parseInt(requestedParts[i]) : 0;

            if (existingNum > requestedNum) return true;
            if (existingNum < requestedNum) return false;
        }

        return false;
    }



    private List<String> analyzeVerboseTree(File verboseFile, UpdateDependencyRequest request) throws IOException {
        List<String> lines = Files.readAllLines(verboseFile.toPath());
        List<String> conflicts = new ArrayList<>();

        // Vzorek, který matchuje např.:
        // (org.junit.jupiter:junit-jupiter-params:jar:5.10.2:test - omitted for conflict with 5.0.3)
        Pattern pattern = Pattern.compile(
                "\\(([^:]+):([^:]+):jar:([^:]+):[^)]+\\s-\\s+omitted\\s+for\\s+conflict\\s+with\\s+([^\\)]+)\\)"
        );

        // Pokud chcete ukazovat konflikty jen pro tu dependency, kterou měníte:
        String targetDepKey = request.getGroupId() + ":" + request.getArtifactId();
        String explicitVersion = request.getNewVersion();

        for (String line : lines) {
            // PROJDEME JEDNU ŘÁDKU:
            Matcher matcher = pattern.matcher(line);

            // NAJDEME PRVNÍ (a jediný) match v téhle řádce:
            if (matcher.find()) {
                System.out.println("LINE MATCH: " + line); // pro ladění

                String groupId = matcher.group(1);
                String artifactId = matcher.group(2);
                String omittedVersion = matcher.group(3);
                String forcedVersion = matcher.group(4).trim();

                // Pokud chcete konflikty hlásit pouze pro G:A, který uživatel mění:
                String foundDepKey = groupId + ":" + artifactId;
                if (!foundDepKey.equals(targetDepKey)) {
                    continue;  // Nepřidávat do conflict listu
                }

                // Porovnáme verze – pokud je omittedVersion vyšší než forcedVersion,
                // znamená to, že Maven zahodil novější verzi a ponechal starší = konflikt.
                if (isVersionHigher(omittedVersion, forcedVersion)) {
                    conflicts.add(String.format(
                            "Konflikt: Tranzitivní závislost %s vyžaduje verzi %s, " +
                                    "ale explicitně byla nastavena nižší verze %s",
                            targetDepKey, omittedVersion, forcedVersion
                    ));
                }
            }
        }

        return conflicts;
    }








    //-------------------------------Versions-------------------------------









    public List<String> getAvailableVersions(String groupId, String artifactId){
        System.out.println("Fetching versions for: " + groupId + ":" + artifactId);

        String query = "g:\"" + groupId + "\" AND a:\"" + artifactId + "\"";
        String url = UriComponentsBuilder.fromHttpUrl("https://search.maven.org/solrsearch/select")
                .queryParam("q", query)
                .queryParam("core", "gav")
                .queryParam("rows", "200") // čím víc, tím víc verzí
                .queryParam("wt", "json")
                .build()
                .toUriString();

        System.out.println("Final URL: " + url);

        RestTemplate restTemplate = new RestTemplate();
        String response = restTemplate.getForObject(url,String.class);

        System.out.println("Response: " + response);

        JSONObject root = new JSONObject(response);
        JSONArray docs = root.getJSONObject("response").getJSONArray("docs");

        List<String> versions = new ArrayList<>();
        for (int i = 0; i < docs.length(); i++) {
            JSONObject doc = docs.getJSONObject(i);
            String version = doc.getString("v");
            versions.add(version);
        }

        return versions;
    }




    // todo UpdateModuleRequest

    public List<String> updateModuleVersion(UpdateModuleRequest request) throws Exception {
        if (projectRootPomPath == null || loadedProject == null) {
            throw new IllegalStateException("Projekt není načten nebo není dostupný strom modulů.");
        }

        ModuleDTO module = findModuleByName(request.getModuleName(), loadedProject.getModules());
        if (module == null || module.getPomPath() == null) {
            throw new FileNotFoundException("Nepodařilo se najít pom.xml pro modul: " + request.getModuleName());
        }

        File modulePomFile = new File(module.getPomPath());

        InvocationRequest invocationRequest = new DefaultInvocationRequest();
        invocationRequest.setPomFile(modulePomFile);

        invocationRequest.setGoals(List.of(
                "versions:set",
                "-DnewVersion=" + request.getNewVersion(),
                "-DgenerateBackupPoms=false"
        ));

        Invoker invoker = new DefaultInvoker();
        invoker.setMavenHome(new File("/usr/local/Cellar/maven/3.9.6/libexec"));
        invoker.setOutputHandler(System.out::println);
        invoker.setErrorHandler(System.err::println);

        InvocationResult result = invoker.execute(invocationRequest);
        if (result.getExitCode() != 0) {
            throw new RuntimeException("Maven příkaz pro změnu verze modulu selhal." +
                    result.getExecutionException());
        }

        // Volitelně můžeme spustit další cíl pro update child modulů:
        // Například:
        InvocationRequest childUpdateRequest = new DefaultInvocationRequest();
        childUpdateRequest.setPomFile(modulePomFile);
        childUpdateRequest.setGoals(List.of("versions:update-child-modules"));
        InvocationResult childResult = invoker.execute(childUpdateRequest);
        if (childResult.getExitCode() != 0) {
            throw new RuntimeException("Maven příkaz pro update child modulů selhal." +
                    childResult.getExecutionException());
        }


        versionToDependenciesUpdate(request.getGroupId(),request.getModuleName(),request.getNewVersion());



        List<String> conflicts = checkAllModulesConflicts();
        return conflicts;
    }




    private void versionToDependenciesUpdate(String groupId, String artifactId, String newVersion) throws Exception {
        List<ModuleDTO> allModules = flattenModules(loadedProject.getModules());

        for (ModuleDTO module : allModules) {
            if (module.getArtifactId().equals(artifactId) && module.getGroupId().equals(groupId)) {
                continue;
            }

            File pomFile = new File(module.getPomPath());

            InvocationRequest request = new DefaultInvocationRequest();
            request.setPomFile(pomFile);
            request.setGoals(List.of(
                    "versions:use-dep-version",
                    "-Dincludes=" + groupId + ":" + artifactId,
                    "-DdepVersion=" + newVersion,
                    "-DforceVersion=true",
                    "-DgenerateBackupPoms=false",
                    "-DprocessDependencies=true",
                    "-DprocessDependencyManagement=false"
            ));

            Invoker invoker = new DefaultInvoker();
            invoker.setMavenHome(new File("/usr/local/Cellar/maven/3.9.6/libexec"));
            invoker.setOutputHandler(System.out::println);
            System.out.println("=====Errors=====");
            invoker.setErrorHandler(System.out::println);

            InvocationResult invocationResult = invoker.execute(request);

            if (invocationResult.getExitCode() != 0){
                System.out.println("Nepodařilo se aktualizovat zzávislosti v modulu" + module.getName());
            }
            else {
                System.out.println("Úspěšně se podařilo aktualizovat závislosti v modulu" + module.getName());
            }


        }
    }

    public void compileProject() throws Exception {

        if (projectRootPomPath == null)
        {
            throw new IllegalStateException("Projekt není načten.");
        }
        InvocationRequest invocationRequest = new DefaultInvocationRequest();
        invocationRequest.setPomFile(new File(projectRootPomPath));
        invocationRequest.setGoals(List.of("clean", "install"));


        Invoker invoker = new DefaultInvoker();
        invoker.setMavenHome(new File("/usr/local/Cellar/maven/3.9.6/libexec"));

        InvocationResult result = invoker.execute(invocationRequest);

        if (result.getExitCode() != 0)
        {
            throw new RuntimeException("Maven příkaz clean compile selhal: " + result.getExecutionException());
        }

    }

}

