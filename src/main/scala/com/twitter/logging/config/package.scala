/*
 * Copyright 2011 Twitter, Inc.
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

package com.twitter.logging

package object config {
  type LoggerConfig = com.twitter.ostrich.logging.config.LoggerConfig
  type FormatterConfig = com.twitter.ostrich.logging.config.FormatterConfig
  val BasicFormatterConfig = com.twitter.ostrich.logging.config.BasicFormatterConfig
  val BareFormatterConfig = com.twitter.ostrich.logging.config.BareFormatterConfig
  type SyslogFormatterConfig = com.twitter.ostrich.logging.config.SyslogFormatterConfig
  type HandlerConfig = com.twitter.ostrich.logging.config.HandlerConfig
  type ConsoleHandlerConfig = com.twitter.ostrich.logging.config.ConsoleHandlerConfig
  type ThrottledHandlerConfig = com.twitter.ostrich.logging.config.ThrottledHandlerConfig
  type FileHandlerConfig = com.twitter.ostrich.logging.config.FileHandlerConfig
  type SyslogHandlerConfig = com.twitter.ostrich.logging.config.SyslogHandlerConfig
  type ScribeHandlerConfig = com.twitter.ostrich.logging.config.ScribeHandlerConfig
}
