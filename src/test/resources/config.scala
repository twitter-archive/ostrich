import com.twitter.util.Config

new Config[String] {
  override def apply() = "foo"
}

