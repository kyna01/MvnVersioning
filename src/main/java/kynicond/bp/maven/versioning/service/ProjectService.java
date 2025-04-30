package kynicond.bp.maven.versioning.service;

import kynicond.bp.maven.versioning.entity.dto.*;
import org.apache.maven.artifact.versioning.ComparableVersion;
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


//    public void resetProject() {
//        this.projectRootPomPath = null;
//    }
//
//    public String getProjectPomPath() {
//        return this.projectRootPomPath;
//    }

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
            module.setDependencyManagement(new ArrayList<>());
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

        String mavenHome = System.getenv("MAVEN_HOME");
        if (mavenHome == null) {
            throw new IllegalStateException("Systémová proměnná MAVEN_HOME není nastavena. Nastav ji na cestu ke tvému Maven adresáři.");
        }
        invoker.setMavenHome(new File(mavenHome));
        invoker.setOutputHandler(System.out::println);
        invoker.setErrorHandler(System.err::println);

        InvocationResult result = invoker.execute(invocationRequest);
        if (result.getExitCode() != 0){
            throw new RuntimeException("Maven příkaz selhal." + result.getExecutionException());
        }


        //TODO conflicts

        List<String> versionConflictWarnings = new ArrayList<>(); // rozšíření

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

        String mavenHome = System.getenv("MAVEN_HOME");
        if (mavenHome == null) {
            throw new IllegalStateException("Systémová proměnná MAVEN_HOME není nastavena. Nastav ji na cestu ke tvému Maven adresáři.");
        }
        invoker.setMavenHome(new File(mavenHome));

        invoker.setOutputHandler(System.out::println);
        invoker.setErrorHandler(System.err::println);

        InvocationResult result = invoker.execute(invocationRequest);
        if (result.getExitCode() != 0){
            throw new RuntimeException("Maven příkaz selhal." + result.getExecutionException());
        }




        // TODO conflicts
        List<String> versionConflictWarnings = new ArrayList<>(); // rozšíření

        if (!versionConflictWarnings.isEmpty()) {
            throw new RuntimeException("Nekompatibilní verze:\n" + String.join("\n", versionConflictWarnings));
        }

        return versionConflictWarnings;

    }








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


    //-----------------------------Conflicts----------------------------

    //TODO funkce na konflikty
    public List<String> checkAllModulesConflicts() throws Exception {
        List<String> conflicts = new ArrayList<>();
        return conflicts;
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

        System.out.println(docs);

        List<String> versions = new ArrayList<>();
        for (int i = 0; i < docs.length(); i++) {
            JSONObject doc = docs.getJSONObject(i);
            String version = doc.getString("v");
            versions.add(version);
        }

        return versions;
    }






    public List<String> updateModuleVersion(UpdateModuleRequest request) throws Exception {
        if (projectRootPomPath == null || loadedProject == null) {
            throw new IllegalStateException("Projekt není načten nebo není dostupný strom modulů.");
        }

        File modulePomFile;

        if (request.getModuleName().equals(loadedProject.getArtifactId())) {
            modulePomFile = new File(projectRootPomPath);
        }else {
            ModuleDTO module = findModuleByName(request.getModuleName(), loadedProject.getModules());
            if (module == null || module.getPomPath() == null) {
                throw new FileNotFoundException("Nepodařilo se najít pom.xml pro modul: " + request.getModuleName());
            }
            modulePomFile = new File(module.getPomPath());
        }

        InvocationRequest invocationRequest = new DefaultInvocationRequest();
        invocationRequest.setPomFile(modulePomFile);

        invocationRequest.setGoals(List.of(
                "versions:set",
                "-DnewVersion=" + request.getNewVersion(),
                "-DgenerateBackupPoms=false"
        ));

        Invoker invoker = new DefaultInvoker();
        String mavenHome = System.getenv("MAVEN_HOME");
        if (mavenHome == null) {
            throw new IllegalStateException("Systémová proměnná MAVEN_HOME není nastavena. Nastav ji na cestu ke tvému Maven adresáři.");
        }
        invoker.setMavenHome(new File(mavenHome));

        invoker.setOutputHandler(System.out::println);
        invoker.setErrorHandler(System.err::println);

        InvocationResult result = invoker.execute(invocationRequest);
        if (result.getExitCode() != 0) {
            throw new RuntimeException("Maven příkaz pro změnu verze modulu selhal." +
                    result.getExecutionException());
        }

        InvocationRequest childUpdateRequest = new DefaultInvocationRequest();
        childUpdateRequest.setPomFile(modulePomFile);
        childUpdateRequest.setGoals(List.of("versions:update-child-modules"));
        InvocationResult childResult = invoker.execute(childUpdateRequest);
        if (childResult.getExitCode() != 0) {
            throw new RuntimeException("Maven příkaz pro update child modulů selhal." +
                    childResult.getExecutionException());
        }


        versionToDependenciesUpdate(request.getGroupId(),request.getModuleName(),request.getNewVersion());


        List<String> conflicts = listOutdatedModuleReferences();
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
                    "-DprocessDependencyManagement=true"
            ));

            Invoker invoker = new DefaultInvoker();
            String mavenHome = System.getenv("MAVEN_HOME");
            if (mavenHome == null) {
                throw new IllegalStateException("Systémová proměnná MAVEN_HOME není nastavena. Nastav ji na cestu ke tvému Maven adresáři.");
            }
            invoker.setMavenHome(new File(mavenHome));

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
        String mavenHome = System.getenv("MAVEN_HOME");
        if (mavenHome == null) {
            throw new IllegalStateException("Systémová proměnná MAVEN_HOME není nastavena. Nastav ji na cestu ke tvému Maven adresáři.");
        }
        invoker.setMavenHome(new File(mavenHome));


        InvocationResult result = invoker.execute(invocationRequest);

        if (result.getExitCode() != 0)
        {
            throw new RuntimeException("Maven příkaz clean install selhal: " + result.getExecutionException());
        }

    }






    // ------------------------------Outdated-------------------------------------

    public List<String> listOutdatedDependencies_MavenCentral() {

        Map<String, String> latestCache = new HashMap<>();
        List<String> result = new ArrayList<>();

        for (ModuleDTO mod : flattenModules(loadedProject.getModules())) {

            List<DependencyDTO> all = new ArrayList<>();
            Optional.ofNullable(mod.getDependencies())        .ifPresent(all::addAll);
            Optional.ofNullable(mod.getDependencyManagement()).ifPresent(all::addAll);

            for (DependencyDTO dep : all) {
                String ga = dep.getGroupId() + ":" + dep.getArtifactId();

                String latest = latestCache.computeIfAbsent(ga, key -> {
                    List<String> vers = getAvailableVersions(
                            dep.getGroupId(), dep.getArtifactId());

                    return vers.stream()
                            .filter(this::isStable)
                            .max(this::compareSemver)
                            .orElse(null);
                });

                if (latest == null) continue;

                String current = dep.getVersion();
                if (current != null && isVersionHigher(latest, current)) {
                    result.add(mod.getName()
                            + ", dependency: " + ga
                            + ", actualVersion: " + current
                            + ", newestVersion: " + latest);
                }
            }
        }
        return result;
    }

    private boolean isStable(String v) {
        return !v.matches("(?i).*(alpha|beta|rc|snapshot|m\\d+|milestone).*");
    }


    private int compareSemver(String v1, String v2) {
        return new ComparableVersion(v1).compareTo(new ComparableVersion(v2));
    }






    //------------------------Outdated modules dependencies----------------------------------
    private final List<OutdatedModuleRef> outdated = new ArrayList<>();


    public List<String> listOutdatedModuleReferences() {

        Map<String, String> currentVersions = new HashMap<>();
        for (ModuleDTO m : flattenModules(loadedProject.getModules())) {
            String ga = m.getGroupId() + ":" + m.getArtifactId();
            if (!m.getVersion().isEmpty()) {
                currentVersions.put(ga, m.getVersion());
            }
        }

        List<String> result = new ArrayList<>();

        outdated.clear();


        for (ModuleDTO mod : flattenModules(loadedProject.getModules())) {

            String parentGA = mod.getParentGroupId() + ":" + mod.getParentArtifactId();
            String latestParentVersion = currentVersions.get(parentGA);
            if (latestParentVersion != null) {
                String declaredParentVersion = mod.getParentVersion();
                if (declaredParentVersion != null &&
                        new ComparableVersion(latestParentVersion).compareTo(
                                new ComparableVersion(declaredParentVersion)) > 0) {

                    result.add(String.format(
                            "%s, moduleRef: %s, declaredVersion: %s, newestVersion: %s",
                            mod.getName(), parentGA, declaredParentVersion, latestParentVersion
                    ));

                    OutdatedModuleRef ref = new OutdatedModuleRef();
                    ref.setModule(mod.getName());
                    ref.setGroupId(mod.getParentGroupId());
                    ref.setArtifactId(mod.getParentArtifactId());
                    ref.setLatest(latestParentVersion);
                    ref.setLocation(OutdatedModuleRef.Location.PARENT);
                    outdated.add(ref);
                }
            }


            List<DependencyDTO> all = new ArrayList<>();
            Optional.ofNullable(mod.getDependencies())        .ifPresent(all::addAll);
            Optional.ofNullable(mod.getDependencyManagement()).ifPresent(all::addAll);

            for (DependencyDTO dep : all) {
                String ga = dep.getGroupId() + ":" + dep.getArtifactId();
                String latest = currentVersions.get(ga);
                if (latest == null) continue;

                String declared = dep.getVersion();
                if (declared != null &&
                        new ComparableVersion(latest).compareTo(
                                new ComparableVersion(declared)) > 0) {

                    result.add(String.format(
                            "%s, moduleRef: %s, declaredVersion: %s, newestVersion: %s",
                            mod.getName(), ga, declared, latest));

                    OutdatedModuleRef ref = new OutdatedModuleRef();
                    ref.setModule(mod.getName());
                    ref.setGroupId(dep.getGroupId());
                    ref.setArtifactId(dep.getArtifactId());
                    ref.setLatest(latest);
                    ref.setLocation(
                            mod.getDependencyManagement().contains(dep)
                            ? OutdatedModuleRef.Location.DEP_MGMT
                            : OutdatedModuleRef.Location.DEPENDENCIES);
                    outdated.add(ref);
                }
            }
        }
        return result;
    }



    public List<String> fixOutdatedRefs() throws Exception {

        List<String> fails = new ArrayList<>();
        Set<String> parentModulesToPropagate = new HashSet<>();

        for (OutdatedModuleRef r : outdated) {
            try {
                UpdateDependencyRequest req = new UpdateDependencyRequest();
                req.setModuleName(r.getModule());
                req.setGroupId   (r.getGroupId());
                req.setArtifactId(r.getArtifactId());
                req.setNewVersion(r.getLatest());


                    if (r.getLocation() == OutdatedModuleRef.Location.DEPENDENCIES){
                        updateDependencyVersion(req);
                    }else if (r.getLocation() == OutdatedModuleRef.Location.DEP_MGMT) {
                        updateDependencyManagementVersion(req);
                    }
                    else if (r.getLocation() == OutdatedModuleRef.Location.PARENT) {
                    updateParentVersion(r.getModule(), r.getLatest());
                    parentModulesToPropagate.add(r.getArtifactId());
                }

            } catch (Exception ex) {
                fails.add(r.getModule()+" – "+ex.getMessage());
            }
        }

        for (String parentModule : parentModulesToPropagate) {
            try {
                propagateParentVersionToChildren(parentModule);
            } catch (Exception ex) {
                fails.add(parentModule + " – (propagace parent verze selhala) " + ex.getMessage());
            }
        }


        outdated.clear();
        compileProject();
        return fails;  // 200 - []
    }


    private void updateParentVersion(String moduleName, String newVersion) throws Exception {
        ModuleDTO module = findModuleByName(moduleName, loadedProject.getModules());
        File pomFile = new File(module.getPomPath());

        InvocationRequest request = new DefaultInvocationRequest();
        request.setPomFile(pomFile);
        request.setGoals(List.of(
                "versions:update-parent",
                "-DparentVersion=" + newVersion,
                "-DgenerateBackupPoms=false",
                "-DallowSnapshots=true"
        ));

        System.out.println(">>> Spouštím updateParentVersion pro modul " + moduleName);
        Invoker invoker = new DefaultInvoker();
        String mavenHome = System.getenv("MAVEN_HOME");
        if (mavenHome == null) {
            throw new IllegalStateException("Systémová proměnná MAVEN_HOME není nastavena. Nastav ji na cestu ke tvému Maven adresáři.");
        }
        invoker.setMavenHome(new File(mavenHome));
        invoker.setOutputHandler(System.out::println);
        invoker.setErrorHandler(System.err::println);

        InvocationResult result = invoker.execute(request);
        System.out.println(">>> Dokončeno.");
        if (result.getExitCode() != 0) {
            throw new RuntimeException("Nepodařilo se aktualizovat <parent> v modulu " + moduleName);
        }
    }


    public void propagateParentVersionToChildren(String parentModuleName) throws Exception {
        ModuleDTO parent = findModuleByName(parentModuleName, loadedProject.getModules());
        if (parent == null || parent.getPomPath() == null) {
            throw new FileNotFoundException("Nepodařilo se najít parent modul: " + parentModuleName);
        }

        File pomFile = new File(parent.getPomPath());

        InvocationRequest req = new DefaultInvocationRequest();
        req.setPomFile(pomFile);
        req.setGoals(List.of(
                "versions:update-child-modules",
                "-DgenerateBackupPoms=false"
        ));

        Invoker invoker = new DefaultInvoker();
        String mavenHome = System.getenv("MAVEN_HOME");
        if (mavenHome == null) {
            throw new IllegalStateException("Systémová proměnná MAVEN_HOME není nastavena. Nastav ji na cestu ke tvému Maven adresáři.");
        }
        invoker.setMavenHome(new File(mavenHome));
        invoker.setOutputHandler(System.out::println);
        invoker.setErrorHandler(System.err::println);

        InvocationResult result = invoker.execute(req);
        if (result.getExitCode() != 0) {
            throw new RuntimeException("Nepodařilo se aktualizovat <parent> v child modulech pro " + parentModuleName);
        }
    }












}

