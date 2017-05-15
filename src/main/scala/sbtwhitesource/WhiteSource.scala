package sbtwhitesource

import java.io.{ File, IOException }
import java.net.URI

import org.whitesource.agent.api._, dispatch._, model._
import org.whitesource.agent.client._
import org.whitesource.agent.report._

import scala.collection.JavaConverters._

import sbt.{ Logger, ModuleID }

import net.virtualvoid.sbt.graph._

final class Config(
    val projectName: String,
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
    val moduleGraph: ModuleGraph,
    val ignoreTestScopeDependencies: Boolean,
    val outDir: File,
    val projectToken: String,
    val ignore: Boolean,
    val includes: Vector[String],
    val excludes: Vector[String],
    val ignoredScopes: Vector[String],
    val aggregateModules: Boolean,
    val aggregateProjectName: String,
    val aggregateProjectToken: String,
    val requesterEmail: String,
    val autoDetectProxySettings: Boolean,
    val log: Logger
) {
  def groupId: String    = projectID.organization
  def artifactId: String = projectID.name
  def version: String    = projectID.revision
}

final case class WhiteSourceException(message: String = null, cause: Exception = null)
    extends RuntimeException(message, cause)

sealed abstract class BaseAction(config: Config) {
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
    val projectInfos = if (shouldProcess()) Vector(processProject()) else Vector.empty
    debugProjectInfos(projectInfos, log)

    // TODO: Add support for aggregateModules

    projectInfos
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
    log info s"Service URL is $serviceUrl"
    val service = new WhitesourceService(
      agentType, agentVersion, serviceUrl.toString, autoDetectProxySettings)
    log info "Initiated WhiteSource Service"
    service
  }

  private def shouldProcess(): Boolean = {
    def matchAny(patterns: Vector[String]): Boolean = {
      for (pattern <- patterns) {
        val regex = pattern.replace(".", "\\.").replace("*", ".*")
        if (artifactId matches regex)
          return true
      }
      false
    }

    if (ignore) {
      log info s"Skipping $projectId (marked as ignored)"
      false
    } else if (excludes.nonEmpty && matchAny(excludes)) {
      log info s"Skipping $projectId (marked as excluded)"
      false
    } else if (includes.nonEmpty && matchAny(includes))
      true
    else true
  }

  private def processProject(): AgentProjectInfo = {
    log info s"Processing $projectId"
    val projectInfo = new AgentProjectInfo
    projectInfo setProjectToken projectToken
    projectInfo setCoordinates extractCoordinates()
    projectInfo setDependencies collectDependencyStructure().asJava
    projectInfo
  }

  private def projectId            = s"$groupId:$artifactId:$version"
  private def extractCoordinates() = new Coordinates(groupId, artifactId, version)

  private lazy val deps = moduleGraph.dependencyMap
  private def getChildren(node: Module) = deps.getOrElse(node.id, Nil)

  private def collectDependencyStructure(): Vector[DependencyInfo] = {
    val dependencyInfos = moduleGraph.roots.toVector flatMap (getChildren(_) map getDependencyInfo)

    log debug s"*** Printing Graph Results for $projectName"
    for (dependencyInfo <- dependencyInfos)
      debugPrintChildren(dependencyInfo, "")

    dependencyInfos
  }

  private def getDependencyInfo(node: Module): DependencyInfo = {
    val info = new DependencyInfo()
    info setGroupId node.id.organisation
    info setArtifactId node.id.name
    info setVersion node.id.version
    info setScope "compile"
    info setClassifier ""
    info setOptional false
    info setType "jar"
    node.jarFile filter (_.exists()) foreach (artifactFile =>
      try {
        info setSystemPath artifactFile.getAbsolutePath
        info setSha1 (ChecksumUtils calculateSHA1 artifactFile)
      } catch {
        case _: IOException => log debug s"Error calculating SHA-1 for ${node.id.idString}"
      }
    )
    info setExclusions List.empty[ExclusionInfo].asJava
    info setChildren (getChildren(node) map getDependencyInfo).asJava
    info
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

final class CheckPoliciesAction(config: Config) extends BaseAction(config) {
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

final class UpdateAction(config: Config) extends BaseAction(config) {
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
          val voilateMsg = "Some dependencies violate open source policies, " +
              "however all were force updated to organization inventory."
          log info (if (hasRejections) voilateMsg else conformMsg)
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

object WhiteSource {
}
