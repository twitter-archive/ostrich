/*
 * Copyright 2014 Twitter, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain
 * a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.twitter.ostrich.util

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.fasterxml.jackson.module.scala.experimental.ScalaObjectMapper

/**
 * Wraps Jackson to convert objects into json (string)
 */
object Json {

  private[this] val writer = {
    val mapper = new ObjectMapper
    mapper.registerModule(DefaultScalaModule)
    mapper.writer
  }

  /**
   * Generate a json from a object
   */
  def build(obj: Any): String =
    writer.writeValueAsString(obj)

  /**
   * Parse a json into a map
   */
  def parse(json: String): Map[String, Any] = {
    val mapper = new ObjectMapper() with ScalaObjectMapper
    mapper.registerModule(DefaultScalaModule)
    mapper.readValue[Map[String, Any]](json)
  }

}
