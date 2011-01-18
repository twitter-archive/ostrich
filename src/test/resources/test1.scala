import com.twitter.logging.config._

new LoggerConfig {
  node = "com.twitter"
  level = Level.DEBUG
  handlers = new ConsoleHandlerConfig()
}
