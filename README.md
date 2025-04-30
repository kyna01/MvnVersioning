# Nástroj pro správu verzí multimodulárních Maven projektů

Tato aplikace umožňuje správu verzí, aktualizaci závislostí a kontrolu konfliktních verzí v rámci Maven multimodulárních projektů.

## Požadavky

- Java 21
- Maven 3.9+
- (doporučeno) IDE jako IntelliJ IDEA, Eclipse nebo VS Code
- Internetové připojení pro stahování verzí z Maven Central

## Spuštění bez IDE (v terminálu)

### 1. Build projektu Spuštění bez IDE (přes terminál)

# 1. Build projektu
```bash
mvn clean install
```
# 2. Spuštění aplikace
```bash
mvn spring-boot:run
```
# 3. Otevření Swagger UI:
http://localhost:8080/api/swagger-ui/index.html#/

Poznámky pro windows:
Pokud Maven selže s chybou MAVEN_HOME not found, ujistěte se, že máte nastavenou systémovou proměnnou:
```bash
MAVEN_HOME = C:\Program Files\Apache\Maven
PATH += ;%MAVEN_HOME%\bin
```
Poznámky pro macOS/Linux:
```bash
echo $MAVEN_HOME
```
Pokud není nastavena, přidejte do svého ~/.bashrc, ~/.zshrc nebo ~/.bash_profile (podle shellu):
```bash
export MAVEN_HOME=/usr/local/Cellar/maven/3.9.6/libexec
export PATH=$MAVEN_HOME/bin:$PATH
```
Pak restartujte terminál nebo spusťte:
```bash
source ~/.zshrc
# nebo source ~/.bashrc
```

Důležité
V aplikaci se používá:
```bash
System.getenv("MAVEN_HOME")
```
Tedy: je nutné mít proměnnou MAVEN_HOME správně nastavenou.

