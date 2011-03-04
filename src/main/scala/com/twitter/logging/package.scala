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

package com.twitter

package object logging {
  // FileHandler.scala
  type Policy = com.twitter.ostrich.logging.Policy
  val Policy = com.twitter.ostrich.logging.Policy
  type FileHandler = com.twitter.ostrich.logging.FileHandler

  // Formatter.scala
  type Formatter = com.twitter.ostrich.logging.Formatter
  type ExceptionJsonFormatter = com.twitter.ostrich.logging.ExceptionJsonFormatter
  val BasicFormatter = com.twitter.ostrich.logging.BasicFormatter
  val BareFormatter = com.twitter.ostrich.logging.BareFormatter

  // Handler.scala
  type Handler = com.twitter.ostrich.logging.Handler
  type StringHandler = com.twitter.ostrich.logging.StringHandler
  type ConsoleHandler = com.twitter.ostrich.logging.ConsoleHandler

  // LazyLogRecord.scala
  type LazyLogRecord = com.twitter.ostrich.logging.LazyLogRecord

  // Logger.scala
  val Level = com.twitter.ostrich.logging.Level
  type LoggingException = com.twitter.ostrich.logging.LoggingException
  type Logger = com.twitter.ostrich.logging.Logger
  val Logger = com.twitter.ostrich.logging.Logger

  // ScribeHandler.scala
  type ScribeHandler = com.twitter.ostrich.logging.ScribeHandler

  // SyslogHandler.scala
  val SyslogHandler = com.twitter.ostrich.logging.SyslogHandler
  type SyslogFormatter = com.twitter.ostrich.logging.SyslogFormatter
  type SyslogHandler = com.twitter.ostrich.logging.SyslogHandler
  val SyslogFuture = com.twitter.ostrich.logging.SyslogFuture

  // ThrottledHandler.scala
  type ThrottledHandler = com.twitter.ostrich.logging.ThrottledHandler
}
