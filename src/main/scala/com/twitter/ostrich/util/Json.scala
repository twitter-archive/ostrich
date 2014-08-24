package com.twitter.ostrich.util

import com.fasterxml.jackson.core.util.DefaultPrettyPrinter
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule

object Json {

  private[this] val writer = {
    val mapper = new ObjectMapper
    mapper.registerModule(DefaultScalaModule)
    val printer = new DefaultPrettyPrinter
    printer.indentArraysWith(new DefaultPrettyPrinter.Lf2SpacesIndenter)
    mapper.writer(printer)
  }

  def build(obj: Any): String = writer.writeValueAsString(obj)

}
