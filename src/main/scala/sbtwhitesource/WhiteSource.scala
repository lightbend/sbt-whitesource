package sbtwhitesource

import java.io.{ File, IOException }
import java.net.URI

import org.whitesource.agent.api._, dispatch._, model._
import org.whitesource.agent.client._
import org.whitesource.agent.report._

import scala.collection.JavaConverters._

import sbt._, compat._
import sbt.librarymanagement.ConfigRef

final class Config(
    val projectID: ModuleID,
    val skip: Boolean,
    val failOnError: Boolean,
    val serviceUrl: URI,
    val checkPolicies: Boolean,
    val orgToken: String,
    val forceCheckAllDependencies: Boolean,
    val forceUpdate: Boolean,
    val product: String,
    val productVersion: String,
    val outDir: File,
    val aggregateModules: Boolean,
    val aggregateProjectName: String,
    val aggregateProjectToken: String,
    val requesterEmail: String,
    val autoDetectProxySettings: Boolean,
    val log: Logger
)

final class ProjectConfig(
    val projectName: String,
    val projectID: ModuleID,
    val onlyDirectDependencies: Boolean,
    val libraryDependencies: Seq[ModuleID],
    val updateReport: UpdateReport,
    val ignoreTestScopeDependencies: Boolean,
    val projectToken: String,
    val ignore: Boolean,
    val includes: Vector[String],
    val excludes: Vector[String],
    val ignoredScopes: Vector[String]
) {
  def groupId: String    = projectID.organization
  def artifactId: String = projectID.name
  def version: String    = projectID.revision
}

final case class WhiteSourceException(message: String = null, cause: Exception = null)
    extends RuntimeException(message, cause)

sealed abstract class BaseAction(config: Config, childConfigs: Vector[ProjectConfig]) {
  val agentType: String    = "maven-plugin"     // TODO: use "sbt-plugin" or "sbt-whitesource"
  val agentVersion: String = "2.3.5" // "0.1.0-SNAPSHOT" // TODO: Extract this from the build.
  import config._

  final def execute(): Unit = {
    val startTime = System.currentTimeMillis()

    if (skip) log info "Skipping update" else {
      var service: WhitesourceService = null
      try {
        service = createService()
        doExecute(service)
      } catch {
        case e: WhiteSourceException => handleError(e)
        case e: RuntimeException     => throw new RuntimeException("Unexpected error", e)
      } finally
        if (service != null) service.shutdown()
    }

    log debug s"Total execution time is ${System.currentTimeMillis() - startTime} [msec]"
  }

  protected def doExecute(service: WhitesourceService): Unit

  final protected def extractProjectInfos(): Vector[AgentProjectInfo] = {
    val projectInfos = childConfigs.iterator.filter(shouldProcess).map(processProject).toVector
    debugProjectInfos(projectInfos, log)

    if (aggregateModules) {
      val flatDependencies =
        projectInfos flatMap (_.getDependencies.asScala flatMap (dep => dep +: extractChildren(dep)))

      val aggregatingProject = new AgentProjectInfo
      aggregatingProject setProjectToken aggregateProjectToken
      aggregatingProject setCoordinates extractCoordinates(config)
      aggregatingProject setDependencies flatDependencies.asJava
      if (org.apache.commons.lang.StringUtils isNotBlank aggregateProjectName)
        aggregatingProject.getCoordinates setArtifactId aggregateProjectName
      Vector(aggregatingProject)
    } else projectInfos
  }

  final protected def generateReport(result: BaseCheckPoliciesResult): Unit = {
    log info "Generating Policy Check Report"
    val report = new PolicyCheckReport(result)
    try {
      report.generate(outDir, false)
      report generateJson outDir
    } catch {
      case e: IOException => throw WhiteSourceException(s"Error generating report: ${e.getMessage}", e)
    }
    ()
  }

  private def createService() = {
    log info s"Service URL is ${serviceUrl}"
    val service = new WhitesourceService(
      agentType, agentVersion, serviceUrl.toString, autoDetectProxySettings)
    log info "Initiated WhiteSource Service"
    service
  }

  private def shouldProcess(c: ProjectConfig): Boolean = {
    import c._
    def matchAny(patterns: Vector[String]): Boolean = {
      for (pattern <- patterns) {
        val regex = pattern.replace(".", "\\.").replace("*", ".*")
        if (artifactId matches regex)
          return true
      }
      false
    }

    if (ignore) {
      log info s"Skipping ${projectId(c)} (marked as ignored)"
      false
    } else if (excludes.nonEmpty && matchAny(excludes)) {
      log info s"Skipping ${projectId(c)} (marked as excluded)"
      false
    } else if (includes.nonEmpty && matchAny(includes))
      true
    else true
  }

  private def processProject(c: ProjectConfig): AgentProjectInfo = {
    log info s"Processing ${projectId(c)}"
    val projectInfo = new AgentProjectInfo
    projectInfo setProjectToken null
    projectInfo setCoordinates extractCoordinates(c)
    projectInfo setDependencies collectDependencyStructure(c).asJava
    projectInfo
  }

  private def projectId(c: ProjectConfig)          = s"${c.groupId}:${c.artifactId}:${c.version}"
  private def extractCoordinates(c: ProjectConfig) = new Coordinates(c.groupId, c.artifactId, c.version)
  private def extractCoordinates(c: Config)        = new Coordinates(c.projectID.organization, c.projectID.name, c.projectID.revision)

  private def collectDependencyStructure(c: ProjectConfig): Vector[DependencyInfo] = {
    import c._
    type GA = (String, String) // GA, as in GroupId and ArtifactID

    def moduleInfoByGA(confReport: ConfigurationReport): Map[GA, ModuleInfo] =
      confReport.modules
          .map(mr => ModuleInfo(mr.module.organization, mr.module.name, mr.module.revision, artifactAndJar(mr)))
          .keyByAndMerge(mi => (mi.groupId, mi.artifactId), mergeModuleInfo)

    def moduleInfos(config: String): Map[GA, ModuleInfo] =
      updateReport.configuration(ConfigRef(config)) match {
        case Some(confReport) => moduleInfoByGA(confReport)
        case None             => Map.empty
      }

    val  compileModuleInfos = moduleInfos("compile")
    val  runtimeModuleInfos = moduleInfos("runtime")
    val     testModuleInfos = moduleInfos("test")
    val providedModuleInfos = moduleInfos("provided")
    val optionalModuleInfos = moduleInfos("optional")

    def definedInConfigs(ga: GA): Vector[ConfigRef] =
      updateReport.configurations.iterator
          .filter(_.modules exists (mr => mr.module.organization == ga._1 && mr.module.name == ga._2))
          .map(ConfigRef wrap _.configuration)
          .toVector

    def shouldIgnore(config: ConfigRef) = ignoredScopes contains config.name

    def forGA(ga: GA): Option[DependencyInfo] = {
      val isCompile  =  compileModuleInfos get ga
      val isRuntime  =  runtimeModuleInfos get ga
      val isTest     =     testModuleInfos get ga
      val isProvided = providedModuleInfos get ga
      val isOptional = optionalModuleInfos get ga

      val optScopeAndMr = (isCompile, isRuntime, isTest, isProvided, isOptional) match {
        case (None, _, _, Some(mr), _) => Some((MavenScope.Provided, mr))
        case (Some(mr), _, _, _, _)    => Some((MavenScope.Compile, mr))
        case (_, Some(mr), _, _, _)    => Some((MavenScope.Runtime, mr))
        case (_, _, Some(mr), _, _)    => Some((MavenScope.Test, mr))
        case (_, _, _, _, Some(mr))    => Some((MavenScope.Compile, mr))
        case _                         =>
          val configs = definedInConfigs(ga) filterNot shouldIgnore
          if (configs.nonEmpty)
            log warn s"Ignoring dependency $ga which is defined in config(s) $configs"
          None
      }

      optScopeAndMr filterNot (x => shouldIgnore(x._1.configRef)) map { case (scope, moduleInfo) =>
        getDependencyInfo(moduleInfo, scope, isOptional.isDefined)
      }
    }

    val gas =
      if (onlyDirectDependencies) libraryDependencies map (m => (m.organization, m.name))
      else updateReport.configurations
          .flatMap(_.modules map (mr => (mr.module.organization, mr.module.name)))
          .distinct

    val dependencyInfos = gas.toVector flatMap forGA

    log debug s"*** Printing Graph Results for $projectName"
    for (dependencyInfo <- dependencyInfos)
      debugPrintChildren(dependencyInfo, "")

    dependencyInfos
  }

  sealed case class ModuleInfo(
      groupId: String,
      artifactId: String,
      version: String,
      artifactAndJar: Option[(Artifact, File)]
  )

  /* Under sbt-coursier it's possible to have multiple ModuleInfo for the same (groupId, artifactId),
   * where there is no difference between them,
   * or the difference is one has artifacts of type "jar" and the other of type "bundle".
   *
   * For this last case we 'upgrade' "jar" to "bundle".
   */
  private def mergeModuleInfo(m1: ModuleInfo, m2: ModuleInfo): Option[ModuleInfo] = {

    def moduleInfoToTuple(m: ModuleInfo) = (m.groupId, m.artifactId, m.version)

    def artifactToTuple(a: Artifact) =
      (a.name, a.extension, a.classifier, a.configurations, a.url, a.extraAttributes)

    if (moduleInfoToTuple(m1) != moduleInfoToTuple(m2)) None else {
      (m1.artifactAndJar, m2.artifactAndJar) match {
        case (None, Some(_))                  => None
        case (Some(_), None)                  => None
        case (None, None)                     => Some(m1)
        case (Some((a1, f1)), Some((a2, f2))) =>
          val artifactAndJar = if (f1 != f2 || artifactToTuple(a1) != artifactToTuple(a2)) None else {
            val t1 = a1.`type`
            val t2 = a2.`type`
            val finalType = if (t1 == t2) Some(t1)
            else if (Set(t1, t2) == Set("jar", "bundle")) Some("bundle")
            else None
            finalType map (tpe => a1.withType(tpe) -> f1)
          }
          artifactAndJar map (artifactAndJar => m1.copy(artifactAndJar = Some(artifactAndJar)))
      }
    }
  }

  private def getDependencyInfo(m: ModuleInfo, scope: MavenScope, optional: Boolean): DependencyInfo = {
    val info = new DependencyInfo()
    info setGroupId m.groupId
    info setArtifactId m.artifactId
    info setVersion m.version
    info setScope scope.name
    info setOptional optional
    info setClassifier m.artifactAndJar.flatMap(_._1.classifier).orNull
    info setType m.artifactAndJar.map(_._1.`type`).orNull
    try {
      info setSystemPath m.artifactAndJar.map(_._2.getAbsolutePath).orNull
      info setSha1 m.artifactAndJar.map(ChecksumUtils calculateSHA1 _._2).orNull
    } catch {
      case _: IOException => log debug s"Error calculating SHA-1 for $m"
    }
    info setExclusions List.empty[ExclusionInfo].asJava
    info setChildren List.empty[DependencyInfo].asJava
    info
  }

  private def artifactAndJar(modReport: ModuleReport) = {
    val artifacts = modReport.artifacts
    artifacts find (_._1.`type` == Artifact.DefaultType) orElse
        (artifacts find (_._1.extension == Artifact.DefaultExtension))
  }

  private def debugPrintChildren(info: DependencyInfo, prefix: String): Unit = {
    import info._
    log debug s"$prefix$getGroupId:$getArtifactId:$getVersion:$getScope"
    for (child <- info.getChildren.asScala)
      debugPrintChildren(child, s"$prefix   ")
  }

  private def debugProjectInfos(projectInfos: Vector[AgentProjectInfo], log: Logger): Unit = {
    log debug "----------------- dumping projectInfos -----------------"
    log debug "Total Number of Projects : " + projectInfos.size
    for (projectInfo <- projectInfos) {
      log debug s"Project Coordinates: ${projectInfo.getCoordinates}"
      log debug s"Project Parent Coordinates: ${Option(projectInfo.getParentCoordinates) getOrElse ""}"
      log debug s"Project Token: ${projectInfo.getProjectToken}"
      log debug s"Total Number of Dependencies: ${projectInfo.getDependencies.size}"
      for (info <- projectInfo.getDependencies.asScala)
        log debug s"${info.toString} SHA-1: ${info.getSha1}"
    }
    log debug "----------------- dump finished -----------------"
  }

  private def extractChildren(dependency: DependencyInfo): Vector[DependencyInfo] =
    dependency.getChildren.asScala.flatMap(child => child +: extractChildren(child)).toVector

  private def handleError(e: Exception) = {
    val msg = e.getMessage
    val msg2 = if (msg eq null) "" else msg
    if (failOnError) {
      if (msg ne null) log debug msg
      log trace e
      sys error msg2
    } else {
      if (msg ne null) log error msg
      log trace e
    }
  }
}

final class CheckPoliciesAction(config: Config, childConfigs: Vector[ProjectConfig]) extends BaseAction(config, childConfigs) {
  import config._

  protected def doExecute(service: WhitesourceService): Unit = {
    val projectInfos = extractProjectInfos()
    if (projectInfos.isEmpty)
      log info "No open source information found."
    else
      sendCheckPolicies(service, projectInfos)
  }

  private def sendCheckPolicies(service: WhitesourceService, projectInfos: Vector[AgentProjectInfo]) = {
    try {
      log info "Checking Policies"

      val result = service.checkPolicyCompliance(
        orgToken, product, productVersion, projectInfos.asJava, forceCheckAllDependencies)

      generateReport(result)

      if (result.hasRejections())
        throw WhiteSourceException("Some dependencies were rejected by the organization's policies.")
      else
        log info "All dependencies conform with the organization's policies."
    } catch {
      case e: WssServiceException =>
        throw WhiteSourceException(s"Error communicating with service: ${e.getMessage}", e)
    }
  }
}

final class UpdateAction(config: Config, childConfigs: Vector[ProjectConfig]) extends BaseAction(config, childConfigs) {
  import config._

  protected def doExecute(service: WhitesourceService): Unit = {
    val projectInfos = extractProjectInfos()
    if (projectInfos.isEmpty)
      log info "No open source information found."
    else
      sendUpdate(service, projectInfos)
  }

  private def sendUpdate(service: WhitesourceService, projectInfos: Vector[AgentProjectInfo]) = {
    try {
      if (checkPolicies) {
        log info "Checking Policies"
        val result = service.checkPolicyCompliance(
          orgToken, product, productVersion, projectInfos.asJava, forceCheckAllDependencies)

        generateReport(result)

        val hasRejections = result.hasRejections
        if (hasRejections && !forceUpdate)
          throw WhiteSourceException("Some dependencies were rejected by the organization's policies.")
        else {
          val conformMsg = "All dependencies conform with open source policies."
          val violateMsg = "Some dependencies violate open source policies, " +
              "however all were force updated to organization inventory."
          log info (if (hasRejections) violateMsg else conformMsg)
        }
      }
      log info "Sending Update Request to WhiteSource"
      val updateResult = service.update(orgToken, requesterEmail, product, productVersion, projectInfos.asJava)
      logResult(updateResult, log)
    } catch {
      case e: WssServiceException =>
        throw WhiteSourceException(s"Error communicating with service: ${e.getMessage}", e)
    }
  }

  private def logResult(result: UpdateInventoryResult, log: Logger): Unit = {
    log info ""
    log info "------------------------------------------------------------------------"
    log info s"Inventory Update Result for ${result.getOrganization}"
    log info "------------------------------------------------------------------------"

    // newly created projects
    val createdProjects = result.getCreatedProjects
    if (!createdProjects.isEmpty) {
      log info ""
      log info "Newly Created Projects:"
      for (projectName <- createdProjects.asScala)
        log info s"* $projectName"
    }

    // updated projects
    val updatedProjects = result.getUpdatedProjects
    if (!updatedProjects.isEmpty) {
      log.info("")
      log.info("Updated Projects:")
      for (projectName <- updatedProjects.asScala)
        log.info("* " + projectName)
    }
    log.info("")
  }
}

private sealed trait MavenScope {
  def name: String = this match {
    case MavenScope.Compile  => "compile"
    case MavenScope.Runtime  => "runtime"
    case MavenScope.Test     => "test"
    case MavenScope.Provided => "provided"
  }
  def configRef: ConfigRef = ConfigRef(name)
}
private object MavenScope {
  case object Compile  extends MavenScope
  case object Runtime  extends MavenScope
  case object Test     extends MavenScope
  case object Provided extends MavenScope
}
