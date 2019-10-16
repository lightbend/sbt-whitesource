package sbtwhitesource

import java.net.URL

import sbt.Artifact

import org.scalatest.WordSpec
import org.scalatest.Matchers

class BaseActionSpec extends WordSpec with Matchers {
  "The base action" should {
    "merge standard and native jars of the same artifacts" in {
      val nativeUrl = new URL("https://repo1.maven.org/maven2/com/github/jnr/jffi/1.2.16/jffi-1.2.16-native.jar")
      val nativeArtifact: Artifact = Artifact("jffi", "jar", "jar", Some("native"), Vector(), Some(nativeUrl))
      val native = ModuleInfo("com.github", "jffi", "1.2.16", Some((nativeArtifact, null)))
      
      val javaUrl = new URL("https://repo1.maven.org/maven2/com/github/jnr/jffi/1.2.16/jffi-1.2.16.jar")
      val javaArtifact: Artifact = Artifact("jffi", "jar", "jar", None, Vector(), Some(javaUrl))
      val java = ModuleInfo("com.github", "jffi", "1.2.16", Some((javaArtifact, null)))
      
      BaseAction.mergeModuleInfo(native, java) should be(Some(java))
      BaseAction.mergeModuleInfo(java, native) should be(Some(java))
    }

    "merge platform-specific artifacts with matching platform-independent artifacts" in {
      val nativeUrl = new URL("https://repo1.maven.org/maven2/io/netty/netty-transport-native-epoll/4.1.42.Final/netty-transport-native-epoll-4.1.42.Final-linux-x86_64.jar")
      val nativeArtifact: Artifact = Artifact("netty-transport-native-epoll", "jar", "jar", Some("linux-x86_64"), Vector(), Some(nativeUrl))
      val native = ModuleInfo("io.netty", "netty-transport-native-epoll", "4.1.42.Final", Some((nativeArtifact, null)))

      val javaUrl = new URL("https://repo1.maven.org/maven2/io/netty/netty-transport-native-epoll/4.1.42.Final/netty-transport-native-epoll-4.1.42.Final.jar")
      val javaArtifact: Artifact = Artifact("netty-transport-native-epoll", "jar", "jar", None, Vector(), Some(javaUrl))
      val java = ModuleInfo("io.netty", "netty-transport-native-epoll", "4.1.42.Final", Some((javaArtifact, null)))

      BaseAction.mergeModuleInfo(native, java) should be(Some(java))
      BaseAction.mergeModuleInfo(java, native) should be(Some(java))
    }

    "upgrade 'jar' to 'bundle' when both types are present" in {
      val url = new URL("https://repo1.maven.org/maven2/com/example/osgi/fake-osgi-bundle/1.0.0/fake-osgi-bundle-1.0.0.jar")

      val bundleArtifact: Artifact = Artifact("fake-osgi-bundle", "bundle", "jar", None, Vector(), Some(url))
      val bundle = ModuleInfo("com.example.osgi", "fake-osgi-bundle", "1.0.0", Some((bundleArtifact, null)))

      val jarArtifact: Artifact = Artifact("fake-osgi-bundle", "jar", "jar", None, Vector(), Some(url))
      val jar = ModuleInfo("com.example.osgi", "fake-osgi-bundle", "1.0.0", Some((jarArtifact, null)))

      BaseAction.mergeModuleInfo(bundle, jar) should be(Some(bundle))
      BaseAction.mergeModuleInfo(jar, bundle) should be(Some(bundle))
    }
  }
}
