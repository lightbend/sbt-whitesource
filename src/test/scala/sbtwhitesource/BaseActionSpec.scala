package sbtwhitesource

import java.net.URL

import sbt.Artifact

import org.scalatest.WordSpec
import org.scalatest.Matchers

class BaseActionSpec extends WordSpec with Matchers {
  "The base action" should {
    "merge standard and native jars of the same artifacts" in {
      val nativeUrl = new URL("https://repo1.maven.org/maven2/com/github/jnr/jffi/1.2.16/jffi-1.2.16-native.jar")
      val nativeArtifact: Artifact = Artifact("com.github", "jar", "jar", Some("native"), Vector(), Some(nativeUrl))
      val native = ModuleInfo("com.github", "jffi", "1.2.16", Some((nativeArtifact, null)))
      
      val javaUrl = new URL("https://repo1.maven.org/maven2/com/github/jnr/jffi/1.2.16/jffi-1.2.16.jar")
      val javaArtifact: Artifact = Artifact("com.github", "jar", "jar", None, Vector(), Some(javaUrl))
      val java = ModuleInfo("com.github", "jffi", "1.2.16", Some((javaArtifact, null)))
      
      BaseAction.mergeModuleInfo(native, java) should be(Some(java))
      BaseAction.mergeModuleInfo(java, native) should be(Some(java))
    }
  }
}
