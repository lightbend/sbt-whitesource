package sbtwhitesource

import java.io.File
import java.net.URI

import org.whitesource.agent.api.dispatch._
import org.whitesource.agent.api.model._
import org.whitesource.agent.client._
import org.whitesource.agent.report._

import scala.collection.JavaConverters._

import sbt.Logger

object WhiteSource {
  val agentType: String    = "sbt-plugin"     // TODO: or "sbt-whitesource"
  val agentVersion: String = "0.1.0-SNAPSHOT" // TODO: Extract this from the build.

  // TODO: handle skip in WhitesourceMojo execute
  // TODO: handleError in WhitesourceMojo execute
  // TODO: Add finally service.shutdown()

  def checkPolicies(
      serviceUrl: URI,
      orgToken: String,
      projectToken: String,
      product: String,
      productVersion: String,
      forceCheckAllDependencies: Boolean,
      outDir: File,
      log: Logger
  ): Unit = {
    val projectInfos = extractProjectInfos(projectToken, log)
    if (projectInfos.isEmpty)
      log info "No open source information found."
    else
      sendCheckPolicies(
        serviceUrl,
        orgToken,
        product,
        productVersion,
        projectInfos,
        forceCheckAllDependencies,
        outDir,
        log
      )
  }

  def update(
      checkPolicies: Boolean,
      serviceUrl: URI,
      orgToken: String,
      projectToken: String,
      product: String,
      productVersion: String,
      forceCheckAllDependencies: Boolean,
      outDir: File,
      forceUpdate: Boolean,
      requesterEmail: String,
      log: Logger
  ): Unit = {
    val projectInfos = extractProjectInfos(projectToken, log)
    if (projectInfos.isEmpty)
      log info "No open source information found."
    else
      sendUpdate(
        checkPolicies,
        serviceUrl,
        orgToken,
        product,
        productVersion,
        projectInfos,
        forceCheckAllDependencies,
        outDir,
        forceUpdate,
        requesterEmail,
        log
      )
  }

  private def extractProjectInfos(projectToken: String, log: Logger): Vector[AgentProjectInfo] = {
    val projectId = ""
    val artifactId = ""
    val ignore = false
    val includes = Vector.empty
    val excludes = Vector.empty

    val projectInfos =
      if (shouldProcess(projectId, artifactId, ignore, includes, excludes, log)) {
        Vector(processProject(projectToken, log))
      } else {
        Vector.empty
      }
    debugProjectInfos(projectInfos, log)

    // TODO: Add support for aggregateModules

    projectInfos
  }

  private def shouldProcess(
      projectId: String,
      artifactId: String,
      ignore: Boolean,
      includes: Vector[String],
      excludes: Vector[String],
      log: Logger
  ): Boolean = {

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

  private def processProject(projectToken: String, log: Logger): AgentProjectInfo = {
    val projectId = "<some project id>"
    log info s"Processing $projectId"
    val projectInfo = new AgentProjectInfo
    projectInfo setProjectToken projectToken
    projectInfo setCoordinates extractCoordinates()
    projectInfo setDependencies collectDependencyStructure().asJava
    projectInfo
  }

  private def extractCoordinates() = {
    val groupId: String    = ""
    val artifactId: String = ""
    val version: String    = ""
    new Coordinates(groupId, artifactId, version)
  }

  private def collectDependencyStructure(): Vector[DependencyInfo] = {
    Vector.empty
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

  private def sendCheckPolicies(
      serviceUrl: URI,
      orgToken: String,
      product: String,
      productVersion: String,
      projectInfos: Vector[AgentProjectInfo],
      forceCheckAllDependencies: Boolean,
      outDir: File,
      log: Logger
  ) = {
    log info "Checking Policies"
    val service = createService(serviceUrl, log)

    val result = service.checkPolicyCompliance(
      orgToken, product, productVersion, projectInfos.asJava, forceCheckAllDependencies)

    generateReport(result, outDir, log)

    if (result.hasRejections())
      sys error "Some dependencies were rejected by the organization's policies."
    else
      log info "All dependencies conform with the organization's policies."
  }

  private def sendUpdate(
      checkPolicies: Boolean,
      serviceUrl: URI,
      orgToken: String,
      product: String,
      productVersion: String,
      projectInfos: Vector[AgentProjectInfo],
      forceCheckAllDependencies: Boolean,
      outDir: File,
      forceUpdate: Boolean,
      requesterEmail: String,
      log: Logger
  ) = {
    val service = createService(serviceUrl, log)

    if (checkPolicies) {
      log info "Checking Policies"
      val result = service.checkPolicyCompliance(
        orgToken, product, productVersion, projectInfos.asJava, forceCheckAllDependencies)

      generateReport(result, outDir, log)

      val hasRejections = result.hasRejections
      if (hasRejections && !forceUpdate)
        sys error "Some dependencies were rejected by the organization's policies."
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
  }

  private def createService(serviceUrl: URI, log: Logger) = {
    log info s"Service URL is $serviceUrl"
    val service = new WhitesourceService(
      agentType, agentVersion, serviceUrl.toString, /* autoDetectProxySettings = */ false)
    log info "Initiated WhiteSource Service"
    service
  }

  private def generateReport(result: BaseCheckPoliciesResult, outDir: File, log: Logger) = {
    log info "Generating Policy Check Report"
    val report = new PolicyCheckReport(result)
    report.generate(outDir, false)
    report generateJson outDir
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

  private def handleError(e: Exception, failOnError: Boolean, log: Logger) = {
    val msg = e.getMessage
    if (failOnError) {
      log debug msg
      log trace e
      sys error msg
    } else {
      log error msg
      log trace e
    }
  }
}
