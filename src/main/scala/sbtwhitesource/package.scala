package sbtwhitesource

import scala.collection.GenTraversableOnce
import scala.collection.mutable

object `package` {
  implicit class KeyByAndMergeSyntax[A](val _xs: GenTraversableOnce[A]) extends AnyVal {
    /** Creates a key-value map using the given `f` function to determine the key
      * and the given `m` function to attempt to merge any duplicates.
      *
      * If `m` returns None an exception is thrown.
      *
      * @param keyFn the function that determines the key
      * @param merge the merging function
      * @tparam K the type of key of the map
      * @return a key-value map from the original collection
      */
    def keyByAndMerge[K](keyFn: A => K, merge: (A, A) => Option[A]): Map[K, A] = {
      val m = mutable.Map.empty[K, A]
      for (elem <- _xs) {
        val key = keyFn(elem)
        val value = m get key match {
          case None    => elem
          case Some(a) =>
            merge(a, elem) getOrElse
              (sys error s"Multiple elements for the same key $key:\n\t$a\n\t$elem")
        }
        m(key) = value
      }
      m.toMap
    }
  }
}
